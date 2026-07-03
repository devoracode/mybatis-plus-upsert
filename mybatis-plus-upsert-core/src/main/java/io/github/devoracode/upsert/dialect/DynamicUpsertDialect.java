package io.github.devoracode.upsert.dialect;

import io.github.devoracode.upsert.core.UpsertMeta;

/**
 * Extension of UpsertDialect for dynamic datasource support.
 * Allows resolving the actual dialect at runtime based on the current data source.
 */
public interface DynamicUpsertDialect extends UpsertDialect {

    /**
     * Get the dialect for the current data source context.
     * Called at runtime when executing upsert operations.
     *
     * @return the UpsertDialect to use for the current data source
     */
    UpsertDialect getCurrentDialect();
}