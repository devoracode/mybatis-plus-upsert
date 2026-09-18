package io.github.devoracode.upsert.dialect;

import io.github.devoracode.upsert.core.UpsertMeta;

/**
 * MySQL / MariaDB 方言，使用 MySQL 8.0.20+ 引入的新语法（AS 别名）。
 *
 * <p>该方言使用 {@code INSERT ... AS new ON DUPLICATE KEY UPDATE} 语法，
 * 更新值引用插入行的别名（如 {@code name = new.name}）。
 *
 * <p>MySQL 5.x 或 MariaDB 请使用 {@link MysqlLegacyUpsertDialect}，
 * 后者使用已废弃的 {@code VALUES()} 函数。
 *
 * @author devoracode
 * @since 1.0.0
 */
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
        // 兜底自赋值用非限定列名（targetRefPrefix 为空串）：未加 new. 前缀即引用目标行当前值
        sb.append(DynamicSqlBuilder.updateSetTrim(meta.getUpdateFieldMetas(), "et", "new.", "", ""));
        return sb.toString();
    }

    @Override
    public String buildUpsertBatchSql(UpsertMeta meta) {
        StringBuilder sb = new StringBuilder(128 + meta.getInsertColumns().size() * 20
                + meta.getUpdateFieldMetas().size() * 20);
        DynamicSqlBuilder.appendBatchInsertClause(sb, meta);
        sb.append(" AS new ON DUPLICATE KEY UPDATE ");
        DynamicSqlBuilder.appendBatchUpdateSet(sb, meta, "new.", "");
        return sb.toString();
    }
}