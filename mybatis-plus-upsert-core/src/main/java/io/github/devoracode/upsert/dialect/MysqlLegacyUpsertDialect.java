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
        String emptyUpdate = DynamicSqlBuilder.emptyUpdateCondition(meta.getUpdateFieldMetas());
        String nonEmptyUpdate = DynamicSqlBuilder.nonEmptyUpdateCondition(meta.getUpdateFieldMetas());
        StringBuilder sb = new StringBuilder(256 + meta.getInsertFieldMetas().size() * 24
                + meta.getUpdateFieldMetas().size() * 32);
        sb.append("INSERT ");
        if (emptyUpdate != null) {
            sb.append("<if test=\"").append(emptyUpdate).append("\">IGNORE </if>");
        }
        sb.append("INTO ").append(meta.getTableName()).append(' ');
        sb.append(DynamicSqlBuilder.insertColumnsTrim(meta.getInsertFieldMetas()));
        sb.append(" VALUES ");
        sb.append(DynamicSqlBuilder.insertValuesTrim(meta.getInsertFieldMetas()));
        if (nonEmptyUpdate == null) {
            sb.append(" ON DUPLICATE KEY UPDATE ");
            sb.append(DynamicSqlBuilder.updateSetTrim(meta.getUpdateFieldMetas(), "VALUES(", ")"));
        } else {
            sb.append("<if test=\"").append(nonEmptyUpdate).append("\"> ON DUPLICATE KEY UPDATE ");
            sb.append(DynamicSqlBuilder.updateSetTrim(meta.getUpdateFieldMetas(), "VALUES(", ")"));
            sb.append("</if>");
        }
        return sb.toString();
    }
}