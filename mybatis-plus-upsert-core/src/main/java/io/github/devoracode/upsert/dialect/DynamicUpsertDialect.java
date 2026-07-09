package io.github.devoracode.upsert.dialect;

/**
 * Extension of {@link UpsertDialect} for dynamic datasource support.
 * Allows resolving the actual dialect at runtime based on the current data source context.
 *
 * @author devoracode
 * @since 1.2.0
 */
public interface DynamicUpsertDialect extends UpsertDialect {

    /**
     * Gets the dialect for the current data source context.
     * Called at runtime when executing upsert operations.
     *
     * @return the {@link UpsertDialect} to use for the current data source
     */
    UpsertDialect getCurrentDialect();
}