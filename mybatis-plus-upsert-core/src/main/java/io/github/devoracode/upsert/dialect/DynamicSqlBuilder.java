package io.github.devoracode.upsert.dialect;

import io.github.devoracode.upsert.core.FieldMeta;

import java.util.List;

/**
 * 各方言共用的动态 SQL 片段构建器（包私有、无状态）。
 *
 * <p>要解决的是列同步：某些列要按值增减时，列名列表与取值列表必须在对应位置同步跳过。
 * 做法与 MyBatis-Plus 一致——列表用 {@code <trim suffixOverrides=",">} 包裹，
 * 动态列各自用 {@code <if>} 包裹，末尾逗号由 {@code <trim>} 去掉。
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
     * 构建 UPDATE 的 {@code SET col = <value>, ...} 片段（{@code <trim>} 包裹，动态列用
     * {@code <if>} 跳过）。
     *
     * <p>赋值默认写成行引用 {@code valuePrefix + column + valueSuffix}，让 UPDATE 分支复用
     * "本条语句刚打算插入的那一行"，而不必把实体值再绑定一遍：
     * {@code EXCLUDED.col}（PostgreSQL）、{@code new.col}（MySQL 别名）、
     * {@code VALUES(col)}（旧版 MySQL）、{@code src.col}（Oracle / SQL Server MERGE）。
     * 例外是 {@link FieldMeta#isParamRef() paramRef} 字段（参与 UPDATE 却被排除在 INSERT 之外，
     * 如 {@code insertStrategy = NEVER}）——行引用指向插入行里不存在的列，只能用参数引用
     * {@code #{paramPrefix.property}}。
     *
     * <p>全部更新列都被 {@code <if>} 跳过时 SET 会渲染为空，故追加一个兜底自赋值，
     * 详见 {@code appendEmptySetFallback}。
     *
     * @param valuePrefix     赋值表达式中列名前的固定前缀
     * @param valueSuffix     赋值表达式中列名后的固定后缀
     * @param targetRefPrefix 兜底自赋值中引用目标行的列前缀：MySQL 两种语法传 {@code ""}
     *                        （非限定列名即目标表当前值），PostgreSQL 传目标表名限定（如
     *                        {@code "t_user."}），Oracle / SQL Server 传目标别名 {@code "t."}
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
     * 空 SET 兜底：四种数据库都不允许 UPDATE SET 为空，所以当全部更新列都是动态列（所有非冲突键
     * 字段均为默认 {@code NOT_NULL} 且本次全部传 null）时，SET 会渲染为空。于是在 {@code <trim>}
     * 内追加一条自赋值 {@code col = targetRefPrefix + col}，由反向条件（所有动态列"不成立"之并）
     * 包裹，仅在整个 SET 会被渲染为空时成立；只要存在任一非动态更新列就恒不为空，不追加。
     *
     * <p>兜底不能无条件渲染：H2（MySQL 模式）会拒绝同一列既出现在正常赋值里又出现在兜底中
     * （Duplicate column name）。兜底列取首个更新列，它必然不是冲突键，因此不会触发 Oracle 的
     * ORA-38104（MERGE 的 ON 条件列不允许出现在 UPDATE SET 中）。
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
