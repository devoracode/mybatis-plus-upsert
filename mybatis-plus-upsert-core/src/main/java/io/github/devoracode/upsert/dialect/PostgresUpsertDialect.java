package io.github.devoracode.upsert.dialect;

import io.github.devoracode.upsert.core.UpsertMeta;

import java.util.List;

// PostgreSQL dialect: INSERT ... ON CONFLICT (cols) DO UPDATE SET col = EXCLUDED.col
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
        sb.append(DynamicSqlBuilder.updateSetTrim(meta.getUpdateFieldMetas(), "et"));
        return sb.toString();
    }

    @Override
    public String buildUpsertBatchSql(UpsertMeta meta) {
        List<String> updCols   = meta.getUpdateColumns();

        StringBuilder sb = new StringBuilder(128 + meta.getInsertColumns().size() * 20 + updCols.size() * 20);
        DynamicSqlBuilder.appendBatchInsertClause(sb, meta);
        sb.append(" ON CONFLICT (");
        DynamicSqlBuilder.appendJoin(sb, meta.getConflictColumns());
        sb.append(") DO UPDATE SET ");
        for (int i = 0; i < updCols.size(); i++) {
            if (i > 0) sb.append(", ");
            String col = updCols.get(i);
            sb.append(col).append(" = EXCLUDED.").append(col);
        }
        return sb.toString();
    }
}
