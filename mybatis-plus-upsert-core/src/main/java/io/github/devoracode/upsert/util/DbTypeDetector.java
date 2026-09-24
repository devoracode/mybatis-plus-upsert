package io.github.devoracode.upsert.util;

import io.github.devoracode.upsert.exception.UpsertException;
import lombok.extern.slf4j.Slf4j;

import java.util.Locale;

/**
 * 从配置字符串或 JDBC URL 检测数据库类型的无状态工具类。
 *
 * @author devoracode
 * @since 1.0.0
 */
@Slf4j
public class DbTypeDetector {

    /**
     * 本库可识别的数据库类型。
     */
    public enum DbType {
        /** MySQL 或 MariaDB。 */
        MYSQL,
        /** PostgreSQL。 */
        POSTGRESQL,
        /** Oracle。 */
        ORACLE,
        /** SQL Server。 */
        SQLSERVER,
        /** H2。 */
        H2,
        /** 由用户自行提供的方言。 */
        CUSTOM,
        /** 无法识别。 */
        UNKNOWN
    }

    /**
     * 把数据库类型字符串解析为 {@link DbType}，不区分大小写、允许部分匹配；无法识别时返回 {@link DbType#UNKNOWN} 而不抛异常。
     *
     * @param dbType 数据库类型字符串，可为 null
     */
    public static DbType tryParseDbType(String dbType) {
        if (dbType == null) {
            return DbType.UNKNOWN;
        }
        String upper = dbType.toUpperCase(Locale.ROOT);
        if (upper.contains("MYSQL") || upper.contains("MARIADB")) {
            return DbType.MYSQL;
        }
        if (upper.contains("POSTGRESQL") || upper.contains("POSTGRES")) {
            return DbType.POSTGRESQL;
        }
        if (upper.contains("ORACLE")) {
            return DbType.ORACLE;
        }
        if (upper.contains("SQLSERVER") || upper.contains("SQL SERVER") || upper.contains("SQL-SERVER")
                || upper.contains("MICROSOFT")) {
            return DbType.SQLSERVER;
        }
        if (upper.contains("H2")) {
            return DbType.H2;
        }
        if (upper.contains("CUSTOM")) {
            return DbType.CUSTOM;
        }
        return DbType.UNKNOWN;
    }

    /**
     * 与 {@link #tryParseDbType} 相同，但无法识别时抛 {@link UpsertException}。
     *
     * @throws UpsertException 数据库类型未知
     */
    public static DbType parseDbType(String dbType) {
        DbType type = tryParseDbType(dbType);
        if (type == DbType.UNKNOWN) {
            throw new UpsertException("Unknown db-type: " + dbType);
        }
        return type;
    }

    /**
     * 按 JDBC URL 中的子串识别数据库类型。
     *
     * @param jdbcUrl JDBC URL，可为 null
     * @return 识别结果；URL 为 null 或不含已知前缀时为 {@link DbType#UNKNOWN}
     */
    public static DbType parseDbTypeByJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null) {
            return DbType.UNKNOWN;
        }
        String url = jdbcUrl.toLowerCase(Locale.ROOT);
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