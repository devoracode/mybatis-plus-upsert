package io.github.devoracode.upsert.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记字段为 Upsert 的冲突键：命中该列（组合）已有行时转为更新，否则插入。
 *
 * <p>实体必须至少有一个此注解的字段，否则该 Mapper 不会注入 Upsert 语句。
 * 多个冲突键列按 {@link #order()} 升序排列。
 *
 * @author devoracode
 * @since 1.0.0
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ConflictKey {

    /**
     * 冲突键列的排序权重，值小者在前，未指定时为 0。
     *
     * @return 顺序值
     */
    int order() default 0;
}
