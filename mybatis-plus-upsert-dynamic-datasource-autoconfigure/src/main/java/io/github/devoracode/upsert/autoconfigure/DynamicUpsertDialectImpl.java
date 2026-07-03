package io.github.devoracode.upsert.autoconfigure;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import io.github.devoracode.upsert.dialect.DynamicUpsertDialect;
import io.github.devoracode.upsert.dialect.UpsertDialect;
import io.github.devoracode.upsert.core.UpsertMeta;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of DynamicUpsertDialect that routes to the appropriate dialect
 * based on the current data source from DynamicDataSourceContextHolder.
 */
@Slf4j
@Getter
@Setter
public class DynamicUpsertDialectImpl implements DynamicUpsertDialect {

    private final Map<String, UpsertDialect> dialectMap = new HashMap<>();
    private UpsertDialect primaryDialect;

    public void addDialect(String dataSourceName, UpsertDialect dialect) {
        dialectMap.put(dataSourceName, dialect);
    }

    @Override
    public UpsertDialect getCurrentDialect() {
        // Get current data source name from dynamic-datasource context holder
        String dataSourceName = DynamicDataSourceContextHolder.peek();

        if (dataSourceName != null) {
            UpsertDialect dialect = dialectMap.get(dataSourceName);
            if (dialect != null) {
                log.debug("Using dialect {} for data source {}", dialect.getClass().getSimpleName(), dataSourceName);
                return dialect;
            }
            log.warn("No dialect configured for data source '{}', falling back to primary dialect", dataSourceName);
        }

        return primaryDialect;
    }

    @Override
    public String buildUpsertSql(UpsertMeta meta) {
        UpsertDialect dialect = getCurrentDialect();
        return dialect != null ? dialect.buildUpsertSql(meta) : "";
    }

    @Override
    public String buildUpsertBatchSql(UpsertMeta meta) {
        UpsertDialect dialect = getCurrentDialect();
        return dialect != null ? dialect.buildUpsertBatchSql(meta) : "";
    }
}