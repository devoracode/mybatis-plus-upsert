package io.github.devoracode.upsert.dialect;

/**
 * {@link UpsertDialect} 的动态数据源扩展：实现类把调用转发给当前数据源上下文对应的方言，
 * 注入器据此改用按方言实例路由的 SqlSource，而不是在启动时固定一份 SQL。
 *
 * @author devoracode
 * @since 1.2.0
 */
public interface DynamicUpsertDialect extends UpsertDialect {

    /**
     * 解析当前数据源上下文对应的方言，每次执行 Upsert 时调用。
     *
     * <p>实现应按数据源返回稳定实例：每次新建实例会让路由缓存无法命中，SQL 被反复重建。
     *
     * @return 当前数据源应使用的 {@link UpsertDialect}
     */
    UpsertDialect getCurrentDialect();
}
