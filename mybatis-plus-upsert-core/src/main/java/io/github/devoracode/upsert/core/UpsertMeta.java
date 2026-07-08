package io.github.devoracode.upsert.core;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class UpsertMeta {

    private final String tableName;
    private final List<String> insertColumns;
    private final List<String> insertFields;
    private final List<String> conflictColumns;
    private final List<String> updateColumns;
    private final List<String> updateFields;
    private final List<FieldMeta> insertFieldMetas;
    private final List<FieldMeta> updateFieldMetas;
    private final Map<String, String> fieldToColumnMap;
}
