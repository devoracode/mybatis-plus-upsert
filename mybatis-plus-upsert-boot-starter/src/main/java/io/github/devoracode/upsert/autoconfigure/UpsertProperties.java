package io.github.devoracode.upsert.autoconfigure;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for single-datasource upsert support.
 *
 * <p>These properties are bound to the {@code mybatis-plus.upsert} prefix.
 * The {@code db-type} property is optional — if not specified, the library
 * attempts to auto-infer it from the JDBC URL.
 *
 * @author devoracode
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "mybatis-plus.upsert")
public class UpsertProperties {

    /**
     * Whether upsert support is enabled. Default is true.
     */
    private boolean enabled = true;

    /**
     * The database type (e.g., "mysql", "postgresql"). Optional — auto-inferred from JDBC URL if not specified.
     */
    private String dbType;

    /**
     * Whether to use the new MySQL 8.0.20+ syntax (AS alias) for MySQL upserts.
     * Only applies when the database type is MySQL. Default is false (legacy VALUES() syntax).
     */
    private boolean useNewMysqlSyntax = false;

    /**
     * Whether to auto-fill entity fields before upsert using MyBatis-Plus'
     * {@code MetaObjectHandler}. When enabled, both {@code insertFill} and
     * {@code updateFill} are invoked because upsert is semantically
     * "insert or update". Default is true.
     *
     * @since 1.5.0
     */
    private boolean autoFill = true;
}