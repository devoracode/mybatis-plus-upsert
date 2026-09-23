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
     * @param valuePrefix     赋值表达式中列名前的固定前缀
     * @param valueSuffix     赋值表达式中列名后的固定后缀
     * @param targetRefPrefix 空 SET 兜底自赋值中引用目标行的列前缀
     */
    static String updateSetTrim(List<FieldMeta> updateFieldMetas,
                                String valuePrefix, String valueSuffix, String targetRefPrefix) {
        StringBuilder sb = new StringBuilder(updateFieldMetas.size() * 32 + 64);
        sb.append("<trim suffixOverrides=\",\">");
        appendIfWrapped(sb, updateFieldMetas, fm -> {
            String value = fm.isParamRef()
                    ? "#{" + fm.getProperty() + "}"
                    : valuePrefix + fm.getColumn() + valueSuffix;
            return fm.getColumn() + " = " + value + ", ";
        });
        appendEmptySetFallback(sb, updateFieldMetas, targetRefPrefix);
        sb.append("</trim>");
        return sb.toString();
    }

    /**
     * 全部更新列都被跳过时向 SET 追加一条兜底自赋值，避免 SET 渲染为空；不能无条件渲染。
     */
    private static void appendEmptySetFallback(StringBuilder sb, List<FieldMeta> updateFieldMetas,
                                               String targetRefPrefix) {
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
            String ref = fm.getProperty();
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
