package io.github.devoracode.upsert.dialect;

import io.github.devoracode.upsert.core.FieldMeta;
import io.github.devoracode.upsert.core.UpsertMeta;

import java.util.List;

/**
 * Oracle 方言，使用 {@code MERGE INTO t USING (SELECT ... FROM dual) src ON (...)
 * WHEN MATCHED THEN UPDATE / WHEN NOT MATCHED THEN INSERT}。
 *
 * <p>生成的 SQL 带 {@code <if>}/{@code <trim>} 标签，列集合按字段策略在运行时裁剪。
 * {@code upsert(Collection)} 逐条执行这份语句（每行一个 MERGE），所以多条记录不会拼进同一个
 * 源子查询，也就不存在"重复冲突键命中同一目标行"的 ORA-30926（unable to get a stable set of rows）。
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
        // 动态字段各自包在 <if> 里，末尾逗号由 <trim suffixOverrides> 去掉
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
        // 引用 src.*，与 USING 子查询用同一组 <if> 条件保持同步；
        // 兜底自赋值引用目标别名 t——首个更新列必不在 ON 条件里，不触发 ORA-38104
        sb.append(DynamicSqlBuilder.updateSetTrim(meta.getUpdateFieldMetas(), "et", "src.", "", "t."));
        sb.append(" WHEN NOT MATCHED THEN INSERT ");
        // 列名与值都按同一组 <if> 条件裁剪，两者始终一一对应
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
}
