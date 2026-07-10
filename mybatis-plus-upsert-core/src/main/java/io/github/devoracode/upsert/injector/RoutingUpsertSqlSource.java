package io.github.devoracode.upsert.injector;

import io.github.devoracode.upsert.core.UpsertMeta;
import io.github.devoracode.upsert.core.fill.UpsertFieldFillHandler;
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
 * <p>The per-dialect SqlSource is wrapped in a {@link FillableSqlSource} so that field
 * auto-filling runs before the SQL is bound.
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
    private final UpsertFieldFillHandler fillHandler;

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
     * @param fillHandler    the field fill handler for auto-filling
     */
    RoutingUpsertSqlSource(Configuration configuration,
                           LanguageDriver languageDriver,
                           DynamicUpsertDialect dynamicDialect,
                           UpsertMeta meta,
                           boolean batch,
                           Class<?> modelClass,
                           UpsertFieldFillHandler fillHandler) {
        this.configuration = configuration;
        this.languageDriver = languageDriver;
        this.dynamicDialect = dynamicDialect;
        this.meta = meta;
        this.batch = batch;
        this.modelClass = modelClass;
        this.fillHandler = fillHandler;
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
        
        // Delegate to the actual SqlSource (already wrapped with FillableSqlSource)
        return sqlSource.getBoundSql(parameterObject);
    }

    /**
     * Builds (and caches) a per-dialect SqlSource by generating upsert SQL from the
     * resolved dialect, wrapping it as a MyBatis script SqlSource, and then wrapping
     * that in a {@link FillableSqlSource} for auto-filling.
     *
     * @param dialect the dialect to build the SqlSource for
     * @return a factory that creates the SqlSource for the given dialect cache key
     */
    private Function<String, SqlSource> createSqlSource(UpsertDialect dialect) {
        return key -> {
            String sql = batch
                    ? dialect.getCachedUpsertBatchSql(meta)
                    : dialect.getCachedUpsertSql(meta);
            SqlSource raw = languageDriver.createSqlSource(configuration, "<script>" + sql + "</script>", modelClass);
            return new FillableSqlSource(raw, fillHandler, batch, configuration);
        };
    }
}
