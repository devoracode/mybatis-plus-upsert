package io.github.devoracode.upsert.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field to be excluded from the UPDATE clause during upsert operations.
 *
 * <p>Fields annotated with {@code @IgnoreOnUpdate} will still be included in the INSERT
 * clause but will not appear in the ON DUPLICATE KEY UPDATE / ON CONFLICT DO UPDATE / MERGE WHEN MATCHED clauses.
 *
 * <p>Typical use case: exclude auto-managed fields like {@code create_time} or {@code created_by}
 * from being overwritten on conflict.
 *
 * @author devoracode
 * @since 1.0.0
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface IgnoreOnUpdate {
}