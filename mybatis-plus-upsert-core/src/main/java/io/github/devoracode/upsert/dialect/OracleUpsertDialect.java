package io.github.devoracode.upsert.dialect;

import io.github.devoracode.upsert.core.FieldMeta;
import io.github.devoracode.upsert.core.UpsertMeta;

import java.util.List;

/**
 * Oracle 方言，使用 {@code MERGE INTO ... USING (SELECT ...) src ON (...) WHEN MATCHED ... WHEN NOT MATCHED ...} 语法。
 *
 * <p>Oracle 不支持原生 upsert 语法，因此该方言生成带有 MyBatis XML 标签
 * （{@code <if>}、{@code <trim>}）的 MERGE 语句以处理动态字段。
 *
 * @author devoracode
 * @since 1.0.0
 */
public class OracleUpsertDialect implements UpsertDialect {

    @Override
    public String buildUpsertSql(UpsertMeta meta) {
        List<FieldMeta> insertMetas = meta.getInsertFieldMetas();
        List<String> confCols = meta.getConflictColumns();

        StringBuilder sb = new StringBuilder(384 + insertMetas.size() * 32
                + meta.getUpdateFieldMetas().size() * 32);
        sb.append("MERGE INTO ").append(meta.getTableName()).append(" t USING (SELECT ");
        // USING dual：每个动态字段单独包裹在 <if> 中，
        // <trim suffixOverrides=","> 负责处理末尾逗号。
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
        // UPDATE 引用 src.*——相同的 <if> 条件使其与 USING 中的查询保持一致
        sb.append(DynamicSqlBuilder.updateSetTrim(meta.getUpdateFieldMetas(), "et", "src.", ""));
        sb.append(" WHEN NOT MATCHED THEN INSERT ");
        // INSERT 的列名和值都引用 src；相同的 <if> 条件使二者保持同步
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
        return "<foreach collection=\"list\" item=\"item\" separator=\";\">>" + sb + "</foreach>";
    }
}