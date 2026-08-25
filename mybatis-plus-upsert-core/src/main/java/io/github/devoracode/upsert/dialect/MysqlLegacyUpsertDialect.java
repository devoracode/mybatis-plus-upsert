package io.github.devoracode.upsert.dialect;

import io.github.devoracode.upsert.core.UpsertMeta;

import java.util.List;

/**
 * MySQL / MariaDB 方言，使用旧版 {@code VALUES()} 引用语法。
 *
 * <p>适用于 MySQL 8.0.20 之前版本和 MariaDB。{@code VALUES(col)} 函数引用
 * 当前行本应插入的列值。
 *
 * <p>单行示例：
 * <pre>{@code
 * INSERT INTO t (id, name) VALUES (#{et.id}, #{et.name})
 * ON DUPLICATE KEY UPDATE name = #{et.name}
 * }</pre>
 *
 * <p>批量示例：
 * <pre>{@code
 * INSERT INTO t (id, name) VALUES (...),(...)
 * ON DUPLICATE KEY UPDATE name = VALUES(name)
 * }</pre>
 *
 * <p>注意：MySQL 8.0.20+ 中 {@code VALUES()} 已废弃。
 * MySQL 8.0.20+ 请使用 {@link MysqlUpsertDialect}。
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
        sb.append(DynamicSqlBuilder.insertValuesTrim(meta.getInsertFieldMetas(), "et"));
        sb.append(" ON DUPLICATE KEY UPDATE ");
        sb.append(DynamicSqlBuilder.updateSetTrim(meta.getUpdateFieldMetas(), "et", "VALUES(", ")"));
        return sb.toString();
    }

    @Override
    public String buildUpsertBatchSql(UpsertMeta meta) {
        List<String> updCols   = meta.getUpdateColumns();

        StringBuilder sb = new StringBuilder(128 + meta.getInsertColumns().size() * 20 + updCols.size() * 20);
        DynamicSqlBuilder.appendBatchInsertClause(sb, meta);
        sb.append(" ON DUPLICATE KEY UPDATE ");
        // VALUES(col) 引用当前批量行刚插入的行值
        for (int i = 0; i < updCols.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(updCols.get(i)).append(" = VALUES(").append(updCols.get(i)).append(")");
        }
        return sb.toString();
    }
}