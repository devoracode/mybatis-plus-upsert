package io.github.devoracode.upsert.injector;

import io.github.devoracode.upsert.core.UpsertMethodNames;
import io.github.devoracode.upsert.dialect.UpsertDialect;

/**
 * Batch upsert SQL injection method that returns a list of {@code BatchResult}.
 * Used by the {@code upsert(Collection)} and {@code upsert(Collection, int)} methods in UpsertMapper.
 *
 * @author devoracode
 * @since 1.0.0
 */
public class UpsertExecutorMethod extends AbstractUpsertMethod {

    /**
     * The method name registered in the mapper.
     */
    public static final String METHOD_NAME = UpsertMethodNames.UPSERT_EXECUTOR;

    /**
     * Creates a new UpsertExecutorMethod with the given dialect.
     *
     * @param dialect the upsert dialect to use for SQL generation
     */
    public UpsertExecutorMethod(UpsertDialect dialect) {
        super(METHOD_NAME, dialect, false);
    }
}