package io.github.devoracode.upsert.core;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/*
 * Immutable upsert metadata parsed from an entity class at startup.
 */
@Getter
@Builder
public class UpsertMeta {

    private String tableName;

    private List<String> insertColumns;

    private List<String> insertFields;

    private List<String> conflictColumns;

    private List<String> updateColumns;

    private List<String> updateFields;

    private List<FieldMeta> insertFieldMetas;

    private List<FieldMeta> updateFieldMetas;

    private Map<String, String> fieldToColumnMap;
}
