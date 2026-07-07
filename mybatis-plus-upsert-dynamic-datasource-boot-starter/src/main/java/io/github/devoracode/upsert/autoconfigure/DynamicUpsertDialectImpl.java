package io.github.devoracode.upsert.autoconfigure;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import io.github.devoracode.upsert.dialect.DynamicUpsertDialect;
import io.github.devoracode.upsert.dialect.UpsertDialect;
import io.github.devoracode.upsert.core.UpsertMeta;
import io.github.devoracode.upsert.exception.UpsertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DynamicUpsertDialectImpl implements DynamicUpsertDialect {

    private static final Logger log = LoggerFactory.getLogger(DynamicUpsertDialectImpl.class);

    private final Map<String, UpsertDialect> dialectMap = new ConcurrentHashMap<>();
    private UpsertDialect primaryDialect;

    public void addDialect(String dataSourceName, UpsertDialect dialect) {
        dialectMap.put(dataSourceName, dialect);
    }

    public Map<String, UpsertDialect> getDialectMap() {
        return Collections.unmodifiableMap(dialectMap);
    }

    public UpsertDialect getPrimaryDialect() {
        return primaryDialect;
    }

    public void setPrimaryDialect(UpsertDialect primaryDialect) {
        this.primaryDialect = primaryDialect;
    }

    @Override
    public UpsertDialect getCurrentDialect() {
        String dataSourceName = DynamicDataSourceContextHolder.peek();
        if (dataSourceName != null) {
            UpsertDialect dialect = dialectMap.get(dataSourceName);
            if (dialect != null) {
                if (log.isDebugEnabled()) {
                    log.debug("Using dialect {} for data source {}", dialect.getClass().getSimpleName(), dataSourceName);
                }
                return dialect;
            }
            log.warn("No dialect configured for data source '{}', falling back to primary dialect", dataSourceName);
        }

        if (primaryDialect == null) {
            throw new UpsertException("Primary dialect has not been configured");
        }

        if (log.isDebugEnabled()) {
            log.debug("Falling back to primary dialect {}", primaryDialect.getClass().getSimpleName());
        }
        return primaryDialect;
    }

    @Override
    public String buildUpsertSql(UpsertMeta meta) {
        return getCurrentDialect().buildUpsertSql(meta);
    }

    @Override
    public String buildUpsertBatchSql(UpsertMeta meta) {
        return getCurrentDialect().buildUpsertBatchSql(meta);
    }
}
