package io.github.devoracode.upsert.dialect;

import io.github.devoracode.upsert.core.UpsertMeta;

public interface UpsertDialect {

    String buildUpsertSql(UpsertMeta meta);

    String buildUpsertBatchSql(UpsertMeta meta);

    default String getCachedUpsertSql(UpsertMeta meta) {
        String key = getClass().getSimpleName() + ":" + meta.getTableName() + ":single";
        return UpsertSqlCache.get(key, k -> buildUpsertSql(meta));
    }

    default String getCachedUpsertBatchSql(UpsertMeta meta) {
        String key = getClass().getSimpleName() + ":" + meta.getTableName() + ":batch";
        return UpsertSqlCache.get(key, k -> buildUpsertBatchSql(meta));
    }
}
