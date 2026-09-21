package io.github.devoracode.upsert.injector;

import io.github.devoracode.upsert.core.fill.FillStrategy;
import io.github.devoracode.upsert.core.fill.UpsertFillProcessor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.session.Configuration;

/**
 * 在委托 SqlSource 绑定动态 Upsert SQL 之前先应用 MP 自动填充的 {@link SqlSource} 装饰器。
 *
 * <p>填充改的是参数 map 指向的<em>实体对象</em>，所以委托方（静态 SqlSource 或
 * {@link RoutingUpsertSqlSource}）求 {@code <if>} 条件时看到的已是填充后的值。
 * 每次 {@code getBoundSql} 恰好填充一次，且在方言路由之前发生，不影响路由源的按方言缓存。
 *
 * <p>只有 {@link UpsertSqlSourceFactory} 产出的 SqlSource 会被包装：不注册全局拦截器，
 * 与分页/乐观锁等插件的顺序无关，其他语句零开销。实例不可变、线程安全。
 *
 * @author devoracode
 * @since 1.6.0
 */
final class PreFillSqlSource implements SqlSource {

    private final SqlSource delegate;
    private final FillStrategy strategy;
    private final Configuration configuration;

    /**
     * @param strategy 永不为 {@code NONE}——工厂在该情况下不做包装
     */
    PreFillSqlSource(SqlSource delegate, FillStrategy strategy, Configuration configuration) {
        this.delegate = delegate;
        this.strategy = strategy;
        this.configuration = configuration;
    }

    @Override
    public BoundSql getBoundSql(Object parameterObject) {
        UpsertFillProcessor.fill(parameterObject, configuration, strategy);
        return delegate.getBoundSql(parameterObject);
    }
}
