package io.github.devoracode.upsert.dialect;

import io.github.devoracode.upsert.core.UpsertMeta;

import java.util.List;

public class MysqlUpsertDialect implements UpsertDialect {

    @Override
    public String buildUpsertSql(UpsertMeta meta) {
        StringBuilder sb = new StringBuilder(256 + meta.getInsertFieldMetas().size() * 24
                + meta.getUpdateFieldMetas().size() * 32);
        sb.append("INSERT INTO ").append(meta.getTableName()).append(' ');
        sb.append(DynamicSqlBuilder.insertColumnsTrim(meta.getInsertFieldMetas()));
        sb.append(" VALUES ");
        sb.append(DynamicSqlBuilder.insertValuesTrim(meta.getInsertFieldMetas(), "et"));
        sb.append(" AS new ON DUPLICATE KEY UPDATE ");
        sb.append(DynamicSqlBuilder.updateSetTrim(meta.getUpdateFieldMetas(), "et"));
        return sb.toString();
    }

    @Override
    public String buildUpsertBatchSql(UpsertMeta meta) {
        List<String> updCols   = meta.getUpdateColumns();

        StringBuilder sb = new StringBuilder(128 + meta.getInsertColumns().size() * 20 + updCols.size() * 20);
        DynamicSqlBuilder.appendBatchInsertClause(sb, meta);
        sb.append(" AS new ON DUPLICATE KEY UPDATE ");
        for (int i = 0; i < updCols.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(updCols.get(i)).append(" = new.").append(updCols.get(i));
        }
        return sb.toString();
    }
}
