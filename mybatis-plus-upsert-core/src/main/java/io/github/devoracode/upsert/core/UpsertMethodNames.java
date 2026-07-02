package io.github.devoracode.upsert.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class UpsertMethodNames {

    public static final String UPSERT = "upsert";

    public static final String UPSERT_BATCH = "upsertBatch";

    public static final String UPSERT_EXECUTOR = "upsertExecutor";

    public static final Set<String> ALL = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(UPSERT, UPSERT_BATCH, UPSERT_EXECUTOR)));

    private UpsertMethodNames() {
    }
}
