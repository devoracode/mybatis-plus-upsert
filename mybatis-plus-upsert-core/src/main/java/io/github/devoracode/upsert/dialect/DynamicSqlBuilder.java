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
     * @param valuePrefix 赋值表达式中列名前的固定前缀
     * @param valueSuffix 赋值表达式中列名后的固定后缀
     */
    static String updateSetTrim(List<FieldMeta> updateFieldMetas, String paramPrefix,
                                String valuePrefix, String valueSuffix) {
        StringBuilder sb = new StringBuilder(updateFieldMetas.size() * 32 + 32);
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
        sb.append("</trim>");
        return sb.toString();
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
