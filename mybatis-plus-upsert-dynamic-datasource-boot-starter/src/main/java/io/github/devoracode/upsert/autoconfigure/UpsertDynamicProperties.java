package io.github.devoracode.upsert.autoconfigure;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for dynamic-datasource upsert support.
 *
 * <p>These properties are bound to the {@code mybatis-plus.upsert.dynamic} prefix.
 * The per-datasource {@code db-type} is optional — if not specified, the library
 * attempts to auto-infer it from the JDBC URL defined in {@code spring.datasource.dynamic.datasource}.
 *
 * @author devoracode
 * @since 1.2.0
 */
@Data
@ConfigurationProperties(prefix = "mybatis-plus.upsert.dynamic")
public class UpsertDynamicProperties {

    /**
     * Whether dynamic upsert support is enabled. Default is true.
     */
    private boolean enabled = true;

    /**
     * Global default for MySQL syntax style. Applied to all MySQL data sources
     * that do not override this at the per-datasource level. Default is false.
     */
    private boolean useNewMysqlSyntax = false;

    /**
     * Map of per-datasource upsert configuration.
     * Keys are datasource names matching those in {@code spring.datasource.dynamic.datasource}.
     */
    private Map<String, DataSourceConfig> datasource = new HashMap<>();

    /**
     * Per-datasource upsert configuration.
     *
     * @author devoracode
     * @since 1.2.0
     */
    @Data
    public static class DataSourceConfig {
        /**
         * The database type for this data source. Optional — auto-inferred from JDBC URL if not specified.
         */
        private String dbType;
        /**
         * Whether to use the new MySQL 8.0.20+ syntax (AS alias) for this data source.
         * Overrides the global {@code use-new-mysql-syntax} setting.
         */
        private boolean useNewMysqlSyntax = false;
        /**
         * Bean name of a user-defined {@code UpsertDialect} to use when {@code db-type} is set to {@code custom}.
         * Ignored for built-in db types.
         */
        private String dialectRef;
    }
}