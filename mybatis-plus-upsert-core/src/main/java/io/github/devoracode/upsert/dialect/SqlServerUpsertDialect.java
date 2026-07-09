package io.github.devoracode.upsert.dialect;

import io.github.devoracode.upsert.core.FieldMeta;
import io.github.devoracode.upsert.core.UpsertMeta;

import java.util.List;

/**
 * SQL Server dialect using {@code MERGE INTO ... USING (...) AS src ON (...) WHEN MATCHED ... WHEN NOT MATCHED ...}.
 *
 * <p>SQL Server's MERGE statement must end with a semicolon. This dialect generates
 * MyBatis XML with conditional tags to handle dynamic fields.
 *
 * @author devoracode
 * @since 1.0.0
 */
public class SqlServerUpsertDialect implements UpsertDialect {

    @Override
    public String buildUpsertSql(UpsertMeta meta) {
        List<FieldMeta> insertMetas = meta.getInsertFieldMetas();
        List<String> confCols = meta.getConflictColumns();
        StringBuilder sb = new StringBuilder(384 + insertMetas.size() * 32
                + meta.getUpdateFieldMetas().size() * 32);
        sb.append("MERGE INTO ").append(meta.getTableName()).append(" AS t USING (SELECT ");
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
        sb.append(") AS src ON (");
        for (int i = 0; i < confCols.size(); i++) {
            if (i > 0) sb.append(" AND ");
            String col = confCols.get(i);
            sb.append("t.").append(col).append(" = src.").append(col);
        }
        sb.append(") WHEN MATCHED THEN UPDATE SET ");
        sb.append(DynamicSqlBuilder.updateSetTrim(meta.getUpdateFieldMetas(), "et"));
        sb.append(" WHEN NOT MATCHED THEN INSERT ");
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
        sb.append(";");
        return sb.toString();
    }

    @Override
    public String buildUpsertBatchSql(UpsertMeta meta) {
        StringBuilder sb = new StringBuilder(256 + meta.getInsertColumns().size() * 30
                + meta.getUpdateColumns().size() * 20);
        sb.append("MERGE INTO ").append(meta.getTableName()).append(" AS t USING (VALUES ");
        sb.append("<foreach collection=\"list\" item=\"item\" separator=\",\">(");
        for (int i = 0; i < meta.getInsertFields().size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("#{item.").append(meta.getInsertFields().get(i)).append("}");
        }
        sb.append(")</foreach>");
        sb.append(") AS src(");
        DynamicSqlBuilder.appendJoin(sb, meta.getInsertColumns());
        DynamicSqlBuilder.appendMergeOnClause(sb, meta.getConflictColumns());
        DynamicSqlBuilder.appendMergeUpdateAndInsert(sb, meta);
        sb.append(";");
        return sb.toString();
    }
}