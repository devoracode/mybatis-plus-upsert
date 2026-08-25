package io.github.devoracode.upsert.dialect;

/**
 * {@link UpsertDialect} 的动态数据源扩展接口。
 * 允许根据当前数据源上下文在运行时解析实际方言。
 *
 * @author devoracode
 * @since 1.2.0
 */
public interface DynamicUpsertDialect extends UpsertDialect {

    /**
     * 获取当前数据源上下文对应的方言。
     * 在执行 Upsert 操作时在运行时调用。
     *
     * @return 当前数据源应使用的 {@link UpsertDialect}
     */
    UpsertDialect getCurrentDialect();
}
