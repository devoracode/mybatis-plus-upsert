package io.github.devoracode.upsert.dialect;

import io.github.devoracode.upsert.core.UpsertMeta;

import java.util.List;

/*
 * MySQL / MariaDB dialect using the VALUES() reference syntax.
 *
 * Used for MySQL < 8.0.20 and MariaDB. The VALUES() function references the value
 * of the column that would have been inserted for the current row.
 *
 * Example single-row:
 *   INSERT INTO t (id, name) VALUES (#{et.id}, #{et.name})
 *   ON DUPLICATE KEY UPDATE name = #{et.name}
 *
 * Example batch:
 *   INSERT INTO t (id, name) VALUES (...),(...)
 *   ON DUPLICATE KEY UPDATE name = VALUES(name)
 *
 * Note: VALUES() is deprecated in MySQL 8.0.20+. Use {@link MysqlUpsertDialect} for 8.0.20+.
 */
public class MysqlLegacyUpsertDialect implements UpsertDialect {

    @Override
    public String buildUpsertSql(UpsertMeta meta) {
        StringBuilder sb = new StringBuilder(256 + meta.getInsertFieldMetas().size() * 24
                + meta.getUpdateFieldMetas().size() * 32);
        sb.append("INSERT INTO ").append(meta.getTableName()).append(' ');
        sb.append(DynamicSqlBuilder.insertColumnsTrim(meta.getInsertFieldMetas()));
        sb.append(" VALUES ");
        sb.append(DynamicSqlBuilder.insertValuesTrim(meta.getInsertFieldMetas(), "et"));
        sb.append(" ON DUPLICATE KEY UPDATE ");
        sb.append(DynamicSqlBuilder.updateSetTrim(meta.getUpdateFieldMetas(), "et"));
        return sb.toString();
    }

    @Override
    public String buildUpsertBatchSql(UpsertMeta meta) {
        List<String> updCols   = meta.getUpdateColumns();

        StringBuilder sb = new StringBuilder(128 + meta.getInsertColumns().size() * 20 + updCols.size() * 20);
        DynamicSqlBuilder.appendBatchInsertClause(sb, meta);
        sb.append(" ON DUPLICATE KEY UPDATE ");
        // VALUES(col) references the just-inserted row value for the current batch row.
        for (int i = 0; i < updCols.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(updCols.get(i)).append(" = VALUES(").append(updCols.get(i)).append(")");
        }
        return sb.toString();
    }
}
