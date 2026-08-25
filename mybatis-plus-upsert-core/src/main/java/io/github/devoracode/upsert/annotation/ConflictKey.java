package io.github.devoracode.upsert.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个字段为 Upsert（插入或更新）操作中的冲突键。
 *
 * <p>实体类必须至少有一个字段使用 {@code @ConflictKey} 注解。
 * order 属性控制冲突键字段的评估顺序，在冲突解决时，值越小的字段越先被检查。
 *
 * @author devoracode
 * @since 1.0.0
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ConflictKey {

    /**
     * 此冲突键字段的评估顺序。
     * 在冲突解决过程中，值越小则越先被检查。
     * 未显式指定 order 的字段默认为 0。
     *
     * @return 顺序值（默认 0）
     */
    int order() default 0;
}
