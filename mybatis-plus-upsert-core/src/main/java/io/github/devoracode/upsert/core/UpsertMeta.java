package io.github.devoracode.upsert.core;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * Metadata container for upsert SQL generation.
 * Contains all information needed to build upsert statements: table name, columns,
 * conflict keys, update columns, and field-to-column mappings.
 *
 * <p>Instances are created by {@link UpsertMetaParser} and cached per entity class.
 *
 * @author devoracode
 * @since 1.0.0
 */
@Getter
@Builder
public class UpsertMeta {

    /**
     * The database table name.
     */
    private final String tableName;

    /**
     * Column names for the INSERT clause.
     */
    private final List<String> insertColumns;

    /**
     * Java field names for the INSERT clause (corresponds to insertColumns).
     */
    private final List<String> insertFields;

    /**
     * Column names that form the conflict key (used in ON CONFLICT / ON DUPLICATE KEY / MERGE ON).
     */
    private final List<String> conflictColumns;

    /**
     * Column names for the UPDATE clause (excludes conflict key columns).
     */
    private final List<String> updateColumns;

    /**
     * Java field names for the UPDATE clause (corresponds to updateColumns).
     */
    private final List<String> updateFields;

    /**
     * Field metadata for insert columns (includes dynamic/empty-check flags).
     */
    private final List<FieldMeta> insertFieldMetas;

    /**
     * Field metadata for update columns (includes dynamic/empty-check flags).
     */
    private final List<FieldMeta> updateFieldMetas;

    /**
     * Mapping from Java field name to database column name.
     */
    private final Map<String, String> fieldToColumnMap;

    /**
     * The entity class this metadata was parsed from.
     * Used as part of the SQL cache key so that entities mapped to the same
     * table name but with different structures (for example across data
     * sources) get their own cached upsert SQL instead of sharing a wrong one.
     */
    private final Class<?> entityClass;
}