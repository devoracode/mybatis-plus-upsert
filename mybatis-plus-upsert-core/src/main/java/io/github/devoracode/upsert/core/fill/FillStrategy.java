package io.github.devoracode.upsert.core.fill;

/**
 * 控制 Upsert 在 SQL 绑定之前调用哪些 {@code MetaObjectHandler} 回调。
 *
 * <p>Upsert 一条语句可能有插入或冲突更新两种结果，填充时结果尚未确定，所以由策略决定
 * 运行哪些回调；分支级别的列正确性由生成的 SQL 自己保证（INSERT 列表与 UPDATE SET
 * 已按 {@code FieldStrategy}、{@code @IgnoreOnUpdate} 和冲突键排除裁剪过）。
 *
 * @author devoracode
 * @since 1.6.0
 */
public enum FillStrategy {

    /**
     * 不做绑定前填充。MP 原生的 {@code insertFill} 仍会在 {@code MybatisParameterHandler}
     * 中运行，但那已在 SQL 绑定之后——绑定时为 null 的填充字段会被动态列裁剪掉。
     */
    NONE,

    /**
     * 绑定前调用 {@code insertFill}。
     */
    INSERT,

    /**
     * 绑定前同时调用 {@code insertFill} 和 {@code updateFill}，对应"插入或更新"语义。默认策略。
     */
    INSERT_UPDATE
}
