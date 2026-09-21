package io.github.devoracode.upsert.dialect;

import io.github.devoracode.upsert.core.UpsertMeta;

import java.util.List;

/**
 * H2 方言，使用简化的 {@code MERGE INTO ... KEY(cols) VALUES(...)} 语法：没有 WHEN 子句，
 * 冲突由 {@code KEY()} 里给出的主键或唯一约束决定。
 *
 * @author devoracode
 * @since 1.0.0
 */
public class H2UpsertDialect implements UpsertDialect {

    @Override
    public String buildUpsertSql(UpsertMeta meta) {
        StringBuilder sb = new StringBuilder(128 + meta.getInsertFieldMetas().size() * 24);
        sb.append("MERGE INTO ").append(meta.getTableName()).append(' ');
        sb.append(DynamicSqlBuilder.insertColumnsTrim(meta.getInsertFieldMetas()));
        sb.append(" KEY(");
        DynamicSqlBuilder.appendJoin(sb, meta.getConflictColumns());
        sb.append(") VALUES ");
        sb.append(DynamicSqlBuilder.insertValuesTrim(meta.getInsertFieldMetas(), "et"));
        return sb.toString();
    }
}