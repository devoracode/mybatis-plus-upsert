package io.github.devoracode.upsert.util;

import io.github.devoracode.upsert.exception.UpsertException;
import lombok.extern.slf4j.Slf4j;

/**
 * 从配置字符串或 JDBC URL 中检测数据库类型的工具类。
 * 所有方法均为静态方法，线程安全。
 *
 * @author devoracode
 * @since 1.0.0
 */
@Slf4j
public class DbTypeDetector {

    /**
     * 数据库类型枚举。
     */
    public enum DbType {
        /**
         * MySQL 或 MariaDB。
         */
        MYSQL,
        /**
         * PostgreSQL。
         */
        POSTGRESQL,
        /**
         * Oracle。
         */
        ORACLE,
        /**
         * Microsoft SQL Server。
         */
        SQLSERVER,
        /**
         * H2 数据库。
         */
        H2,
        /**
         * 自定义方言（由用户提供）。
         */
        CUSTOM,
        /**
         * 未知或无法识别的数据库类型。
         */
        UNKNOWN
    }

    /**
     * 尝试将数据库类型字符串解析为 {@link DbType} 枚举。
     * 无法匹配时返回 {@link DbType#UNKNOWN}。
     *
     * @param dbType 数据库类型字符串（不区分大小写，允许部分匹配）
     * @return 解析后的 DbType，无法识别时返回 UNKNOWN
     */
    public static DbType tryParseDbType(String dbType) {
        if (dbType == null) {
            return DbType.UNKNOWN;
        }
        String upper = dbType.toUpperCase();
        if (upper.contains("MYSQL") || upper.contains("MARIADB")) return DbType.MYSQL;
        if (upper.contains("POSTGRESQL") || upper.contains("POSTGRES")) return DbType.POSTGRESQL;
        if (upper.contains("ORACLE"))                              return DbType.ORACLE;
        if (upper.contains("SQLSERVER") || upper.contains("SQL SERVER") || upper.contains("SQL-SERVER")
                || upper.contains("MICROSOFT"))                   return DbType.SQLSERVER;
        if (upper.contains("H2"))                                  return DbType.H2;
        if (upper.contains("CUSTOM"))                              return DbType.CUSTOM;
        return DbType.UNKNOWN;
    }

    /**
     * 将数据库类型字符串解析为 {@link DbType} 枚举。
     * 无法匹配时抛出 {@link UpsertException}。
     *
     * @param dbType 数据库类型字符串（不区分大小写）
     * @return 解析后的 DbType
     * @throws UpsertException 如果数据库类型未知
     */
    public static DbType parseDbType(String dbType) {
        DbType type = tryParseDbType(dbType);
        if (type == DbType.UNKNOWN) {
            throw new UpsertException("Unknown db-type: " + dbType);
        }
        return type;
    }

    /**
     * 通过 JDBC URL 解析数据库类型。
     * 匹配 {@code jdbc:mysql:}、{@code jdbc:postgresql:} 等前缀。
     *
     * @param jdbcUrl JDBC URL（如 {@code jdbc:mysql://localhost:3306/db}）；可为 null
     * @return 检测到的 DbType；URL 为 null 或无法匹配任何已知类型时返回 UNKNOWN
     */
    public static DbType parseDbTypeByJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null) {
            return DbType.UNKNOWN;
        }
        String url = jdbcUrl.toLowerCase();
        if (url.contains(":mysql:") || url.contains(":mariadb:")) {
            return DbType.MYSQL;
        } else if (url.contains(":oracle:")) {
            return DbType.ORACLE;
        } else if (url.contains(":sqlserver:") || url.contains(":microsoft:")) {
            return DbType.SQLSERVER;
        } else if (url.contains(":postgresql:")) {
            return DbType.POSTGRESQL;
        } else if (url.contains(":h2:")) {
            return DbType.H2;
        }
        return DbType.UNKNOWN;
    }
}