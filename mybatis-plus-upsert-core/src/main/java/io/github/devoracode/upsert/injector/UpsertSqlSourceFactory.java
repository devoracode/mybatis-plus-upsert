package io.github.devoracode.upsert.injector;

import io.github.devoracode.upsert.core.UpsertMeta;
import io.github.devoracode.upsert.core.fill.FillStrategy;
import io.github.devoracode.upsert.dialect.DynamicUpsertDialect;
import io.github.devoracode.upsert.dialect.UpsertDialect;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.session.Configuration;

/**
 * 创建 Upsert 语句 {@link SqlSource} 的包私有工厂，无状态。
 * 静态方言直接烘焙 SQL，{@link DynamicUpsertDialect} 改建 {@link RoutingUpsertSqlSource}。
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
                            Class<?> modelClass,
                            FillStrategy fillStrategy) {
        SqlSource delegate;
        if (dialect instanceof DynamicUpsertDialect) {
            delegate = new RoutingUpsertSqlSource(configuration,
                    languageDriver,
                    (DynamicUpsertDialect) dialect,
                    meta,
                    modelClass);
        } else {
            String sql = dialect.buildUpsertSql(meta);
            delegate = languageDriver.createSqlSource(configuration,
                    "<script>" + sql + "</script>",
                    modelClass);
        }
        if (fillStrategy != null && fillStrategy != FillStrategy.NONE) {
            delegate = new PreFillSqlSource(delegate, fillStrategy, configuration);
        }
        return new ParameterGuardSqlSource(delegate);
    }
}
