package io.github.devoracode.upsert.dialect;

import io.github.devoracode.upsert.core.UpsertMeta;

/**
 * PostgreSQL 方言，使用 {@code INSERT ... ON CONFLICT (cols) DO UPDATE SET col = EXCLUDED.col} 语法。
 *
 * <p>{@code EXCLUDED} 关键字引用本应插入的行，
 * 无需表别名即可实现干净的冲突解决。
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
        sb.append(DynamicSqlBuilder.insertValuesTrim(meta.getInsertFieldMetas(), "et"));
        sb.append(" ON CONFLICT (");
        DynamicSqlBuilder.appendJoin(sb, meta.getConflictColumns());
        sb.append(") DO UPDATE SET ");
        sb.append(DynamicSqlBuilder.updateSetTrim(meta.getUpdateFieldMetas(), "et", "EXCLUDED.", "",
                targetQualifier(meta.getTableName())));
        return sb.toString();
    }

    /**
     * 兜底自赋值需以目标表名限定列引用（{@code col = t_user.col}）才能读到目标行当前值。
     * PostgreSQL 的 SET 子句中限定符只接受表名（别名），不含 schema 前缀，故取最后一段。
     */
    private static String targetQualifier(String tableName) {
        int dot = tableName.lastIndexOf('.');
        return (dot >= 0 ? tableName.substring(dot + 1) : tableName) + ".";
    }

    @Override
    public String buildUpsertBatchSql(UpsertMeta meta) {
        StringBuilder sb = new StringBuilder(128 + meta.getInsertColumns().size() * 20
                + meta.getUpdateFieldMetas().size() * 20);
        DynamicSqlBuilder.appendBatchInsertClause(sb, meta);
        sb.append(" ON CONFLICT (");
        DynamicSqlBuilder.appendJoin(sb, meta.getConflictColumns());
        sb.append(") DO UPDATE SET ");
        DynamicSqlBuilder.appendBatchUpdateSet(sb, meta, "EXCLUDED.", "");
        return sb.toString();
    }
}