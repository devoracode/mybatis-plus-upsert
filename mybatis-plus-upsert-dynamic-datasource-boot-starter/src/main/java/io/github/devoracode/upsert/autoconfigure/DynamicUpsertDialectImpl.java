package io.github.devoracode.upsert.autoconfigure;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import io.github.devoracode.upsert.dialect.DynamicUpsertDialect;
import io.github.devoracode.upsert.dialect.UpsertDialect;
import io.github.devoracode.upsert.core.UpsertMeta;
import io.github.devoracode.upsert.exception.UpsertException;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of {@link DynamicUpsertDialect} for dynamic datasource environments.
 *
 * <p>Maintains a mapping of data source names to {@link UpsertDialect} instances.
 * At runtime, resolves the correct dialect by inspecting the current data source
 * context via {@link DynamicDataSourceContextHolder}. Falls back to the configured
 * primary data source if no context is active.
 *
 * @author devoracode
 * @since 1.2.0
 */
@Slf4j
public class DynamicUpsertDialectImpl implements DynamicUpsertDialect {

    private final Map<String, UpsertDialect> dialectMap = new ConcurrentHashMap<>();
    private volatile String primary;

    /**
     * Registers a dialect for the given data source name.
     *
     * @param dataSourceName the data source name (must not be null)
     * @param dialect the UpsertDialect instance (must not be null)
     */
    public void addDialect(String dataSourceName, UpsertDialect dialect) {
        dialectMap.put(dataSourceName, dialect);
    }

    /**
     * Returns an unmodifiable view of the registered dialect map.
     *
     * @return the dialect map (data source name &rarr; UpsertDialect)
     */
    public Map<String, UpsertDialect> getDialectMap() {
        return Collections.unmodifiableMap(dialectMap);
    }

    /**
     * Returns the name of the primary data source.
     *
     * @return the primary data source name, or null if not set
     */
    public String getPrimary() {
        return primary;
    }

    /**
     * Sets the primary data source name.
     *
     * @param primary the primary data source name
     */
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