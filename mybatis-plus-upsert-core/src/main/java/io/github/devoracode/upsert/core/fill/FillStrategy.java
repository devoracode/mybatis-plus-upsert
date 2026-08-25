package io.github.devoracode.upsert.core.fill;

/**
 * Upsert 操作的自动填充策略，控制在上 Upsert SQL 绑定之前调用哪些
 * {@code MetaObjectHandler} 回调。
 *
 * <p>Upsert 是包含两种可能结果（插入或冲突更新）的单条语句。在填充时结果尚未确定，
 * 因此策略用于控制运行哪些处理器；分支级别的列正确性由生成的 SQL 单独保证
 * （INSERT 列列表和 UPDATE SET 子句已通过 {@code FieldStrategy}、{@code @IgnoreOnUpdate}
 * 及冲突键排除进行裁剪）。
 *
 * <ul>
 *   <li>{@link #NONE} — 不调用任何处理器；仅 MyBatis-Plus 的原生
 *       {@code insertFill}（在 SQL 绑定后运行）生效。</li>
 *   <li>{@link #INSERT} — {@code insertFill} 在 SQL 绑定前运行，确保
 *       带有 {@code NOT_NULL} 策略的填充注解字段包含在生成的列中。</li>
 *   <li>{@link #INSERT_UPDATE} — {@code insertFill} 和 {@code updateFill}
 *       均在 SQL 绑定前运行。这与"插入或更新"的语义一致，是默认策略。</li>
 * </ul>
 *
 * @author devoracode
 * @since 1.6.0
 */
public enum FillStrategy {

    /**
     * 不进行绑定前填充。MyBatis-Plus 的原生 {@code insertFill} 仍会在
     * {@code MybatisParameterHandler} 中运行，但发生在 SQL 绑定之后——
     * 绑定时刻为 null 的填充注解字段可能从动态 SQL 中被省略。
     */
    NONE,

    /**
     * 在 SQL 绑定前调用 {@code MetaObjectHandler.insertFill}。
     */
    INSERT,

    /**
     * 在 SQL 绑定前同时调用 {@code insertFill} 和 {@code updateFill}。
     * 默认策略。
     */
    INSERT_UPDATE
}
