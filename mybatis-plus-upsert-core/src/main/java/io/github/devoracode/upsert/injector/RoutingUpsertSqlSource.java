package io.github.devoracode.upsert.injector;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.devoracode.upsert.core.UpsertMeta;
import io.github.devoracode.upsert.dialect.DynamicUpsertDialect;
import io.github.devoracode.upsert.dialect.UpsertDialect;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.session.Configuration;

import java.util.Objects;

/**
 * 动态数据源下按当前线程的数据源解析方言、再委托该方言绑定 SQL 的 {@link SqlSource}，
 * 解析出的 SqlSource 按方言实例缓存。
 *
 * @author devoracode
 * @since 1.2.0
 */
final class RoutingUpsertSqlSource implements SqlSource {

    /**
     * 缓存条目上限，防止方言实现每次新建实例导致缓存无限增长；达到上限后由 Caffeine 淘汰旧条目。
     */
    private static final int MAX_CACHED_SQL_SOURCES = 64;

    private final Configuration configuration;
    private final LanguageDriver languageDriver;
    private final DynamicUpsertDialect dynamicDialect;
    private final UpsertMeta meta;
    private final Class<?> modelClass;

    private final Cache<SqlSourceKey, SqlSource> sqlSourceCache = Caffeine.newBuilder()
            .maximumSize(MAX_CACHED_SQL_SOURCES)
            .recordStats()
            .build();

    RoutingUpsertSqlSource(Configuration configuration,
                           LanguageDriver languageDriver,
                           DynamicUpsertDialect dynamicDialect,
                           UpsertMeta meta,
                           Class<?> modelClass) {
        this.configuration = configuration;
        this.languageDriver = languageDriver;
        this.dynamicDialect = dynamicDialect;
        this.meta = meta;
        this.modelClass = modelClass;
    }

    @Override
    public BoundSql getBoundSql(Object parameterObject) {
        return cachedSqlSource(dynamicDialect.getCurrentDialect()).getBoundSql(parameterObject);
    }

    /**
     * 取该方言实例对应的 SqlSource，首次访问时构建并写入缓存。
     */
    private SqlSource cachedSqlSource(UpsertDialect dialect) {
        SqlSourceKey key = new SqlSourceKey(dialect, entityKey());
        return sqlSourceCache.get(key, cacheKey -> buildSqlSource(cacheKey.dialect));
    }

    /**
     * 缓存键里区分实体的部分：优先实体类名，回退表名。
     */
    private String entityKey() {
        return meta.getEntityClass() != null ? meta.getEntityClass().getName() : meta.getTableName();
    }

    private SqlSource buildSqlSource(UpsertDialect dialect) {
        String sql = dialect.buildUpsertSql(meta);
        return languageDriver.createSqlSource(configuration, "<script>" + sql + "</script>", modelClass);
    }

    private static final class SqlSourceKey {

        private final UpsertDialect dialect;
        private final String entityKey;

        SqlSourceKey(UpsertDialect dialect, String entityKey) {
            this.dialect = dialect;
            this.entityKey = entityKey;
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
            return entityKey.equals(that.entityKey)
                    && dialect.equals(that.dialect);
        }

        @Override
        public int hashCode() {
            return Objects.hash(dialect, entityKey);
        }
    }
}
