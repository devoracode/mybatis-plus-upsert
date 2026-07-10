package io.github.devoracode.upsert.injector;

import io.github.devoracode.upsert.core.UpsertMeta;
import io.github.devoracode.upsert.core.fill.UpsertFieldFillHandler;
import io.github.devoracode.upsert.dialect.DynamicUpsertDialect;
import io.github.devoracode.upsert.dialect.UpsertDialect;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.session.Configuration;

/**
 * Factory for creating MyBatis {@link SqlSource} instances for upsert methods.
 *
 * <p>In single-datasource mode, a static {@link UpsertDialect} is used and the SQL is built
 * once and cached by the dialect. In dynamic-datasource mode (when the dialect is a
 * {@link DynamicUpsertDialect}), a {@link RoutingUpsertSqlSource} is created instead so the
 * actual dialect can be resolved at runtime per data source.
 *
 * <p>The resulting SqlSource is wrapped in a {@link FillableSqlSource} so that field
 * auto-filling runs before the SQL is bound.
 *
 * <p>This class is package-private and stateless. All methods are thread-safe.
 *
 * @author devoracode
 * @since 1.2.0
 */
final class UpsertSqlSourceFactory {

    private UpsertSqlSourceFactory() {
    }

    static SqlSource create(Configuration configuration,
                            LanguageDriver languageDriver,
                            UpsertMeta meta,
                            UpsertDialect dialect,
                            boolean batch,
                            Class<?> modelClass,
                            UpsertFieldFillHandler fillHandler) {
        SqlSource rawSqlSource;
        // If using dynamic datasource, create a routing SqlSource that resolves dialect at runtime
        if (dialect instanceof DynamicUpsertDialect) {
            rawSqlSource = new RoutingUpsertSqlSource(configuration, languageDriver,
                    (DynamicUpsertDialect) dialect, meta, batch, modelClass, fillHandler);
        } else {
            // Single datasource mode: create SqlSource with static dialect
            String sql = batch
                    ? dialect.getCachedUpsertBatchSql(meta)
                    : dialect.getCachedUpsertSql(meta);
            rawSqlSource = languageDriver.createSqlSource(configuration, "<script>" + sql + "</script>", modelClass);
        }
        return new FillableSqlSource(rawSqlSource, fillHandler, batch, configuration);
    }
}
