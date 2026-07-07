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
    private volatile String primary;

    public void addDialect(String dataSourceName, UpsertDialect dialect) {
        dialectMap.put(dataSourceName, dialect);
    }

    public Map<String, UpsertDialect> getDialectMap() {
        return Collections.unmodifiableMap(dialectMap);
    }

    public String getPrimary() {
        return primary;
    }

    public void setPrimary(String primary) {
        this.primary = primary;
    }

    @Override
    public UpsertDialect getCurrentDialect() {
        String dataSourceName = DynamicDataSourceContextHolder.peek();
        if(dataSourceName == null) {
            dataSourceName = primary;
        }
        UpsertDialect dialect = dialectMap.get(dataSourceName);
        if (dialect == null) {
            throw new UpsertException("No upsert dialect configured for data source '" + dataSourceName + "'");
        }
        if (log.isDebugEnabled()) {
            log.debug("Using dialect {} for data source {}", dialect.getClass().getSimpleName(), dataSourceName);
        }
        return dialect;
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
