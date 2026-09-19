package io.github.devoracode.upsert.injector;

import io.github.devoracode.upsert.core.UpsertMeta;
import io.github.devoracode.upsert.core.fill.FillStrategy;
import io.github.devoracode.upsert.dialect.DynamicUpsertDialect;
import io.github.devoracode.upsert.dialect.UpsertDialect;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.session.Configuration;

/**
 * 为 Upsert 方法创建 MyBatis {@link SqlSource} 实例的工厂类。
 *
 * <p>在单数据源模式下，使用静态 {@link UpsertDialect}，SQL 构建一次并烘焙到
 * {@code SqlSource} 中（缓存于 {@code MappedStatement}）。
 * 在动态数据源模式下（当时态为 {@link DynamicUpsertDialect}），
 * 改为创建 {@link RoutingUpsertSqlSource}，以便在每个数据源运行时解析实际方言。
 *
 * <p>当 {@code fillStrategy} 不是 {@link FillStrategy#NONE} 时，生成的 SqlSource
 * 会被包装在 {@link PreFillSqlSource} 中，使 MyBatis-Plus 自动填充在动态
 * SQL 绑定之前运行。在动态数据源模式下，包装器位于路由源之外：
 * 填充与方言无关，在每次 {@code getBoundSql} 调用时运行一次，
 * 在方言路由之前执行，且不触碰路由源按方言缓存的 SqlSource。
 *
 * <p>最后统一包一层 {@link ParameterGuardSqlSource}，使 {@code null} 实体 /
 * {@code null} 集合元素在进入填充与 SQL 绑定之前就被拒绝。
 *
 * <p>此类为包私有且无状态，所有方法均为线程安全。
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
                            FillStrategy fillStrategy) {
        SqlSource delegate;
        // 若使用动态数据源，创建路由 SqlSource 以便在运行时解析方言
        if (dialect instanceof DynamicUpsertDialect) {
            delegate = new RoutingUpsertSqlSource(configuration,
                    languageDriver,
                    (DynamicUpsertDialect) dialect,
                    meta,
                    batch,
                    modelClass);
        } else {
            // 单数据源模式：构建一次 SQL 并烘焙到 SqlSource
            String sql = batch ? dialect.buildUpsertBatchSql(meta) : dialect.buildUpsertSql(meta);
            delegate = languageDriver.createSqlSource(configuration,
                    "<script>" + sql + "</script>",
                    modelClass);
        }
        if (fillStrategy != null && fillStrategy != FillStrategy.NONE) {
            delegate = new PreFillSqlSource(delegate, fillStrategy, configuration);
        }
        return new ParameterGuardSqlSource(delegate, batch);
    }
}
