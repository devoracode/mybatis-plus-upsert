package io.github.devoracode.upsert.dialect;

import io.github.devoracode.upsert.core.FieldMeta;
import io.github.devoracode.upsert.core.UpsertMeta;

import java.util.List;

/**
 * Oracle Upsert 方言。
 *
 * <p>使用 Oracle MERGE INTO 实现 Upsert：
 *
 * <pre>
 * MERGE INTO target t
 * USING (
 *     SELECT ... FROM dual
 * ) src
 * ON (...)
 * WHEN MATCHED THEN
 *     UPDATE SET ...
 * WHEN NOT MATCHED THEN
 *     INSERT (...)
 *     VALUES (...);
 * </pre>
 *
 * <p>批量 Upsert 使用一个 MERGE + UNION ALL：
 *
 * <pre>
 * MERGE INTO target t
 * USING (
 *     SELECT ... FROM dual
 *     UNION ALL
 *     SELECT ... FROM dual
 *     ...
 * ) src
 * ON (...)
 * WHEN MATCHED THEN UPDATE ...
 * WHEN NOT MATCHED THEN INSERT ...
 * </pre>
 *
 * <p>单条场景生成带 {@code <if>}/{@code <trim>} 标签的动态 SQL；批量场景为固定列集合，
 * 全部行拼进同一个源子查询后以单条 MERGE 一次往返执行——无 PL/SQL 匿名块，
 * {@code executeUpdate} 返回的受影响行数为 insert+update 合计，语义明确。
 * 注意同一批次内不能包含重复的冲突键：源子查询中多行命中同一目标行时
 * Oracle 报 ORA-30926（unable to get a stable set of rows）。
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
        List<String> insCols = meta.getInsertColumns();
        List<String> insFields = meta.getInsertFields();

        StringBuilder sb = new StringBuilder(256 + insCols.size() * 30
                + meta.getUpdateColumns().size() * 20);
        sb.append("MERGE INTO ").append(meta.getTableName()).append(" t USING (");
        // 每个实体渲染为 SELECT ... FROM dual，行间以 UNION ALL 拼成单个源子查询。
        // 单条 MERGE 一次往返执行：不依赖 PL/SQL 匿名块（Oracle JDBC 不支持分号
        // 分隔的多语句，会报 ORA-00911），executeUpdate 行数语义也明确。
        sb.append("<foreach collection=\"list\" item=\"item\" separator=\" UNION ALL \">SELECT ");
        for (int i = 0; i < insCols.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("#{item.").append(insFields.get(i))
                    .append("} AS ").append(insCols.get(i));
        }
        sb.append(" FROM dual</foreach>");
        sb.append(") src ON (");
        DynamicSqlBuilder.appendOnConditions(sb, meta.getConflictColumns());
        DynamicSqlBuilder.appendMergeUpdateAndInsert(sb, meta);
        return sb.toString();
    }
}
