package io.github.devoracode.upsert.injector;

import io.github.devoracode.upsert.core.UpsertMeta;
import io.github.devoracode.upsert.dialect.DynamicUpsertDialect;
import io.github.devoracode.upsert.dialect.UpsertDialect;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.session.Configuration;

final class UpsertSqlSourceFactory {

    private UpsertSqlSourceFactory() {
    }

    static SqlSource create(Configuration configuration,
                            LanguageDriver languageDriver,
                            UpsertMeta meta,
                            UpsertDialect dialect,
                            boolean batch,
                            Class<?> modelClass) {
        // If using dynamic datasource, create a routing SqlSource that resolves dialect at runtime
        if (dialect instanceof DynamicUpsertDialect) {
            return new RoutingUpsertSqlSource(configuration, languageDriver,
                    (DynamicUpsertDialect) dialect, meta, batch, modelClass);
        }
        // Single datasource mode: create SqlSource with static dialect
        String sql = batch
                ? dialect.getCachedUpsertBatchSql(meta)
                : dialect.getCachedUpsertSql(meta);
        return languageDriver.createSqlSource(configuration, "<script>" + sql + "</script>", modelClass);
    }
}
