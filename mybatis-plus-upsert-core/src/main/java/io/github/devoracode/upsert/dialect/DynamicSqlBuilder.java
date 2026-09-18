package io.github.devoracode.upsert.dialect;

import io.github.devoracode.upsert.core.FieldMeta;
import io.github.devoracode.upsert.core.UpsertMeta;

import java.util.List;

/**
 * 为 Upsert 构建动态 SQL 片段，可被所有方言复用。
 *
 * <p>核心问题：当某些字段需要动态值检查时，列列表和值列表必须保持同步——
 * 在一个列表中跳过的字段，必须在另一个列表的对应位置也跳过。
 * 解决方案与 MyBatis-Plus 自身一致：用 {@code <trim suffixOverrides=",">} 包裹列表，
 * 用 {@code <if>} 包裹每个动态字段，再由 {@code <trim>} 去掉末尾逗号。
 *
 * <p>本类为包私有且无状态，所有方法均线程安全。
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
     * 构建 UPDATE 子句的 {@code SET col = <value>, ...} 片段，包裹在
     * {@code <trim suffixOverrides=",">} 中，并通过 {@code <if>} 跳过动态字段。
     *
     * <p>赋值默认为 {@code valuePrefix + column + valueSuffix} 行引用，使每种方言都能引用
     * 已插入/冲突的行而不是 MyBatis 参数：
     * {@code EXCLUDED.col}（PostgreSQL）、{@code new.col}（MySQL 别名）、
     * {@code VALUES(col)}（旧版 MySQL）、或 {@code src.col}（Oracle / SQL Server MERGE）。
     *
     * <p>使用行引用（而非 {@code #{prefix.col}}）能保证单行与批量形式一致：
     * MERGE 的 UPDATE SET 现在读取与 INSERT VALUES 子句相同的 {@code src} 别名。
     *
     * <p>例外：被排除在 INSERT 列之外的可更新字段（{@code insertStrategy = NEVER}，
     * {@code fm.isParamRef()} 为 true）使用 {@code #{paramPrefix.property}} 参数引用——
     * 行引用会指向插入行中不存在的列。
     *
     * <p>空 SET 兜底：当全部可更新字段均为动态字段时（典型场景：实体字段均为默认
     * {@code NOT_NULL} 策略且本次调用全部传 null），运行时所有 {@code <if>} 都不成立，
     * {@code <trim>} 渲染为空，将产生空 UPDATE SET 的非法 SQL（MySQL 的
     * {@code ON DUPLICATE KEY UPDATE} 后缀、PostgreSQL 的 {@code DO UPDATE SET}、
     * Oracle / SQL Server 的 {@code WHEN MATCHED THEN UPDATE SET} 均不允许为空）。
     * 此时在 {@code <trim>} 内追加一个由反向 {@code <if>} 条件（所有动态字段的不成立
     * 条件之并）包裹的自赋值兜底（{@code col = targetRefPrefix + col}，只引用目标行、
     * 与实体值无关）：仅当运行时全部动态字段被过滤时才渲染，保证 SET 子句始终语法完整。
     * 兜底不能无条件渲染——H2（MySQL 模式）会拒绝同一列既出现在正常赋值列表又出现在
     * 兜底中（Duplicate column name）。只要存在任意非动态更新字段（赋值恒定渲染），
     * SET 即不会为空，不追加兜底，生成的 SQL 与旧版完全一致。
     *
     * @param valuePrefix     赋值表达式中列名前的固定前缀
     * @param valueSuffix     赋值表达式中列名后的固定后缀
     * @param targetRefPrefix 自赋值兜底中引用目标行的列前缀：MySQL 两种语法传
     *                        {@code ""}（非限定列名即目标表当前值）；PostgreSQL 传
     *                        目标表名限定（如 {@code "t_user."}）；Oracle / SQL Server
     *                        传目标别名 {@code "t."}。兜底列为首个更新列，
     *                        必然不是冲突键列，不会触发 Oracle 的 ORA-38104
     *                        （MERGE 的 ON 条件列不允许出现在 UPDATE SET 中）
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
     * 全部更新字段均为动态字段时，追加运行时条件兜底：
     * 当且仅当所有字段的动态判空条件均不成立（即 SET 将渲染为空）时，
     * 输出一个针对首个更新列的自赋值。
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

    /**
     * 追加批量 Upsert 冲突处理子句的 SET 列表：{@code col = <行引用>, ...}。
     *
     * <p>用于 MySQL（新旧语法）和 PostgreSQL 的批量语句。行引用约定与单行
     * {@link #updateSetTrim} 一致：默认赋值为
     * {@code valuePrefix + column + valueSuffix}（{@code new.col}、
     * {@code VALUES(col)}、{@code EXCLUDED.col}）；被排除在 INSERT 列之外的
     * 可更新字段（{@code fm.isParamRef()}）回退为 {@code #{item.property}}
     * 参数引用——行引用会指向插入行中不存在的列。
     *
     * <p>批量场景为固定列集合（无 {@code <if>} 判空），字段不可跳过，
     * 因此直接以 ", " 连接，不包 {@code <trim>}。
     *
     * @param valuePrefix 行引用中列名前的固定前缀
     * @param valueSuffix 行引用中列名后的固定后缀
     */
    static void appendBatchUpdateSet(StringBuilder sb, UpsertMeta meta,
                                     String valuePrefix, String valueSuffix) {
        List<FieldMeta> updMetas = meta.getUpdateFieldMetas();
        for (int i = 0; i < updMetas.size(); i++) {
            if (i > 0) sb.append(", ");
            FieldMeta fm = updMetas.get(i);
            if (fm.isParamRef()) {
                sb.append(fm.getColumn()).append(" = #{item.").append(fm.getProperty()).append("}");
            } else {
                sb.append(fm.getColumn()).append(" = ")
                        .append(valuePrefix).append(fm.getColumn()).append(valueSuffix);
            }
        }
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
     * 向 StringBuilder 追加 {@code INSERT INTO {table} ({columns}) VALUES <foreach...>(values)</foreach>}。
     * 用于 MySQL（新旧两种语法）和 PostgreSQL 的批量 SQL。
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
     * 为 MERGE 语句追加 {@code ON (t.col = src.col AND ...)} 条件列表。
     * 不含前后定界符，供已自行补齐 {@code ON (} 前缀的调用方复用。
     */
    static void appendOnConditions(StringBuilder sb, List<String> confCols) {
        for (int i = 0; i < confCols.size(); i++) {
            if (i > 0) sb.append(" AND ");
            String col = confCols.get(i);
            sb.append("t.").append(col).append(" = src.").append(col);
        }
    }

    /**
     * 为 MERGE 语句追加 {@code ) ON (t.col = src.col AND ...)}。
     * SQL Server 批量 SQL使用——前导 {@code )} 用于关闭尚未闭合的 {@code src(cols} 列清单。
     */
    static void appendMergeOnClause(StringBuilder sb, List<String> confCols) {
        sb.append(") ON (");
        appendOnConditions(sb, confCols);
    }

    /**
     * 为 MERGE 语句追加 {@code ) WHEN MATCHED THEN UPDATE SET t.col = src.col, ...
     * WHEN NOT MATCHED THEN INSERT (cols) VALUES (src.cols)}。
     * Oracle 和 SQL Server 批量 SQL 使用。
     *
     * <p>只更新不插入的字段（{@code fm.isParamRef()}）赋值回退为
     * {@code #{item.property}} 参数引用——{@code src.col} 在
     * USING 子查询中不存在，行引用会生成非法 SQL。
     */
    static void appendMergeUpdateAndInsert(StringBuilder sb, UpsertMeta meta) {
        List<String> insCols = meta.getInsertColumns();
        List<FieldMeta> updMetas = meta.getUpdateFieldMetas();
        sb.append(") WHEN MATCHED THEN UPDATE SET ");
        for (int i = 0; i < updMetas.size(); i++) {
            if (i > 0) sb.append(", ");
            FieldMeta fm = updMetas.get(i);
            if (fm.isParamRef()) {
                sb.append("t.").append(fm.getColumn())
                        .append(" = #{item.").append(fm.getProperty()).append("}");
            } else {
                sb.append("t.").append(fm.getColumn()).append(" = src.").append(fm.getColumn());
            }
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
