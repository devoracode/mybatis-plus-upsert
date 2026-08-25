package io.github.devoracode.upsert.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个字段在 Upsert 操作的 UPDATE 子句中被排除。
 *
 * <p>使用 {@code @IgnoreOnUpdate} 注解的字段仍会包含在 INSERT 子句中，
 * 但不会出现在 ON DUPLICATE KEY UPDATE / ON CONFLICT DO UPDATE / MERGE WHEN MATCHED 子句中。
 *
 * <p>典型使用场景：排除由系统自动管理的字段（如 {@code create_time}、{@code created_by}），
 * 使其在发生冲突时不被覆盖。
 *
 * @author devoracode
 * @since 1.0.0
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface IgnoreOnUpdate {
}
