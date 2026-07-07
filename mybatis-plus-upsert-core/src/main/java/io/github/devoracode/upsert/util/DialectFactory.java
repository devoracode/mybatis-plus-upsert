package io.github.devoracode.upsert.util;

import io.github.devoracode.upsert.dialect.*;
import io.github.devoracode.upsert.exception.UpsertException;
import io.github.devoracode.upsert.util.DbTypeDetector.DbType;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public final class DialectFactory {

    private static final Map<String, UpsertDialect> INSTANCES = new ConcurrentHashMap<>();

    private DialectFactory() {
    }

    public static UpsertDialect create(String dbTypeStr, boolean useNewMysqlSyntax) {
        return create(parseDbType(dbTypeStr), useNewMysqlSyntax);
    }

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

    public static UpsertDialect newMysqlInstance(boolean useNewMysqlSyntax) {
        if (useNewMysqlSyntax) {
            return new MysqlUpsertDialect();
        }
        return new MysqlLegacyUpsertDialect();
    }

    public static DbType parseDbType(String value) {
        return DbTypeDetector.parseDbType(value);
    }
}
