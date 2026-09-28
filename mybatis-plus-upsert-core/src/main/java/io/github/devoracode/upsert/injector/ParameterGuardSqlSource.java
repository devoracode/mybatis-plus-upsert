package io.github.devoracode.upsert.injector;

import com.baomidou.mybatisplus.core.toolkit.Constants;
import io.github.devoracode.upsert.exception.UpsertException;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;

import java.util.List;
import java.util.Map;

/**
 * 在 SQL 绑定之前拒绝 {@code null} 实体或 {@code null} 冲突键的 {@link SqlSource} 装饰器，
 * 命中时抛出 {@link UpsertException}。实例不可变、线程安全。
 *
 * @author devoracode
 * @since 1.7.0
 */
final class ParameterGuardSqlSource implements SqlSource {

    private final SqlSource delegate;
    private final Configuration configuration;
    private final List<String> conflictFields;

    ParameterGuardSqlSource(SqlSource delegate, Configuration configuration, List<String> conflictFields) {
        this.delegate = delegate;
        this.configuration = configuration;
        this.conflictFields = conflictFields;
    }

    @Override
    public BoundSql getBoundSql(Object parameterObject) {
        Object entity = requireEntity(parameterObject);
        MetaObject metaObject = configuration.newMetaObject(entity);
        for (String conflictField : conflictFields) {
            if (metaObject.getValue(conflictField) == null) {
                throw new UpsertException("Upsert conflict key must not be null: field '" + conflictField
                        + "' is required for conflict detection. A null key never matches an existing row, "
                        + "so the statement would always insert a duplicate.");
            }
        }
        return delegate.getBoundSql(parameterObject);
    }

    private Object requireEntity(Object parameterObject) {
        // getOrDefault 而非 get：ParamMap 对缺失键抛 BindingException，须转成 UpsertException
        Object entity = parameterObject instanceof Map
                ? ((Map<?, ?>) parameterObject).getOrDefault(Constants.ENTITY, null)
                : parameterObject;
        if (entity == null) {
            throw new UpsertException("Upsert entity must not be null: a null entity binds every column to NULL "
                    + "instead of failing fast. Pass the entity you want to insert or update.");
        }
        return entity;
    }
}
