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
     * @return 当前数据源应使用的 {@link UpsertDialect}
     * @throws io.github.devoracode.upsert.exception.UpsertException 当前数据源没有已注册的方言
     */
    UpsertDialect getCurrentDialect();

    /**
     * 为指定数据源注册方言。启动期静态声明（{@code spring.datasource.dynamic.datasource}）
     * 之外的数据源——由 {@code DynamicDataSourceProvider} 从配置中心下发，或运行期
     * {@code addDataSource} 加入的——需要用本方法补注册，否则调用 upsert 时会因
     * 找不到方言而失败。
     *
     * @param dataSourceName 数据源名，与 {@code DynamicDataSourceContextHolder} 中的栈顶一致
     * @param dialect        该数据源使用的方言
     * @since 1.7.1
     */
    void addDialect(String dataSourceName, UpsertDialect dialect);
}
