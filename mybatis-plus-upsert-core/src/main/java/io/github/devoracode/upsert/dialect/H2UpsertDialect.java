package io.github.devoracode.upsert.dialect;

import io.github.devoracode.upsert.core.UpsertMeta;

import java.util.List;

/**
 * H2 database dialect using {@code MERGE INTO ... KEY(...) VALUES(...)}.
 *
 * <p>H2 uses a simplified MERGE syntax that does not require a WHEN clause.
 * The conflict is determined by the primary key or unique constraint specified in KEY().
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

    @Override
    public String buildUpsertBatchSql(UpsertMeta meta) {
        List<String> insCols   = meta.getInsertColumns();
        List<String> insFields = meta.getInsertFields();
        List<String> confCols  = meta.getConflictColumns();
        int insSize = insCols.size();

        StringBuilder single = new StringBuilder(64 + insSize * 20);
        single.append("MERGE INTO ").append(meta.getTableName()).append(" (");
        DynamicSqlBuilder.appendJoin(single, insCols);
        single.append(") KEY(");
        DynamicSqlBuilder.appendJoin(single, confCols);
        single.append(") VALUES (");
        for (int i = 0; i < insSize; i++) {
            if (i > 0) single.append(", ");
            single.append("#{item.").append(insFields.get(i)).append("}");
        }
        single.append(")");

        return "<foreach collection=\"list\" item=\"item\" separator=\";\">" + single + "</foreach>";
    }
}