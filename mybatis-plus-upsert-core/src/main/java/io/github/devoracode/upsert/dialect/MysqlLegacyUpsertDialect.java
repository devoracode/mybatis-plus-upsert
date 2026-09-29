package io.github.devoracode.upsert.dialect;

import io.github.devoracode.upsert.core.UpsertMeta;

/**
 * MySQL / MariaDB 方言，使用旧版 {@code ON DUPLICATE KEY UPDATE col = VALUES(col)} 语法，
 * 兼容所有支持该子句的 MySQL / MariaDB 版本。
 *
 * @author devoracode
 * @since 1.0.0
 */
public class MysqlLegacyUpsertDialect implements UpsertDialect {

    @Override
    public String buildUpsertSql(UpsertMeta meta) {
        StringBuilder sb = new StringBuilder(256 + meta.getInsertFieldMetas().size() * 24
                + meta.getUpdateFieldMetas().size() * 32);
        sb.append("INSERT INTO ").append(meta.getTableName()).append(' ');
        sb.append(DynamicSqlBuilder.insertColumnsTrim(meta.getInsertFieldMetas()));
        sb.append(" VALUES ");
        sb.append(DynamicSqlBuilder.insertValuesTrim(meta.getInsertFieldMetas()));
        sb.append(" ON DUPLICATE KEY UPDATE ");
        // 兜底自赋值用非限定列名（targetRefPrefix 为空串）：引用目标行当前值
        sb.append(DynamicSqlBuilder.updateSetTrim(meta.getUpdateFieldMetas(), "VALUES(", ")", ""));
        return sb.toString();
    }
}