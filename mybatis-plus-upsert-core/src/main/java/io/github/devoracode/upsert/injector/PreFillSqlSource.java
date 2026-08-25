package io.github.devoracode.upsert.injector;

import io.github.devoracode.upsert.core.fill.FillStrategy;
import io.github.devoracode.upsert.core.fill.UpsertFillProcessor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.session.Configuration;

/**
 * {@link SqlSource} 装饰器，在委托 SqlSource 绑定动态 Upsert SQL 之前
 * 应用 MyBatis-Plus 自动填充。
 *
 * <p>填充通过 {@code MetaObject} 修改参数 map 引用到的<em>实体对象</em>，
 * 因此委托方——静态 Upsert SqlSource 或 {@link RoutingUpsertSqlSource}——
 * 对其 {@code <if>} 条件基于已填充的值进行求值。在动态数据源模式下，
 * 填充在每次 {@code getBoundSql} 调用时恰好运行一次，在方言路由之前执行；
 * 路由源的按方言 SqlSource 缓存不受影响。
 *
 * <p>仅由 {@link UpsertSqlSourceFactory} 创建的 SqlSource 会被包装，
 * 因此无关的用户语句不会经过此类——没有全局拦截器、没有插件排序依赖、
 * 也没有语句名匹配逻辑。
 *
 * <p>实例不可变且线程安全。
 *
 * @author devoracode
 * @since 1.6.0
 */
final class PreFillSqlSource implements SqlSource {

    private final SqlSource delegate;
    private final FillStrategy strategy;
    private final Configuration configuration;

    /**
     * 在给定委托 SqlSource 外创建填充包装器。
     *
     * @param delegate      要包装的 Upsert SqlSource（永不为 null）
     * @param strategy      填充策略；永不为 {@code NONE}（工厂在此情况下不做包装）
     * @param configuration 用于在调用时解析 {@code MetaObjectHandler} 的 MyBatis 配置
     */
    PreFillSqlSource(SqlSource delegate, FillStrategy strategy, Configuration configuration) {
        this.delegate = delegate;
        this.strategy = strategy;
        this.configuration = configuration;
    }

    /**
     * 对 Upsert 实体应用自动填充，然后委托 SQL 绑定。
     *
     * @param parameterObject 映射器调用参数
     * @return 由委托方产生的已绑定 SQL
     */
    @Override
    public BoundSql getBoundSql(Object parameterObject) {
        UpsertFillProcessor.fill(parameterObject, configuration, strategy);
        return delegate.getBoundSql(parameterObject);
    }
}
