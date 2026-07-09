package io.github.devoracode.upsert.dialect;

import io.github.devoracode.upsert.core.UpsertMeta;

/**
 * Interface for building upsert SQL statements for a specific database dialect.
 * Implementations are stateless and thread-safe.
 *
 * @author devoracode
 * @since 1.0.0
 */
public interface UpsertDialect {

    /**
     * Builds the single-row upsert SQL for the given metadata.
     *
     * @param meta the upsert metadata containing table name, columns, conflict keys, etc.
     * @return the generated SQL string
     */
    String buildUpsertSql(UpsertMeta meta);

    /**
     * Builds the batch upsert SQL for the given metadata.
     *
     * @param meta the upsert metadata containing table name, columns, conflict keys, etc.
     * @return the generated SQL string
     */
    String buildUpsertBatchSql(UpsertMeta meta);

    /**
     * Gets the cached single-row upsert SQL, generating it on first call.
     *
     * @param meta the upsert metadata
     * @return the cached or newly generated SQL string
     */
    default String getCachedUpsertSql(UpsertMeta meta) {
        String entityKey = meta.getEntityClass() != null ? meta.getEntityClass().getName() : meta.getTableName();
        String key = getClass().getSimpleName() + ":" + entityKey + ":single";
        return UpsertSqlCache.get(key, k -> buildUpsertSql(meta));
    }

    /**
     * Gets the cached batch upsert SQL, generating it on first call.
     *
     * @param meta the upsert metadata
     * @return the cached or newly generated SQL string
     */
    default String getCachedUpsertBatchSql(UpsertMeta meta) {
        String entityKey = meta.getEntityClass() != null ? meta.getEntityClass().getName() : meta.getTableName();
        String key = getClass().getSimpleName() + ":" + entityKey + ":batch";
        return UpsertSqlCache.get(key, k -> buildUpsertBatchSql(meta));
    }
}