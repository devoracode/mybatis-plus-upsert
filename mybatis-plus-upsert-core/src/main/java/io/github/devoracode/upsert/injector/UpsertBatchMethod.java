package io.github.devoracode.upsert.injector;

import io.github.devoracode.upsert.core.UpsertMethodNames;
import io.github.devoracode.upsert.dialect.UpsertDialect;

public class UpsertBatchMethod extends AbstractUpsertMethod {

    public static final String METHOD_NAME = UpsertMethodNames.UPSERT_BATCH;

    public UpsertBatchMethod(UpsertDialect dialect) {
        super(METHOD_NAME, dialect, true);
    }
}
