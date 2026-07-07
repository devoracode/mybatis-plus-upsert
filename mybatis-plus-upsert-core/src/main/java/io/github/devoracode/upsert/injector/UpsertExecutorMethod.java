package io.github.devoracode.upsert.injector;

import io.github.devoracode.upsert.core.UpsertMethodNames;
import io.github.devoracode.upsert.dialect.UpsertDialect;

public class UpsertExecutorMethod extends AbstractUpsertMethod {

    public static final String METHOD_NAME = UpsertMethodNames.UPSERT_EXECUTOR;

    public UpsertExecutorMethod(UpsertDialect dialect) {
        super(METHOD_NAME, dialect, false);
    }
}
