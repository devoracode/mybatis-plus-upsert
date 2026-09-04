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
        sb.append(DynamicSqlBuilder.updateSetTrim(meta.getUpdateFieldMetas(), "et", "EXCLUDED.", ""));
        return sb.toString();
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