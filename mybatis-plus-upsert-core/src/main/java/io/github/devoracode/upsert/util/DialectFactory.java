package io.github.devoracode.upsert.util;

import io.github.devoracode.upsert.dialect.*;
import io.github.devoracode.upsert.exception.UpsertException;
import io.github.devoracode.upsert.util.DbTypeDetector.DbType;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * 按数据库类型创建 {@link UpsertDialect} 的工厂，实例按类型+语法开关缓存。
 *
 * @author devoracode
 * @since 1.0.0
 */
public final class DialectFactory {

    private static final Map<String, UpsertDialect> INSTANCES = new ConcurrentHashMap<>();

    private DialectFactory() {
    }

    /**
     * 按数据库类型字符串创建方言实例。
     *
     * @throws UpsertException 数据库类型未知或不支持
     */
    public static UpsertDialect create(String dbTypeStr, boolean useNewMysqlSyntax) {
        return create(parseDbType(dbTypeStr), useNewMysqlSyntax);
    }

    /**
     * 按数据库类型创建方言实例。
     *
     * @param dbType            数据库类型
     * @param useNewMysqlSyntax MySQL 是否使用 8.0.19+ 的 {@code AS} 别名语法，其他数据库忽略
     * @return 对应的 UpsertDialect 实例；{@code dbType} 为 CUSTOM 时返回 null
     * @throws UpsertException 数据库类型不支持
     */
    public static UpsertDialect create(DbType dbType, boolean useNewMysqlSyntax) {
        if (dbType == DbType.CUSTOM) {
            return null;
        }
        String cacheKey = dbType + (dbType == DbType.MYSQL ? ":" + useNewMysqlSyntax : "");
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
                throw new UpsertException("Unsupported db-type: " + dbType
                        + ". Set db-type explicitly or implement the UpsertDialect interface.");
        }
    }

    /**
     * 新建 MySQL 方言实例，每次返回新实例、不走 {@link #create} 的缓存。
     */
    public static UpsertDialect newMysqlInstance(boolean useNewMysqlSyntax) {
        if (useNewMysqlSyntax) {
            return new MysqlUpsertDialect();
        }
        return new MysqlLegacyUpsertDialect();
    }

    /**
     * 解析数据库类型字符串。
     *
     * @throws UpsertException 字符串无法解析为数据库类型
     */
    public static DbType parseDbType(String value) {
        return DbTypeDetector.parseDbType(value);
    }
}