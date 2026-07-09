package io.github.devoracode.upsert.util;

import io.github.devoracode.upsert.dialect.*;
import io.github.devoracode.upsert.exception.UpsertException;
import io.github.devoracode.upsert.util.DbTypeDetector.DbType;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Factory for creating {@link UpsertDialect} instances based on database type.
 * Instances are cached and reused across threads. This class is stateless and thread-safe.
 *
 * @author devoracode
 * @since 1.0.0
 */
public final class DialectFactory {

    private static final Map<String, UpsertDialect> INSTANCES = new ConcurrentHashMap<>();

    private DialectFactory() {
    }

    /**
     * Creates an UpsertDialect for the given database type string.
     *
     * @param dbTypeStr        the database type string (e.g., "mysql", "postgresql")
     * @param useNewMysqlSyntax whether to use the new MySQL 8.0.20+ syntax (AS alias) for MySQL
     * @return the corresponding UpsertDialect instance
     * @throws UpsertException if the database type is unknown or unsupported
     */
    public static UpsertDialect create(String dbTypeStr, boolean useNewMysqlSyntax) {
        return create(parseDbType(dbTypeStr), useNewMysqlSyntax);
    }

    /**
     * Creates an UpsertDialect for the given database type enum.
     *
     * @param dbType        the database type enum
     * @param useNewMysqlSyntax whether to use the new MySQL 8.0.20+ syntax (AS alias) for MySQL
     * @return the corresponding UpsertDialect instance, or null if dbType is CUSTOM
     * @throws UpsertException if the database type is unsupported
     */
    public static UpsertDialect create(DbType dbType, boolean useNewMysqlSyntax) {
        if (dbType == DbType.CUSTOM) {
            return null;
        }
        String cacheKey = dbType + ":" + (dbType == DbType.MYSQL ? Boolean.toString(useNewMysqlSyntax) : "");
        return INSTANCES.computeIfAbsent(cacheKey,
                k -> newInstance(dbType, useNewMysqlSyntax));
    }

    private static UpsertDialect newInstance(DbType dbType, boolean useNewMysqlSyntax) {
        switch (dbType) {
            case MYSQL:      return newMysqlInstance(useNewMysqlSyntax);
            case POSTGRESQL: return new PostgresUpsertDialect();
            case ORACLE:     return new OracleUpsertDialect();
            case SQLSERVER:  return new SqlServerUpsertDialect();
            case H2:         return new H2UpsertDialect();
            default:
                throw new UpsertException("Unsupported database type: " + dbType
                        + ". Set db-type explicitly or implement UpsertDialect.");
        }
    }

    /**
     * Creates a new MySQL dialect instance.
     *
     * @param useNewMysqlSyntax whether to use the new MySQL 8.0.20+ syntax (AS alias)
     * @return the MySQL UpsertDialect instance
     */
    public static UpsertDialect newMysqlInstance(boolean useNewMysqlSyntax) {
        if (useNewMysqlSyntax) {
            return new MysqlUpsertDialect();
        }
        return new MysqlLegacyUpsertDialect();
    }

    /**
     * Parses a database type string and throws if unknown.
     *
     * @param value the database type string
     * @return the parsed DbType enum
     * @throws UpsertException if the value cannot be parsed
     */
    public static DbType parseDbType(String value) {
        return DbTypeDetector.parseDbType(value);
    }
}