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
 *
 * <p>单数据源用静态 {@link UpsertDialect}，SQL 构建一次即烘焙进 SqlSource；
 * 方言是 {@link DynamicUpsertDialect} 时改建 {@link RoutingUpsertSqlSource}，
 * 由它在运行时按数据源解析方言。
 *
 * <p>包装顺序：{@code fillStrategy} 非 {@link FillStrategy#NONE} 时套 {@link PreFillSqlSource}
 * （位于路由源之外——填充与方言无关，也不触碰按方言缓存的 SqlSource），
 * 最外层再统一套 {@link ParameterGuardSqlSource} 拒绝 null 参数。
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
