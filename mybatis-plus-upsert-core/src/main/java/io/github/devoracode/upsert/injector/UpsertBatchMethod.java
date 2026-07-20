package io.github.devoracode.upsert.injector;

import io.github.devoracode.upsert.core.UpsertMethodNames;
import io.github.devoracode.upsert.dialect.UpsertDialect;

/**
 * Batch upsert SQL injection method.
 *
 * @author devoracode
 * @since 1.0.0
 */
public class UpsertBatchMethod extends AbstractUpsertMethod {

    /**
     * The method name registered in the mapper.
     */
    public static final String METHOD_NAME = UpsertMethodNames.UPSERT_BATCH;

    /**
     * Creates a new UpsertBatchMethod with the given dialect.
     *
     * @param dialect the upsert dialect to use for SQL generation
     */
    public UpsertBatchMethod(UpsertDialect dialect) {
        super(METHOD_NAME, dialect, true);
    }
}
