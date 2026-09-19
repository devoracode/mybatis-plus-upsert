package io.github.devoracode.upsert.injector;

import io.github.devoracode.upsert.core.UpsertMeta;
import io.github.devoracode.upsert.dialect.DynamicUpsertDialect;
import io.github.devoracode.upsert.dialect.UpsertDialect;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.session.Configuration;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 根据当前数据源在运行时解析实际方言的 {@link SqlSource}。
 *
 * <p>用于动态数据源模式。不绑定到固定的 {@link UpsertDialect}，
 * 而是通过 {@code DynamicDataSourceContextHolder} 询问
 * {@link DynamicUpsertDialect} 当前线程的数据源适用哪种方言，
 * 然后将 SQL 构建委托给该方言。每个已解析方言的解析 {@link SqlSource}
 * 都通过复合键进行缓存。
 *
 * <p><strong>缓存键</strong>为"<em>方言实例</em> + 实体标识 + batch 标记"，
 * 其中方言实例使用其自身的 {@code equals/hashCode}（未重写时即对象身份）。
 * 不能用方言类名做键：同一进程里可能存在同一方言类的多个实例
 * （例如两个数据源各自引用一个带不同配置的自定义方言 Bean），
 * 也可能存在简单类名相同但包名不同的两个方言类，
 * 这些情况下按类名缓存会让数据源 B 直接命中数据源 A 生成的 SQL。
 *
 * <p>按方言的 {@code SqlSource} 缓存在单数据源模式下扮演的角色与
 * MyBatis-Plus 的 {@code MappedStatement.SqlSource} 相同：
 * SQL 生成一次且永不重新生成。
 *
 * @author devoracode
 * @since 1.2.0
 */
@Slf4j
final class RoutingUpsertSqlSource implements SqlSource {

    /**
     * 缓存条目上限。正常的方言实现按数据源返回稳定实例，远低于该值；
     * 若某个 {@link DynamicUpsertDialect} 每次调用都新建方言实例，
     * 缓存既不会命中也会无限增长，达到上限后改为直接构建。
     */
    private static final int MAX_CACHED_SQL_SOURCES = 64;

    private final Configuration configuration;
    private final LanguageDriver languageDriver;
    private final DynamicUpsertDialect dynamicDialect;
    private final UpsertMeta meta;
    private final boolean batch;
    private final Class<?> modelClass;

    // 按"方言实例 + 实体 + batch"缓存已解析的 SqlSource
    private final ConcurrentHashMap<SqlSourceKey, SqlSource> sqlSourceCache = new ConcurrentHashMap<>();

    /**
     * 缓存达到上限的告警只输出一次。
     */
    private final AtomicBoolean overflowWarned = new AtomicBoolean();

    /**
     * 创建新的路由 SqlSource。
     *
     * @param configuration    MyBatis 配置
     * @param languageDriver   用于构建按方言 SqlSource 的语言驱动
     * @param dynamicDialect   解析当前数据源方言的动态方言
     * @param meta             已解析的 Upsert 元数据
     * @param batch            是否使用批量 Upsert SQL
     * @param modelClass       实体类
     */
    RoutingUpsertSqlSource(Configuration configuration,
                           LanguageDriver languageDriver,
                           DynamicUpsertDialect dynamicDialect,
                           UpsertMeta meta,
                           boolean batch,
                           Class<?> modelClass) {
        this.configuration = configuration;
        this.languageDriver = languageDriver;
        this.dynamicDialect = dynamicDialect;
        this.meta = meta;
        this.batch = batch;
        this.modelClass = modelClass;
    }

    /**
     * 解析当前数据源的方言并委托给其缓存的 SqlSource。
     *
     * @param parameterObject 映射器调用参数
     * @return 当前方言 SqlSource 产生的已绑定 SQL
     */
    @Override
    public BoundSql getBoundSql(Object parameterObject) {
        // 运行时获取当前方言
        UpsertDialect currentDialect = dynamicDialect.getCurrentDialect();
        return cachedSqlSource(currentDialect).getBoundSql(parameterObject);
    }

    /**
     * 返回给定方言实例所缓存的 SqlSource，首次访问时构建并写入缓存。
     *
     * @param dialect 当前解析出的方言实例
     * @return 该方言实例对应的 SqlSource
     */
    private SqlSource cachedSqlSource(UpsertDialect dialect) {
        if (sqlSourceCache.size() >= MAX_CACHED_SQL_SOURCES) {
            if (overflowWarned.compareAndSet(false, true)) {
                log.warn("Upsert SqlSource cache of entity '{}' reached {} entries: the DynamicUpsertDialect "
                        + "appears to resolve a new dialect instance on every call, so Upsert SQL is rebuilt "
                        + "instead of cached.", entityKey(), MAX_CACHED_SQL_SOURCES);
            }
            return buildSqlSource(dialect);
        }
        // computeIfAbsent 保证同一方言实例被多个线程首次并发访问时也只构建一次
        return sqlSourceCache.computeIfAbsent(
                new SqlSourceKey(dialect, entityKey(), batch), key -> buildSqlSource(key.dialect));
    }

    /**
     * 缓存键中区分实体的部分：优先实体类名，回退表名。
     *
     * @return 实体标识
     */
    private String entityKey() {
        return meta.getEntityClass() != null ? meta.getEntityClass().getName() : meta.getTableName();
    }

    /**
     * 通过从已解析方言生成 Upsert SQL 并将其包装为 MyBatis 脚本 SqlSource，
     * 构建该方言的 SqlSource。
     *
     * @param dialect 要构建 SqlSource 的方言
     * @return 给定方言的 SqlSource
     */
    private SqlSource buildSqlSource(UpsertDialect dialect) {
        String sql = batch
                ? dialect.buildUpsertBatchSql(meta)
                : dialect.buildUpsertSql(meta);
        return languageDriver.createSqlSource(configuration, "<script>" + sql + "</script>", modelClass);
    }

    /**
     * SqlSource 缓存键：方言实例 + 实体标识 + batch 标记。
     *
     * <p>方言实例按其自身的 {@code equals/hashCode} 参与比较，因此同一类的不同实例
     * （或简单类名相同的不同类）不会共享缓存；若方言实现重写了 equals 声明两者等价，
     * 复用同一 SqlSource 即为正确行为。
     */
    private static final class SqlSourceKey {

        private final UpsertDialect dialect;
        private final String entityKey;
        private final boolean batch;

        SqlSourceKey(UpsertDialect dialect, String entityKey, boolean batch) {
            this.dialect = dialect;
            this.entityKey = entityKey;
            this.batch = batch;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof SqlSourceKey)) {
                return false;
            }
            SqlSourceKey that = (SqlSourceKey) other;
            return batch == that.batch
                    && entityKey.equals(that.entityKey)
                    && dialect.equals(that.dialect);
        }

        @Override
        public int hashCode() {
            return Objects.hash(dialect, entityKey, batch);
        }
    }
}
