package io.github.devoracode.upsert.core;

import lombok.Builder;
import lombok.Getter;

/**
 * Metadata for a single field/column in upsert operations.
 * Contains information needed for dynamic SQL generation: column name, property name,
 * and flags for conditional inclusion based on null/empty checks.
 *
 * @author devoracode
 * @since 1.0.0
 */
@Getter
@Builder
public class FieldMeta {

    /**
     * The database column name.
     */
    private final String column;

    /**
     * The Java field (property) name in the entity class.
     */
    private final String property;

    /**
     * Whether this field requires dynamic SQL handling (i.e., conditional inclusion
     * based on null/empty checks). When true, the field will be wrapped in
     * <code><if></code> tags in the generated MyBatis XML.
     */
    private final boolean dynamic;

    /**
     * Whether to check for empty strings in addition to null checks.
     * Only relevant when {@link #dynamic} is true and the field is of String type.
     */
    private final boolean checkEmpty;
}