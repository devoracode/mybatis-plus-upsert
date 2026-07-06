package io.github.devoracode.upsert.core;

import java.util.List;
import java.util.Map;

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

    private UpsertMeta(Builder builder) {
        this.tableName = builder.tableName;
        this.insertColumns = builder.insertColumns;
        this.insertFields = builder.insertFields;
        this.conflictColumns = builder.conflictColumns;
        this.updateColumns = builder.updateColumns;
        this.updateFields = builder.updateFields;
        this.insertFieldMetas = builder.insertFieldMetas;
        this.updateFieldMetas = builder.updateFieldMetas;
        this.fieldToColumnMap = builder.fieldToColumnMap;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getTableName() {
        return tableName;
    }

    public List<String> getInsertColumns() {
        return insertColumns;
    }

    public List<String> getInsertFields() {
        return insertFields;
    }

    public List<String> getConflictColumns() {
        return conflictColumns;
    }

    public List<String> getUpdateColumns() {
        return updateColumns;
    }

    public List<String> getUpdateFields() {
        return updateFields;
    }

    public List<FieldMeta> getInsertFieldMetas() {
        return insertFieldMetas;
    }

    public List<FieldMeta> getUpdateFieldMetas() {
        return updateFieldMetas;
    }

    public Map<String, String> getFieldToColumnMap() {
        return fieldToColumnMap;
    }

    public static class Builder {
        private String tableName;
        private List<String> insertColumns;
        private List<String> insertFields;
        private List<String> conflictColumns;
        private List<String> updateColumns;
        private List<String> updateFields;
        private List<FieldMeta> insertFieldMetas;
        private List<FieldMeta> updateFieldMetas;
        private Map<String, String> fieldToColumnMap;

        public Builder tableName(String tableName) {
            this.tableName = tableName;
            return this;
        }

        public Builder insertColumns(List<String> insertColumns) {
            this.insertColumns = insertColumns;
            return this;
        }

        public Builder insertFields(List<String> insertFields) {
            this.insertFields = insertFields;
            return this;
        }

        public Builder conflictColumns(List<String> conflictColumns) {
            this.conflictColumns = conflictColumns;
            return this;
        }

        public Builder updateColumns(List<String> updateColumns) {
            this.updateColumns = updateColumns;
            return this;
        }

        public Builder updateFields(List<String> updateFields) {
            this.updateFields = updateFields;
            return this;
        }

        public Builder insertFieldMetas(List<FieldMeta> insertFieldMetas) {
            this.insertFieldMetas = insertFieldMetas;
            return this;
        }

        public Builder updateFieldMetas(List<FieldMeta> updateFieldMetas) {
            this.updateFieldMetas = updateFieldMetas;
            return this;
        }

        public Builder fieldToColumnMap(Map<String, String> fieldToColumnMap) {
            this.fieldToColumnMap = fieldToColumnMap;
            return this;
        }

        public UpsertMeta build() {
            return new UpsertMeta(this);
        }
    }
}
