package io.github.devoracode.upsert.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 限制 UPDATE 子句仅包含使用 {@code @UpdateColumn} 注解的字段。
 *
 * <p>默认情况下，所有非冲突键、非 {@link IgnoreOnUpdate} 的字段都会被包含在 UPDATE 子句中。
 * 当任意字段使用 {@code @UpdateColumn} 注解时，UPDATE 子句中仅包含被注解的字段。
 *
 * <p>此注解适用于需要精确控制冲突时哪些列应被更新的场景。
 *
 * @author devoracode
 * @since 1.0.0
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface UpdateColumn {
}
