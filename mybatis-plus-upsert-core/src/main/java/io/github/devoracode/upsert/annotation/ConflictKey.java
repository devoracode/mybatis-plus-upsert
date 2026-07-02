package io.github.devoracode.upsert.annotation;

import java.lang.annotation.*;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ConflictKey {

    int order() default 0;
}
