package io.github.devoracode.upsert.dialect;

import io.github.devoracode.upsert.core.UpsertMeta;

/**
 * Oracle 方言，使用 {@code MERGE INTO t USING (SELECT ... FROM dual) src ON (...)
 * WHEN MATCHED THEN UPDATE / WHEN NOT MATCHED THEN INSERT}。
 *
 * @author devoracode
 * @since 1.0.0
 */
public class OracleUpsertDialect implements UpsertDialect {

    @Override
    public String buildUpsertSql(UpsertMeta meta) {
        return MergeUpsertSqlBuilder.oracle().buildUpsertSql(meta);
    }
}
