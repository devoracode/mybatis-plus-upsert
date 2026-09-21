package io.github.devoracode.upsert.dialect;

import io.github.devoracode.upsert.core.UpsertMeta;

/**
 * MySQL 方言，使用 8.0.19+ 的行别名语法：{@code INSERT ... AS new ON DUPLICATE KEY UPDATE
 * col = new.col}。
 *
 * <p>MySQL 8.0.19 以下与 MariaDB 请用 {@link MysqlLegacyUpsertDialect}——MariaDB 不支持行别名。
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
}