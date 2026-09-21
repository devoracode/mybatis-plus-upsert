package io.github.devoracode.upsert.injector;

import com.baomidou.mybatisplus.core.toolkit.Constants;
import io.github.devoracode.upsert.exception.UpsertException;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;

import java.util.Map;

/**
 * 在填充与 SQL 绑定之前拒绝 {@code null} 参数的 {@link SqlSource} 装饰器。
 *
 * <p>MyBatis 自己不会拦下 {@code null} 实体：它把所有列绑成 {@code NULL} 照常执行，
 * 冲突键列非空时报的是看不出根因的数据库约束错误，可空时直接写入一条全空记录。
 * 两种结果都不如一条明确的 {@link UpsertException} 好排查。
 *
 * <p>注入的两条语句（{@code upsert} 与 {@code upsertExecutor}）参数形态一致：实体在
 * {@code et} 参数下，为 null 即拒绝，{@code upsert(Collection)} 里的 {@code null} 元素
 * 因此在轮到它排队执行时被拒而不是整批预扫描；不经 Mapper 代理直接传裸实体时也识别。
 * 本类只判断形态、不触碰主键。实例不可变、线程安全。
 *
 * @author devoracode
 * @since 1.6.2
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
        Object entity = parameterObject instanceof Map
                ? valueOfMap(parameterObject, Constants.ENTITY)
                : parameterObject;
        if (entity == null) {
            throw new UpsertException("Upsert entity must not be null: a null entity binds every column to NULL "
                    + "instead of failing fast. Pass the entity you want to insert or update.");
        }
    }

    /**
     * 读命名参数值。先判 {@code containsKey} 是因为 {@code ParamMap.get()} 对缺失键抛异常。
     */
    private static Object valueOfMap(Object parameterObject, String key) {
        if (!(parameterObject instanceof Map)) {
            return null;
        }
        Map<?, ?> map = (Map<?, ?>) parameterObject;
        return map.containsKey(key) ? map.get(key) : null;
    }
}
