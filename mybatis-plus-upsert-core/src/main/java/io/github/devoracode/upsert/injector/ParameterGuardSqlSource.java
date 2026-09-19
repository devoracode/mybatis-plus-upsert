package io.github.devoracode.upsert.injector;

import com.baomidou.mybatisplus.core.toolkit.Constants;
import io.github.devoracode.upsert.exception.UpsertException;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;

import java.util.Collection;
import java.util.Map;

/**
 * {@link SqlSource} 装饰器，在进入 Upsert SQL 生成与执行之前拒绝 {@code null} 参数。
 *
 * <p>{@code null} 实体不会被 MyBatis 拦下：它会把所有列绑定成 {@code NULL} 后照常执行，
 * 冲突键列非空时抛出的是数据库约束错误，冲突键列可空时则直接写入一条全空记录。
 * 两种结果都看不出真正的问题，因此这里统一给出 {@link UpsertException}。
 *
 * <p>三条路径的参数形态：
 * <ul>
 *   <li>{@code upsert} 与 {@code upsertExecutor}（{@code upsert(Collection)} 逐条提交用）：
 *       实体在 {@code et} 参数下，为 {@code null} 即拒绝；</li>
 *   <li>{@code upsertBatch}（单条多行 VALUES）：集合在 {@code list} 参数下，
 *       {@code null}、空集合与集合内的 {@code null} 元素都拒绝——多行 SQL 只要有一行是
 *       {@code null}，整条语句就已经没有意义。</li>
 * </ul>
 * 不经过 Mapper 代理、直接向 {@code SqlSession} 传入裸实体 / 裸集合时也识别。
 *
 * <p>本类只做参数形态判断，不触碰主键：回填语义（哪些路径回填、哪些不承诺）见
 * {@link io.github.devoracode.upsert.mapper.UpsertMapper}。
 *
 * <p>实例不可变且线程安全。
 *
 * @author devoracode
 * @since 1.6.2
 */
final class ParameterGuardSqlSource implements SqlSource {

    private final SqlSource delegate;
    private final boolean batch;

    /**
     * 在给定委托 SqlSource 外创建参数守卫。
     *
     * @param delegate 要包装的 Upsert SqlSource（永不为 null）
     * @param batch    委托方是否为多行 VALUES 批量 SQL
     */
    ParameterGuardSqlSource(SqlSource delegate, boolean batch) {
        this.delegate = delegate;
        this.batch = batch;
    }

    @Override
    public BoundSql getBoundSql(Object parameterObject) {
        if (batch) {
            requireBatchList(parameterObject);
        } else {
            requireEntity(parameterObject);
        }
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

    private void requireBatchList(Object parameterObject) {
        Object list = parameterObject instanceof Collection
                ? parameterObject
                : valueOfMap(parameterObject, Constants.LIST);
        if (!(list instanceof Collection)) {
            throw new UpsertException("Upsert batch parameter must be a non-null collection, but was: "
                    + (list == null ? "null" : list.getClass().getName()));
        }
        Collection<?> entities = (Collection<?>) list;
        if (entities.isEmpty()) {
            throw new UpsertException("Upsert batch collection must not be empty: the generated multi-row "
                    + "INSERT has no VALUES row to render.");
        }
        int index = 0;
        for (Object entity : entities) {
            if (entity == null) {
                throw new UpsertException("Upsert batch collection contains a null element at index " + index
                        + "; every row of the multi-row INSERT must be an entity instance.");
            }
            index++;
        }
    }

    /**
     * 读取命名参数值。MyBatis 的 {@code ParamMap} 在键缺失时抛异常，因此先判断键是否存在。
     *
     * @param parameterObject 参数对象（此处一定是 Map）
     * @param key             参数名
     * @return 参数值；键不存在或值为 null 时返回 null
     */
    private static Object valueOfMap(Object parameterObject, String key) {
        if (!(parameterObject instanceof Map)) {
            return null;
        }
        Map<?, ?> map = (Map<?, ?>) parameterObject;
        return map.containsKey(key) ? map.get(key) : null;
    }
}
