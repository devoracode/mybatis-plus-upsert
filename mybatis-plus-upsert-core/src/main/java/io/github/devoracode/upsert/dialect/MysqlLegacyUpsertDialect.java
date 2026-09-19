package io.github.devoracode.upsert.dialect;

import io.github.devoracode.upsert.core.UpsertMeta;

/**
 * MySQL / MariaDB 方言，使用旧版 {@code VALUES()} 引用语法。
 *
 * <p>向下兼容所有支持 {@code ON DUPLICATE KEY UPDATE} 的 MySQL / MariaDB 版本。
 * {@code VALUES(col)} 函数引用当前行本应插入的列值。
 *
 * <p>单行示例：
 * <pre>{@code
 * INSERT INTO t (id, name) VALUES (#{et.id}, #{et.name})
 * ON DUPLICATE KEY UPDATE name = VALUES(name)
 * }</pre>
 *
 * <p>批量示例：
 * <pre>{@code
 * INSERT INTO t (id, name) VALUES (...),(...)
 * ON DUPLICATE KEY UPDATE name = VALUES(name)
 * }</pre>
 *
 * <p>注意：{@code VALUES()} 在 MySQL 8.0.20 起被官方废弃（当前仍可用）。
 * MySQL 8.0.19+ 建议改用 {@link MysqlUpsertDialect} 的新语法（AS 别名）。
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
        // 兜底自赋值用非限定列名（targetRefPrefix 为空串）：引用目标行当前值
        sb.append(DynamicSqlBuilder.updateSetTrim(meta.getUpdateFieldMetas(), "et", "VALUES(", ")", ""));
        return sb.toString();
    }

    @Override
    public String buildUpsertBatchSql(UpsertMeta meta) {
        StringBuilder sb = new StringBuilder(128 + meta.getInsertColumns().size() * 20
                + meta.getUpdateFieldMetas().size() * 20);
        DynamicSqlBuilder.appendBatchInsertClause(sb, meta);
        sb.append(" ON DUPLICATE KEY UPDATE ");
        // VALUES(col) 引用当前批量行刚插入的行值
        DynamicSqlBuilder.appendBatchUpdateSet(sb, meta, "VALUES(", ")");
        return sb.toString();
    }
}