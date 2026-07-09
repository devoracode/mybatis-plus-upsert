package io.github.devoracode.upsert.core;

import io.github.devoracode.upsert.injector.UpsertSqlInjector;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Constant names for the upsert SQL methods injected by {@link UpsertSqlInjector}.
 * These correspond to the method names in {@link io.github.devoracode.upsert.mapper.UpsertMapper}.
 *
 * @author devoracode
 * @since 1.0.0
 */
public final class UpsertMethodNames {

    /**
     * Single-row upsert method name.
     */
    public static final String UPSERT = "upsert";

    /**
     * Batch upsert method name.
     */
    public static final String UPSERT_BATCH = "upsertBatch";

    /**
     * Batch upsert with result method name (returns BatchResult list).
     */
    public static final String UPSERT_EXECUTOR = "upsertExecutor";

    /**
     * Unmodifiable set of all upsert method names.
     */
    public static final Set<String> ALL = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(UPSERT, UPSERT_BATCH, UPSERT_EXECUTOR)));

    private UpsertMethodNames() {
    }
}