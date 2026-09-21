package io.github.devoracode.upsert.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 将字段从 Upsert 的 UPDATE 子句中排除：仍参与 INSERT，冲突命中时不被覆盖。
 *
 * <p>典型用于系统托管字段（{@code create_time}、{@code created_by}）。
 *
 * @author devoracode
 * @since 1.0.0
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface IgnoreOnUpdate {
}
