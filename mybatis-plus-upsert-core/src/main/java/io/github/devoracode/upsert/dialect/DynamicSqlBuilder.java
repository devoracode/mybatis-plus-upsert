package io.github.devoracode.upsert.dialect;

import io.github.devoracode.upsert.core.FieldMeta;

import java.util.List;

/*
 * Builds dynamic SQL fragments for single-row upsert, reusable across all dialects.
 *
 * Core problem: when some fields require dynamic value checks, column and value lists must
 * stay in sync -- a field skipped in one list must be skipped in the corresponding position
 * of the other. Solved the same way as MyBatis-Plus itself: wrap with <trim suffixOverrides=",">,
 * wrap each dynamic field with <if>, and let trim strip the trailing comma.
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

    static String updateSetTrim(List<FieldMeta> updateFieldMetas, String paramPrefix) {
        StringBuilder sb = new StringBuilder(updateFieldMetas.size() * 32 + 32);
        sb.append("<trim suffixOverrides=\",\">");
        for (FieldMeta fm : updateFieldMetas) {
            String assignment = fm.getColumn() + " = #{" + paramPrefix + "." + fm.getProperty() + "}, ";
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
}
