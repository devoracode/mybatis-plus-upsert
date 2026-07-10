package io.github.devoracode.upsert.injector;

import io.github.devoracode.upsert.core.UpsertMethodNames;
import io.github.devoracode.upsert.core.fill.UpsertFieldFillHandler;
import io.github.devoracode.upsert.dialect.UpsertDialect;

/**
 * Single-row upsert SQL injection method.
 *
 * @author devoracode
 * @since 1.0.0
 */
public class UpsertMethod extends AbstractUpsertMethod {

    /**
     * The method name registered in the mapper.
     */
    public static final String METHOD_NAME = UpsertMethodNames.UPSERT;

    /**
     * Creates a new UpsertMethod with the given dialect and fill handler.
     *
     * @param dialect     the upsert dialect to use for SQL generation
     * @param fillHandler the field fill handler for auto-filling
     */
    public UpsertMethod(UpsertDialect dialect, UpsertFieldFillHandler fillHandler) {
        super(METHOD_NAME, dialect, false, fillHandler);
    }
}