package io.github.devoracode.upsert.core.fill;

/**
 * 控制 Upsert 在 SQL 绑定之前调用哪些 {@code MetaObjectHandler} 回调。
 *
 * @author devoracode
 * @since 1.6.0
 */
public enum FillStrategy {

    /** 不做绑定前填充，仅剩 MP 原生 {@code insertFill}（发生在 SQL 绑定之后）。 */
    NONE,

    /** 绑定前调用 {@code insertFill}。 */
    INSERT,

    /** 绑定前同时调用 {@code insertFill} 和 {@code updateFill}，对应“插入或更新”语义。默认策略。 */
    INSERT_UPDATE
}
