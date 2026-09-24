package io.github.devoracode.upsert.dialect;

import io.github.devoracode.upsert.core.UpsertMeta;

/**
 * MySQL 方言，使用 8.0.19+ 的行别名语法：{@code INSERT ... AS new ON DUPLICATE KEY UPDATE
 * col = new.col}。
 *
 * @author devoracode
 * @since 1.0.0
 */
public class MysqlUpsertDialect implements UpsertDialect {

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
            sb.append(" AS new ON DUPLICATE KEY UPDATE ");
            sb.append(DynamicSqlBuilder.updateSetTrim(meta.getUpdateFieldMetas(), "new.", ""));
        } else {
            sb.append("<if test=\"").append(nonEmptyUpdate).append("\"> AS new ON DUPLICATE KEY UPDATE ");
            sb.append(DynamicSqlBuilder.updateSetTrim(meta.getUpdateFieldMetas(), "new.", ""));
            sb.append("</if>");
        }
        return sb.toString();
    }
}