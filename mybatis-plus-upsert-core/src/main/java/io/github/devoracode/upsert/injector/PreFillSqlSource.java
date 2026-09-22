package io.github.devoracode.upsert.injector;

import io.github.devoracode.upsert.core.fill.FillStrategy;
import io.github.devoracode.upsert.core.fill.UpsertFillProcessor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.session.Configuration;

/**
 * 在委托 SqlSource 绑定动态 Upsert SQL 之前先应用 MP 自动填充的 {@link SqlSource} 装饰器。
 * 实例不可变、线程安全。
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
