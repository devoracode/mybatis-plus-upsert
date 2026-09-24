package io.github.devoracode.upsert.dialect;

import io.github.devoracode.upsert.core.FieldMeta;

import java.util.List;
import java.util.function.Function;

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
        appendIfWrapped(sb, insertFieldMetas, fm -> fm.getColumn() + ", ");
        sb.append("</trim>)");
        return sb.toString();
    }

    static String insertValuesTrim(List<FieldMeta> insertFieldMetas) {
        StringBuilder sb = new StringBuilder(insertFieldMetas.size() * 24 + 32);
        sb.append("(<trim suffixOverrides=\",\">");
        appendIfWrapped(sb, insertFieldMetas,
                fm -> "#{" + fm.getProperty() + "}, ");
        sb.append("</trim>)");
        return sb.toString();
    }

    /**
     * 构建 UPDATE 的 {@code SET col = <value>, ...} 片段（{@code <trim>} 包裹，动态列用 {@code <if>} 跳过）。
     *
     * @param valuePrefix 赋值表达式中列名前的固定前缀
     * @param valueSuffix 赋值表达式中列名后的固定后缀
     */
    static String updateSetTrim(List<FieldMeta> updateFieldMetas,
                                String valuePrefix, String valueSuffix) {
        StringBuilder sb = new StringBuilder(updateFieldMetas.size() * 32 + 32);
        sb.append("<trim suffixOverrides=\",\">");
        appendIfWrapped(sb, updateFieldMetas, fm -> {
            String value = fm.isParamRef()
                    ? "#{" + fm.getProperty() + "}"
                    : valuePrefix + fm.getColumn() + valueSuffix;
            return fm.getColumn() + " = " + value + ", ";
        });
        sb.append("</trim>");
        return sb.toString();
    }

    /**
     * 返回全部动态更新字段都被过滤时的条件；存在静态更新字段时返回 {@code null}。
     */
    static String emptyUpdateCondition(List<FieldMeta> updateFieldMetas) {
        return updateCondition(updateFieldMetas, false);
    }

    /**
     * 返回至少一个动态更新字段有值时的条件；存在静态更新字段时返回 {@code null}。
     */
    static String nonEmptyUpdateCondition(List<FieldMeta> updateFieldMetas) {
        return updateCondition(updateFieldMetas, true);
    }

    private static String updateCondition(List<FieldMeta> updateFieldMetas, boolean present) {
        if (updateFieldMetas.isEmpty()) {
            return present ? "false" : "true";
        }
        StringBuilder condition = new StringBuilder();
        for (FieldMeta fm : updateFieldMetas) {
            if (!fm.isDynamic()) {
                return null;
            }
            if (condition.length() > 0) {
                condition.append(present ? " or " : " and ");
            }
            String ref = fm.getProperty();
            if (present) {
                if (fm.isCheckEmpty()) {
                    condition.append('(').append(ref).append(" != null and ").append(ref).append(" != '')");
                } else {
                    condition.append(ref).append(" != null");
                }
            } else if (fm.isCheckEmpty()) {
                condition.append('(').append(ref).append(" == null or ").append(ref).append(" == '')");
            } else {
                condition.append(ref).append(" == null");
            }
        }
        return condition.toString();
    }

    static String ifTestExpr(FieldMeta fm) {
        String ref = fm.getProperty();
        if (fm.isCheckEmpty()) {
            return ref + " != null and " + ref + " != ''";
        }
        return ref + " != null";
    }

    /**
     * 逐个渲染 {@code expr(fm)} 生成的 SQL 片段：非动态直接拼接，动态字段包在 {@code <if test="...">} 里。
     * 片段自带尾逗号时由外层 {@code <trim suffixOverrides>} 去掉。
     */
    static void appendIfWrapped(StringBuilder sb, List<FieldMeta> fieldMetas,
                                Function<FieldMeta, String> expr) {
        for (FieldMeta fm : fieldMetas) {
            String piece = expr.apply(fm);
            if (fm.isDynamic()) {
                sb.append("<if test=\"").append(ifTestExpr(fm)).append("\">")
                        .append(piece).append("</if>");
            } else {
                sb.append(piece);
            }
        }
    }

    static void appendJoin(StringBuilder sb, List<String> items) {
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(items.get(i));
        }
    }
}
