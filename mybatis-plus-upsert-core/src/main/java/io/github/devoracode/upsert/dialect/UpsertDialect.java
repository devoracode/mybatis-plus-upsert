package io.github.devoracode.upsert.dialect;

import io.github.devoracode.upsert.core.UpsertMeta;

/**
 * Interface for building upsert SQL statements for a specific database dialect.
 * Implementations are stateless and thread-safe.
 *
 * <p>SQL is generated once at startup (single datasource) or on first use per
 * dialect (dynamic datasource) and baked into the MyBatis {@code SqlSource},
 * which is cached in the {@code MappedStatement}. This mirrors MyBatis-Plus'
 * native approach where the {@code SqlSource} itself serves as the cache,
 * eliminating the need for a separate SQL string cache.
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
}