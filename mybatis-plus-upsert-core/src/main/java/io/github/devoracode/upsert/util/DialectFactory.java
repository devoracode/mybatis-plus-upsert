package io.github.devoracode.upsert.util;

import io.github.devoracode.upsert.dialect.*;
import io.github.devoracode.upsert.exception.UpsertException;
import io.github.devoracode.upsert.util.DbTypeDetector.DbType;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * 根据数据库类型创建 {@link UpsertDialect} 实例的工厂类。
 * 实例被缓存并在所有线程间共享复用，此类无状态且线程安全。
 *
 * @author devoracode
 * @since 1.0.0
 */
public final class DialectFactory {

    private static final Map<String, UpsertDialect> INSTANCES = new ConcurrentHashMap<>();

    private DialectFactory() {
    }

    /**
     * 根据数据库类型字符串创建对应的 {@link UpsertDialect} 实例。
     *
     * @param dbTypeStr         数据库类型字符串（如 "mysql"、"postgresql"）
     * @param useNewMysqlSyntax 是否对 MySQL 使用新的 MySQL 8.0.20+ 语法（AS 别名形式）
     * @return 对应的 UpsertDialect 实例
     * @throws UpsertException 如果数据库类型未知或不支持
     */
    public static UpsertDialect create(String dbTypeStr, boolean useNewMysqlSyntax) {
        return create(parseDbType(dbTypeStr), useNewMysqlSyntax);
    }

    /**
     * 根据数据库类型枚举创建对应的 {@link UpsertDialect} 实例。
     *
     * @param dbType            数据库类型枚举值
     * @param useNewMysqlSyntax 是否对 MySQL 使用新的 MySQL 8.0.20+ 语法（AS 别名形式）
     * @return 对应的 UpsertDialect 实例；若 dbType 为 CUSTOM 则返回 null
     * @throws UpsertException 如果数据库类型不支持
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
                throw new UpsertException("不支持的数据库类型: " + dbType
                        + "，请显式设置 db-type 或实现 UpsertDialect 接口。");
        }
    }

    /**
     * 根据是否使用新语法创建新的 MySQL 方言实例。
     *
     * @param useNewMysqlSyntax 是否使用新的 MySQL 8.0.20+ 语法（AS 别名形式）
     * @return 对应的 MySQL UpsertDialect 实例
     */
    public static UpsertDialect newMysqlInstance(boolean useNewMysqlSyntax) {
        if (useNewMysqlSyntax) {
            return new MysqlUpsertDialect();
        }
        return new MysqlLegacyUpsertDialect();
    }

    /**
     * 解析数据库类型字符串，无法识别时抛出异常。
     *
     * @param value 数据库类型字符串
     * @return 解析后的 DbType 枚举
     * @throws UpsertException 如果字符串无法解析
     */
    public static DbType parseDbType(String value) {
        return DbTypeDetector.parseDbType(value);
    }
}