package io.github.devoracode.upsert.autoconfigure;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for dynamic datasource upsert.
 * Prefix: mybatis-plus.upsert.dynamic
 */
@Data
public class UpsertDynamicProperties {

    /**
     * Enable dynamic datasource upsert support.
     * Default: true
     */
    private boolean enabled = true;

    /**
     * Data source configurations for each data source.
     * Key is the data source name (must match spring.datasource.dynamic.datasource keys).
     */
    private Map<String, DataSourceConfig> datasources = new HashMap<>();

    /**
     * Configuration for a single data source.
     */
    @Getter
    @Setter
    public static class DataSourceConfig {

        /**
         * Database type for this data source.
         * Values: mysql, postgresql, oracle, sqlserver, h2, custom
         */
        private String dbType;

        /**
         * Use new MySQL syntax (INSERT ... AS new ON DUPLICATE KEY UPDATE).
         * Only applicable for MySQL.
         * Default: false
         */
        private boolean useNewMysqlSyntax = false;
    }
}