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
 * {@link SqlSource} that resolves the actual dialect at runtime based on the current data source.
 *
 * <p>Used in dynamic-datasource mode. Instead of binding to a fixed {@link UpsertDialect}, it
 * asks the {@link DynamicUpsertDialect} (via {@code DynamicDataSourceContextHolder}) which
 * dialect applies to the current thread's data source, then delegates SQL building to that dialect.
 * Each resolved dialect's parsed {@link SqlSource} is cached by a composite key.
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

    // Cache for parsed SqlSource per dialect class name
    // Key: dialectClassName + ":" + tableName + ":" + (batch ? "batch" : "single")
    private final ConcurrentHashMap<String, SqlSource> sqlSourceCache = new ConcurrentHashMap<>();

    /**
     * Creates a new routing SqlSource.
     *
     * @param configuration  the MyBatis configuration
     * @param languageDriver the language driver used to build per-dialect SqlSources
     * @param dynamicDialect the dynamic dialect that resolves the current data source's dialect
     * @param meta           the parsed upsert metadata
     * @param batch          whether to use batch upsert SQL
     * @param modelClass     the entity class
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
     * Resolves the current data source's dialect and delegates to its cached SqlSource.
     *
     * @param parameterObject the mapper invocation parameters
     * @return the bound SQL produced by the current dialect's SqlSource
     */
    @Override
    public BoundSql getBoundSql(Object parameterObject) {
        // Get current dialect at runtime
        UpsertDialect currentDialect = dynamicDialect.getCurrentDialect();
        String dialectClassName = currentDialect.getClass().getSimpleName();
        
        // Build cache key
        String entityKey = meta.getEntityClass() != null ? meta.getEntityClass().getName() : meta.getTableName();
        String cacheKey = dialectClassName + ":" + entityKey + ":" + (batch ? "batch" : "single");
        
        // Get or create SqlSource for this dialect
        SqlSource sqlSource = sqlSourceCache.computeIfAbsent(cacheKey, createSqlSource(currentDialect));
        
        // Delegate to the actual SqlSource
        return sqlSource.getBoundSql(parameterObject);
    }

    /**
     * Builds (and caches) a per-dialect SqlSource by generating upsert SQL from the
     * resolved dialect and wrapping it as a MyBatis script SqlSource.
     *
     * @param dialect the dialect to build the SqlSource for
     * @return a factory that creates the SqlSource for the given dialect cache key
     */
    private Function<String, SqlSource> createSqlSource(UpsertDialect dialect) {
        return key -> {
            String sql = batch
                    ? dialect.getCachedUpsertBatchSql(meta)
                    : dialect.getCachedUpsertSql(meta);
            return languageDriver.createSqlSource(configuration, "<script>" + sql + "</script>", modelClass);
        };
    }
}