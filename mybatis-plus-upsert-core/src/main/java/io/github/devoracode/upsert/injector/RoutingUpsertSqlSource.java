package io.github.devoracode.upsert.injector;

import io.github.devoracode.upsert.core.UpsertMeta;
import io.github.devoracode.upsert.dialect.DynamicUpsertDialect;
import io.github.devoracode.upsert.dialect.UpsertDialect;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.session.Configuration;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 根据当前数据源在运行时解析实际方言的 {@link SqlSource}。
 *
 * <p>用于动态数据源模式。不绑定到固定的 {@link UpsertDialect}，
 * 而是通过 {@code DynamicDataSourceContextHolder} 询问
 * {@link DynamicUpsertDialect} 当前线程的数据源适用哪种方言，
 * 然后将 SQL 构建委托给该方言。每个已解析方言的解析 {@link SqlSource}
 * 都通过复合键进行缓存。
 *
 * <p>按方言的 {@code SqlSource} 缓存在单数据源模式下扮演的角色与
 * MyBatis-Plus 的 {@code MappedStatement.SqlSource} 相同：
 * SQL 生成一次且永不重新生成。
 *
 * @author devoracode
 * @since 1.2.0
 */
final class RoutingUpsertSqlSource implements SqlSource {

    private final Configuration configuration;
    private final LanguageDriver languageDriver;
    private final DynamicUpsertDialect dynamicDialect;
    private final UpsertMeta meta;
    private final boolean batch;
    private final Class<?> modelClass;

    // 按方言类名缓存已解析的 SqlSource
    // 键：方言类名 + ":" + 表名 + ":" + (批量 ? "batch" : "single")
    private final ConcurrentHashMap<String, SqlSource> sqlSourceCache = new ConcurrentHashMap<>();

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
        String dialectClassName = currentDialect.getClass().getSimpleName();

        // 构建缓存键
        String entityKey = meta.getEntityClass() != null ? meta.getEntityClass().getName() : meta.getTableName();
        String cacheKey = dialectClassName + ":" + entityKey + ":" + (batch ? "batch" : "single");

        // 获取或创建该方言的 SqlSource
        SqlSource sqlSource = sqlSourceCache.computeIfAbsent(cacheKey, createSqlSource(currentDialect));

        return sqlSource.getBoundSql(parameterObject);
    }

    /**
     * 通过从已解析方言生成 Upsert SQL 并将其包装为 MyBatis 脚本 SqlSource，
     * 构建（并缓存）按方言的 SqlSource。
     *
     * @param dialect 要构建 SqlSource 的方言
     * @return 为给定方言缓存键创建 SqlSource 的工厂函数
     */
    private Function<String, SqlSource> createSqlSource(UpsertDialect dialect) {
        return key -> {
            String sql = batch
                    ? dialect.buildUpsertBatchSql(meta)
                    : dialect.buildUpsertSql(meta);
            return languageDriver.createSqlSource(configuration, "<script>" + sql + "</script>", modelClass);
        };
    }
}
