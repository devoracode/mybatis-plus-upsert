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
     * @param dbTypeStr         数据库类型字符串，大小写不敏感
     * @param useNewMysqlSyntax MySQL 是否使用 8.0.19+ 的 {@code AS} 别名语法，其他数据库忽略
     * @param sqlserverHoldlock SQL Server 的 MERGE 目标表是否加 {@code WITH (HOLDLOCK)}，其他数据库忽略
     * @return 对应的 UpsertDialect 实例
     * @throws UpsertException 数据库类型未知或不支持
     */
    public static UpsertDialect create(String dbTypeStr, boolean useNewMysqlSyntax, boolean sqlserverHoldlock) {
        return create(parseDbType(dbTypeStr), useNewMysqlSyntax, sqlserverHoldlock);
    }

    /**
     * 按数据库类型创建方言实例。
     *
     * @param dbType            数据库类型
     * @param useNewMysqlSyntax MySQL 是否使用 8.0.19+ 的 {@code AS} 别名语法，其他数据库忽略
     * @param sqlserverHoldlock SQL Server 的 MERGE 目标表是否加 {@code WITH (HOLDLOCK)}，其他数据库忽略
     * @return 对应的 UpsertDialect 实例
     * @throws UpsertException 数据库类型不支持，或 dbType 为 CUSTOM（自定义方言不由本工厂创建）
     */
    public static UpsertDialect create(DbType dbType, boolean useNewMysqlSyntax, boolean sqlserverHoldlock) {
        if (dbType == DbType.CUSTOM) {
            throw new UpsertException("db-type=custom means the dialect is supplied by the application;"
                    + " register it as an UpsertDialect bean instead of calling DialectFactory");
        }
        // 语法开关会改变生成的 SQL，必须进入缓存键，否则先到的取值会被后来的调用复用
        String cacheKey = dbType
                + (dbType == DbType.MYSQL ? ":" + useNewMysqlSyntax : "")
                + (dbType == DbType.SQLSERVER ? ":holdlock=" + sqlserverHoldlock : "");
        return INSTANCES.computeIfAbsent(cacheKey,
                k -> newInstance(dbType, useNewMysqlSyntax, sqlserverHoldlock));
    }

    private static UpsertDialect newInstance(DbType dbType, boolean useNewMysqlSyntax, boolean sqlserverHoldlock) {
        switch (dbType) {
            case MYSQL:      return newMysqlInstance(useNewMysqlSyntax);
            case POSTGRESQL: return new PostgresUpsertDialect();
            case ORACLE:     return new OracleUpsertDialect();
            case SQLSERVER:  return new SqlServerUpsertDialect(sqlserverHoldlock);
            case H2:         return new H2UpsertDialect();
            default:
                throw new UpsertException("Unsupported db-type: " + dbType
                        + ". Set db-type explicitly or implement the UpsertDialect interface.");
        }
    }

    /**
     * 新建 MySQL 方言实例，每次返回新实例、不走 {@link #create} 的缓存。
     *
     * @param useNewMysqlSyntax 是否使用 8.0.19+ 的 {@code AS} 别名语法
     * @return {@link MysqlUpsertDialect} 或 {@link MysqlLegacyUpsertDialect} 实例
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
     * @param value 数据库类型字符串，大小写不敏感
     * @return 解析出的数据库类型
     * @throws UpsertException 字符串无法解析为数据库类型
     */
    public static DbType parseDbType(String value) {
        return DbTypeDetector.parseDbType(value);
    }
}