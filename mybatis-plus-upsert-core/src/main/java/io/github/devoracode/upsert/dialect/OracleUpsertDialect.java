package io.github.devoracode.upsert.dialect;

import io.github.devoracode.upsert.core.FieldMeta;
import io.github.devoracode.upsert.core.UpsertMeta;

import java.util.List;

// Oracle dialect: MERGE INTO table t USING (SELECT ... FROM dual) src ON (...) WHEN MATCHED ... WHEN NOT MATCHED ...
public class OracleUpsertDialect implements UpsertDialect {

    @Override
    public String buildUpsertSql(UpsertMeta meta) {
        List<FieldMeta> insertMetas = meta.getInsertFieldMetas();
        List<String> confCols = meta.getConflictColumns();

        StringBuilder sb = new StringBuilder(384 + insertMetas.size() * 32
                + meta.getUpdateFieldMetas().size() * 32);
        sb.append("MERGE INTO ").append(meta.getTableName()).append(" t USING (SELECT ");
        // USING dual: each dynamic field is individually wrapped in <if>,
        // <trim suffixOverrides=","> handles the trailing comma.
        sb.append("<trim suffixOverrides=\",\">");
        for (FieldMeta fm : insertMetas) {
            String expr = "#{et." + fm.getProperty() + "} AS " + fm.getColumn() + ", ";
            if (fm.isDynamic()) {
                sb.append("<if test=\"").append(DynamicSqlBuilder.ifTestExpr("et", fm)).append("\">")
                        .append(expr).append("</if>");
            } else {
                sb.append(expr);
            }
        }
        sb.append("</trim>");
        sb.append(" FROM dual) src ON (");
        for (int i = 0; i < confCols.size(); i++) {
            if (i > 0) sb.append(" AND ");
            String col = confCols.get(i);
            sb.append("t.").append(col).append(" = src.").append(col);
        }
        sb.append(") WHEN MATCHED THEN UPDATE SET ");
        // UPDATE binds #{et.xxx} directly (src columns may be excluded by <if>)
        sb.append(DynamicSqlBuilder.updateSetTrim(meta.getUpdateFieldMetas(), "et"));
        sb.append(" WHEN NOT MATCHED THEN INSERT ");
        // INSERT column names and values both reference src; identical <if> conditions keep them in sync
        sb.append("<trim prefix=\"(\" suffix=\")\" suffixOverrides=\",\">");
        for (FieldMeta fm : insertMetas) {
            String expr = fm.getColumn() + ", ";
            if (fm.isDynamic()) {
                sb.append("<if test=\"").append(DynamicSqlBuilder.ifTestExpr("et", fm)).append("\">")
                        .append(expr).append("</if>");
            } else {
                sb.append(expr);
            }
        }
        sb.append("</trim>");
        sb.append(" VALUES ");
        sb.append("<trim prefix=\"(\" suffix=\")\" suffixOverrides=\",\">");
        for (FieldMeta fm : insertMetas) {
            String expr = "src." + fm.getColumn() + ", ";
            if (fm.isDynamic()) {
                sb.append("<if test=\"").append(DynamicSqlBuilder.ifTestExpr("et", fm)).append("\">")
                        .append(expr).append("</if>");
            } else {
                sb.append(expr);
            }
        }
        sb.append("</trim>");
        return sb.toString();
    }

    @Override
    public String buildUpsertBatchSql(UpsertMeta meta) {
        StringBuilder sb = new StringBuilder(256 + meta.getInsertColumns().size() * 30
                + meta.getUpdateColumns().size() * 20);
        sb.append("MERGE INTO ").append(meta.getTableName()).append(" t USING (SELECT ");
        for (int i = 0; i < meta.getInsertColumns().size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("#{item.").append(meta.getInsertFields().get(i))
                    .append("} AS ").append(meta.getInsertColumns().get(i));
        }
        sb.append(" FROM dual) src");
        DynamicSqlBuilder.appendMergeOnClause(sb, meta.getConflictColumns());
        DynamicSqlBuilder.appendMergeUpdateAndInsert(sb, meta);
        return "<foreach collection=\"list\" item=\"item\" separator=\";\">" + sb + "</foreach>";
    }
}
