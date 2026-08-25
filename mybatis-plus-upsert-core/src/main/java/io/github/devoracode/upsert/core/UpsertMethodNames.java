package io.github.devoracode.upsert.core;

import io.github.devoracode.upsert.injector.UpsertSqlInjector;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * {@link UpsertSqlInjector} 注入的 Upsert SQL 方法名常量。
 * 与 {@link io.github.devoracode.upsert.mapper.UpsertMapper} 中的方法名一一对应。
 *
 * @author devoracode
 * @since 1.0.0
 */
public final class UpsertMethodNames {

    /**
     * 单行 Upsert 方法名。
     */
    public static final String UPSERT = "upsert";

    /**
     * 批量 Upsert 方法名。
     */
    public static final String UPSERT_BATCH = "upsertBatch";

    /**
     * 带结果返回的批量 Upsert 方法名（返回 BatchResult 列表）。
     */
    public static final String UPSERT_EXECUTOR = "upsertExecutor";

    /**
     * 包含所有 Upsert 方法名的不可变集合。
     */
    public static final Set<String> ALL = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(UPSERT, UPSERT_BATCH, UPSERT_EXECUTOR)));

    private UpsertMethodNames() {
    }
}
