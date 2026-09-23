package io.github.devoracode.upsert.dialect;

import io.github.devoracode.upsert.core.FieldMeta;
import io.github.devoracode.upsert.core.UpsertMeta;

import java.util.List;

/**
 * MERGE 系方言（Oracle / SQL Server）共用的语句骨架：
 * {@code MERGE INTO t ... USING (SELECT ... FROM dual) src ON (...) WHEN MATCHED THEN UPDATE /
 * WHEN NOT MATCHED THEN INSERT}。两方言仅目标别名写法、USING 子查询收尾与结尾分号不同。
 *
 * @author devoracode
 * @since 1.0.0
 */
final class MergeUpsertSqlBuilder {

    private final String targetAlias;
    private final String usingClose;
    private final String statementSuffix;

    private MergeUpsertSqlBuilder(String targetAlias, String usingClose, String statementSuffix) {
        this.targetAlias = targetAlias;
        this.usingClose = usingClose;
        this.statementSuffix = statementSuffix;
    }

    /** Oracle 版：目标别名为 {@code t}，USING 子查询带 {@code FROM dual}，结尾无分号。 */
    static MergeUpsertSqlBuilder oracle() {
        return new MergeUpsertSqlBuilder(" t", " FROM dual) src ON (", "");
    }

    /** SQL Server 版：目标别名为 {@code AS t}，USING 子查询收尾带 {@code AS src}，结尾带分号。 */
    static MergeUpsertSqlBuilder sqlServer() {
        return new MergeUpsertSqlBuilder(" AS t", ") AS src ON (", ";");
    }

    String buildUpsertSql(UpsertMeta meta) {
        List<FieldMeta> insertMetas = meta.getInsertFieldMetas();
        List<String> confCols = meta.getConflictColumns();

        StringBuilder sb = new StringBuilder(384 + insertMetas.size() * 32
                + meta.getUpdateFieldMetas().size() * 32);
        sb.append("MERGE INTO ").append(meta.getTableName()).append(targetAlias)
                .append(" USING (SELECT ");
        // 动态字段各自包在 <if> 里，末尾逗号由 <trim suffixOverrides> 去掉
        sb.append("<trim suffixOverrides=\",\">");
        DynamicSqlBuilder.appendIfWrapped(sb, insertMetas, "et",
                fm -> "#{et." + fm.getProperty() + "} AS " + fm.getColumn() + ", ");
        sb.append("</trim>");
        sb.append(usingClose);
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
        DynamicSqlBuilder.appendIfWrapped(sb, insertMetas, "et", fm -> fm.getColumn() + ", ");
        sb.append("</trim>");
        sb.append(" VALUES ");
        sb.append("<trim prefix=\"(\" suffix=\")\" suffixOverrides=\",\">");
        DynamicSqlBuilder.appendIfWrapped(sb, insertMetas, "et", fm -> "src." + fm.getColumn() + ", ");
        sb.append("</trim>");
        sb.append(statementSuffix);
        return sb.toString();
    }
}
