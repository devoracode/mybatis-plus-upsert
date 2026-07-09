package io.github.devoracode.upsert.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as a conflict key for upsert operations.
 *
 * <p>The entity class must have at least one field annotated with {@code @ConflictKey}.
 * The order attribute controls the sequence in which conflict key fields are evaluated
 * during conflict resolution. Lower values are checked first.
 *
 * @author devoracode
 * @since 1.0.0
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ConflictKey {

    /**
     * The evaluation order of this conflict key field.
     * Lower values are checked first during conflict resolution.
     * Fields without an explicit order default to 0.
     *
     * @return the order value (default 0)
     */
    int order() default 0;
}