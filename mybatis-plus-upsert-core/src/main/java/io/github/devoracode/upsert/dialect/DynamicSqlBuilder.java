package io.github.devoracode.upsert.dialect;

import io.github.devoracode.upsert.core.FieldMeta;

import java.util.List;

/**
 * 各方言共用的动态 SQL 片段构建器（包私有、无状态）。
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
     * 构建 UPDATE 的 {@code SET col = <value>, ...} 片段（{@code <trim>} 包裹，动态列用 {@code <if>} 跳过）。
     *
     * @param valuePrefix     赋值表达式中列名前的固定前缀
     * @param valueSuffix     赋值表达式中列名后的固定后缀
     * @param targetRefPrefix 空 SET 兜底自赋值中引用目标行的列前缀
     */
    static String updateSetTrim(List<FieldMeta> updateFieldMetas, String paramPrefix,
                                String valuePrefix, String valueSuffix, String targetRefPrefix) {
        StringBuilder sb = new StringBuilder(updateFieldMetas.size() * 32 + 64);
        sb.append("<trim suffixOverrides=\",\">");
        for (FieldMeta fm : updateFieldMetas) {
            String value = fm.isParamRef()
                    ? "#{" + paramPrefix + "." + fm.getProperty() + "}"
                    : valuePrefix + fm.getColumn() + valueSuffix;
            String assignment = fm.getColumn() + " = " + value + ", ";
            if (fm.isDynamic()) {
                sb.append("<if test=\"").append(ifTestExpr(paramPrefix, fm)).append("\">")
                        .append(assignment).append("</if>");
            } else {
                sb.append(assignment);
            }
        }
        appendEmptySetFallback(sb, updateFieldMetas, paramPrefix, targetRefPrefix);
        sb.append("</trim>");
        return sb.toString();
    }

    /**
     * 全部更新列都被跳过时向 SET 追加一条兜底自赋值，避免 SET 渲染为空；不能无条件渲染。
     */
    private static void appendEmptySetFallback(StringBuilder sb, List<FieldMeta> updateFieldMetas,
                                               String paramPrefix, String targetRefPrefix) {
        if (updateFieldMetas.isEmpty()) {
            return;
        }
        StringBuilder allOmitted = new StringBuilder();
        for (FieldMeta fm : updateFieldMetas) {
            if (!fm.isDynamic()) {
                return;
            }
            if (allOmitted.length() > 0) {
                allOmitted.append(" and ");
            }
            String ref = paramPrefix + "." + fm.getProperty();
            if (fm.isCheckEmpty()) {
                allOmitted.append('(').append(ref).append(" == null or ").append(ref).append(" == '')");
            } else {
                allOmitted.append(ref).append(" == null");
            }
        }
        String column = updateFieldMetas.get(0).getColumn();
        sb.append("<if test=\"").append(allOmitted).append("\">")
                .append(column).append(" = ").append(targetRefPrefix).append(column).append(", </if>");
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
