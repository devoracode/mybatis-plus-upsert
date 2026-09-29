package io.github.devoracode.upsert.dialect;

import io.github.devoracode.upsert.core.UpsertMeta;

/**
 * SQL Server 方言，使用 {@code MERGE INTO t [WITH (HOLDLOCK)] AS t USING (...) AS src ON (...)
 * WHEN MATCHED THEN UPDATE / WHEN NOT MATCHED THEN INSERT}，动态列的裁剪方式与
 * {@link OracleUpsertDialect} 相同，语句末尾带要求的分号。
 *
 * @author devoracode
 * @since 1.0.0
 */
public class SqlServerUpsertDialect implements UpsertDialect {

    private final boolean holdlock;

    /**
     * @param holdlock 目标表是否加 {@code WITH (HOLDLOCK)}；并发写同一冲突键的场景应保持 true
     * @since 1.7.1
     */
    public SqlServerUpsertDialect(boolean holdlock) {
        this.holdlock = holdlock;
    }

    @Override
    public String buildUpsertSql(UpsertMeta meta) {
        return MergeUpsertSqlBuilder.sqlServer(holdlock).buildUpsertSql(meta);
    }
}
