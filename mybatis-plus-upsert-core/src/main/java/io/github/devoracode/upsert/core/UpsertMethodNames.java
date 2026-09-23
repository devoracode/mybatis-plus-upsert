package io.github.devoracode.upsert.core;

import io.github.devoracode.upsert.injector.UpsertSqlInjector;

import java.util.Collections;
import java.util.Set;

/**
 * {@link UpsertSqlInjector} 注入的语句名常量，与
 * {@link io.github.devoracode.upsert.mapper.UpsertMapper} 中的方法名一一对应。
 *
 * @author devoracode
 * @since 1.0.0
 */
public final class UpsertMethodNames {

    /** 单行 Upsert 语句名。 */
    public static final String UPSERT = "upsert";

    /** 全部注入语句名。 */
    public static final Set<String> ALL = Collections.singleton(UPSERT);

    private UpsertMethodNames() {
    }
}
