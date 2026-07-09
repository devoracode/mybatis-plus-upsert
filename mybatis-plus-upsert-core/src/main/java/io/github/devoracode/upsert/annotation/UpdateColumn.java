package io.github.devoracode.upsert.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Restricts the UPDATE clause to only the fields annotated with {@code @UpdateColumn}.
 *
 * <p>By default, all non-conflict-key, non-{@link IgnoreOnUpdate} fields are included
 * in the UPDATE clause. When any field is annotated with {@code @UpdateColumn},
 * ONLY those annotated fields will be included in the UPDATE clause.
 *
 * <p>This is useful for fine-grained control over which columns should be updated on conflict.
 *
 * <p>Example:
 * <pre>{@code
 * &#64;UpdateColumn
 * private String name;
 *
 * &#64;UpdateColumn
 * private String age;
 *
 * // email will NOT be updated on conflict
 * private String email;
 * }</pre>
 *
 * @author devoracode
 * @since 1.0.0
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface UpdateColumn {
}