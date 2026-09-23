package io.github.devoracode.upsert.dialect;

import io.github.devoracode.upsert.core.UpsertMeta;

/**
 * PostgreSQL 方言，使用 {@code INSERT ... ON CONFLICT (cols) DO UPDATE SET col = EXCLUDED.col}：
 * {@code EXCLUDED} 引用本应插入的那行，无需表别名。
 *
 * @author devoracode
 * @since 1.0.0
 */
public class PostgresUpsertDialect implements UpsertDialect {

    @Override
    public String buildUpsertSql(UpsertMeta meta) {
        StringBuilder sb = new StringBuilder(256 + meta.getInsertFieldMetas().size() * 24
                + meta.getUpdateFieldMetas().size() * 32);
        sb.append("INSERT INTO ").append(meta.getTableName()).append(' ');
        sb.append(DynamicSqlBuilder.insertColumnsTrim(meta.getInsertFieldMetas()));
        sb.append(" VALUES ");
        sb.append(DynamicSqlBuilder.insertValuesTrim(meta.getInsertFieldMetas()));
        sb.append(" ON CONFLICT (");
        DynamicSqlBuilder.appendJoin(sb, meta.getConflictColumns());
        sb.append(") DO UPDATE SET ");
        sb.append(DynamicSqlBuilder.updateSetTrim(meta.getUpdateFieldMetas(), "EXCLUDED.", "",
                targetQualifier(meta.getTableName())));
        return sb.toString();
    }

    /**
     * 兜底自赋值需以目标表名限定列引用才能读到目标行当前值；PostgreSQL 只接受表名不含 schema 前缀，故取最后一段。
     */
    private static String targetQualifier(String tableName) {
        int dot = tableName.lastIndexOf('.');
        return (dot >= 0 ? tableName.substring(dot + 1) : tableName) + ".";
    }
}