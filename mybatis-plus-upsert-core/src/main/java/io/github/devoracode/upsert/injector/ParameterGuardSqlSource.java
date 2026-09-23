package io.github.devoracode.upsert.injector;

import com.baomidou.mybatisplus.core.toolkit.Constants;
import io.github.devoracode.upsert.exception.UpsertException;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;

import java.util.Map;

/**
 * 在填充与 SQL 绑定之前拒绝 {@code null} 实体的 {@link SqlSource} 装饰器，
 * 命中时抛出 {@link UpsertException}。实例不可变、线程安全。
 *
 * @author devoracode
 * @since 1.7.0
 */
final class ParameterGuardSqlSource implements SqlSource {

    private final SqlSource delegate;

    ParameterGuardSqlSource(SqlSource delegate) {
        this.delegate = delegate;
    }

    @Override
    public BoundSql getBoundSql(Object parameterObject) {
        requireEntity(parameterObject);
        return delegate.getBoundSql(parameterObject);
    }

    private void requireEntity(Object parameterObject) {
        // getOrDefault 而非 get：ParamMap 对缺失键抛 BindingException，须转成 UpsertException
        Object entity = parameterObject instanceof Map
                ? ((Map<?, ?>) parameterObject).getOrDefault(Constants.ENTITY, null)
                : parameterObject;
        if (entity == null) {
            throw new UpsertException("Upsert entity must not be null: a null entity binds every column to NULL "
                    + "instead of failing fast. Pass the entity you want to insert or update.");
        }
    }
}
