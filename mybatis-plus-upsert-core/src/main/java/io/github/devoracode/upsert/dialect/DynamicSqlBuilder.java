package io.github.devoracode.upsert.dialect;

import io.github.devoracode.upsert.core.FieldMeta;
import io.github.devoracode.upsert.core.UpsertMeta;

import java.util.List;

/**
 * Builds dynamic SQL fragments for upsert, reusable across all dialects.
 *
 * <p>Core problem: when some fields require dynamic value checks, the column list and the
 * value list must stay in sync — a field skipped in one list must be skipped in the
 * corresponding position of the other. This is solved the same way as MyBatis-Plus itself:
 * wrap the list with {@code <trim suffixOverrides=",">}, wrap each dynamic field with
 * {@code <if>}, and let {@code <trim>} strip the trailing comma.
 *
 * <p>This class is package-private and stateless. All methods are thread-safe.
 *
 * @author devoracode
 * @since 1.0.0
 */
final class DynamicSqlBuilder {

    private DynamicSqlBuilder() {
    }

    static String insertColumnsTrim(List<FieldMeta> insertFieldMetas) {
        StringBuilder sb = new StringBuilder(insertFieldMetas.size() * 24 + 32);
        sb.append("(<trim suffixOverrides=\",\">");
        for (FieldMeta fm : insertFieldMetas) {
            if (fm.isDynamic()) {
                sb.append("<if test=\"").append(ifTestExpr("et", fm)).append("\">")
                        .append(fm.getColumn()).append(", </if>");
            } else {
                sb.append(fm.getColumn()).append(", ");
            }
        }
        sb.append("</trim>)");
        return sb.toString();
    }

    static String insertValuesTrim(List<FieldMeta> insertFieldMetas, String paramPrefix) {
        StringBuilder sb = new StringBuilder(insertFieldMetas.size() * 24 + 32);
        sb.append("(<trim suffixOverrides=\",\">");
        for (FieldMeta fm : insertFieldMetas) {
            String valueExpr = "#{" + paramPrefix + "." + fm.getProperty() + "}";
            if (fm.isDynamic()) {
                sb.append("<if test=\"").append(ifTestExpr(paramPrefix, fm)).append("\">")
                        .append(valueExpr).append(", </if>");
            } else {
                sb.append(valueExpr).append(", ");
            }
        }
        sb.append("</trim>)");
        return sb.toString();
    }

    /**
     * Builds the {@code SET col = <value>, ...} fragment of an UPDATE clause, wrapped in a
     * {@code <trim suffixOverrides=",">} that also skips dynamic fields via {@code <if>}.
     *
     * <p>The assigned value is {@code valuePrefix + column + valueSuffix}, which lets each
     * dialect reference the inserted/conflicting row instead of the MyBatis parameter:
     * {@code EXCLUDED.col} (PostgreSQL), {@code new.col} (MySQL alias),
     * {@code VALUES(col)} (legacy MySQL), or {@code src.col} (Oracle / SQL Server MERGE).
     *
     * <p>Using the row reference (instead of {@code #{prefix.col}}) keeps the single-row and
     * batch forms consistent: the MERGE UPDATE SET now reads the same {@code src} alias that the
     * INSERT VALUES clause already references.
     *
     * @param valuePrefix prefix before the column name in the assigned value
     * @param valueSuffix suffix after the column name in the assigned value
     */
    static String updateSetTrim(List<FieldMeta> updateFieldMetas, String paramPrefix,
                                String valuePrefix, String valueSuffix) {
        StringBuilder sb = new StringBuilder(updateFieldMetas.size() * 32 + 32);
        sb.append("<trim suffixOverrides=\",\">");
        for (FieldMeta fm : updateFieldMetas) {
            String assignment = fm.getColumn() + " = " + valuePrefix + fm.getColumn() + valueSuffix + ", ";
            if (fm.isDynamic()) {
                sb.append("<if test=\"").append(ifTestExpr(paramPrefix, fm)).append("\">")
                        .append(assignment).append("</if>");
            } else {
                sb.append(assignment);
            }
        }
        sb.append("</trim>");
        return sb.toString();
    }

    static String ifTestExpr(String paramPrefix, FieldMeta fm) {
        String ref = paramPrefix + "." + fm.getProperty();
        if (fm.isCheckEmpty()) {
            return ref + " != null and " + ref + " != ''";
        }
        return ref + " != null";
    }

    static void appendJoin(StringBuilder sb, List<String> items) {
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(items.get(i));
        }
    }

    /**
     * Appends {@code INSERT INTO {table} ({columns}) VALUES <foreach...>(values)</foreach>}
     * to the StringBuilder. Used by MySQL (both syntaxes) and PostgreSQL batch SQL.
     */
    static void appendBatchInsertClause(StringBuilder sb, UpsertMeta meta) {
        List<String> insCols = meta.getInsertColumns();
        List<String> insFields = meta.getInsertFields();
        sb.append("INSERT INTO ").append(meta.getTableName()).append(" (");
        appendJoin(sb, insCols);
        sb.append(") VALUES ");
        sb.append("<foreach collection=\"list\" item=\"item\" separator=\",\">(");
        for (int i = 0; i < insFields.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("#{item.").append(insFields.get(i)).append("}");
        }
        sb.append(")</foreach>");
    }

    /**
     * Appends {@code ) ON (t.col = src.col AND ...)} for MERGE statements.
     * Used by Oracle and SQL Server batch SQL.
     */
    static void appendMergeOnClause(StringBuilder sb, List<String> confCols) {
        sb.append(") ON (");
        for (int i = 0; i < confCols.size(); i++) {
            if (i > 0) sb.append(" AND ");
            String col = confCols.get(i);
            sb.append("t.").append(col).append(" = src.").append(col);
        }
    }

    /**
     * Appends {@code ) WHEN MATCHED THEN UPDATE SET t.col = src.col, ...
     * WHEN NOT MATCHED THEN INSERT (cols) VALUES (src.cols)} for MERGE statements.
     * Used by Oracle and SQL Server batch SQL.
     */
    static void appendMergeUpdateAndInsert(StringBuilder sb, UpsertMeta meta) {
        List<String> insCols = meta.getInsertColumns();
        List<String> updCols = meta.getUpdateColumns();
        sb.append(") WHEN MATCHED THEN UPDATE SET ");
        for (int i = 0; i < updCols.size(); i++) {
            if (i > 0) sb.append(", ");
            String col = updCols.get(i);
            sb.append("t.").append(col).append(" = src.").append(col);
        }
        sb.append(" WHEN NOT MATCHED THEN INSERT (");
        appendJoin(sb, insCols);
        sb.append(") VALUES (");
        for (int i = 0; i < insCols.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("src.").append(insCols.get(i));
        }
        sb.append(")");
    }
}
