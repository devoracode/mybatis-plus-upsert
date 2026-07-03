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
 * SqlSource that routes to the appropriate dialect at runtime based on the current data source.
 * Uses DynamicDataSourceContextHolder from dynamic-datasource-spring-boot-starter to get the current data source.
 */
final class RoutingUpsertSqlSource implements SqlSource {

    private final Configuration configuration;
    private final LanguageDriver languageDriver;
    private final DynamicUpsertDialect dynamicDialect;
    private final UpsertMeta meta;
    private final boolean batch;
    private final Class<?> modelClass;

    // Cache for parsed SqlSource per dialect class name
    // Key: dialectClassName + ":" + tableName + ":" + (batch ? "batch" : "single")
    private final ConcurrentHashMap<String, SqlSource> sqlSourceCache = new ConcurrentHashMap<>();

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

    @Override
    public BoundSql getBoundSql(Object parameterObject) {
        // Get current dialect at runtime
        UpsertDialect currentDialect = dynamicDialect.getCurrentDialect();
        String dialectClassName = currentDialect.getClass().getSimpleName();
        
        // Build cache key
        String cacheKey = dialectClassName + ":" + meta.getTableName() + ":" + (batch ? "batch" : "single");
        
        // Get or create SqlSource for this dialect
        SqlSource sqlSource = sqlSourceCache.computeIfAbsent(cacheKey, createSqlSource(currentDialect));
        
        // Delegate to the actual SqlSource
        return sqlSource.getBoundSql(parameterObject);
    }

    private Function<String, SqlSource> createSqlSource(UpsertDialect dialect) {
        return key -> {
            String sql = batch
                    ? dialect.getCachedUpsertBatchSql(meta)
                    : dialect.getCachedUpsertSql(meta);
            return languageDriver.createSqlSource(configuration, "<script>" + sql + "</script>", modelClass);
        };
    }
}