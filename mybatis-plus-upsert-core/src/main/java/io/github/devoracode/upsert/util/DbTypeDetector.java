package io.github.devoracode.upsert.util;

import io.github.devoracode.upsert.exception.UpsertException;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Pattern;

@Slf4j
public class DbTypeDetector {

    private static final Pattern NORMALIZE_PATTERN = Pattern.compile("[^a-z0-9./-]+");

    public enum DbType {
        MYSQL,
        POSTGRESQL,
        ORACLE,
        SQLSERVER,
        H2,
        CUSTOM,
        UNKNOWN
    }

    public static DbType tryParseDbType(String dbType) {
        if (dbType == null) {
            return DbType.UNKNOWN;
        }
        String upper = dbType.toUpperCase();
        if (upper.contains("MYSQL") || upper.contains("MARIADB")) return DbType.MYSQL;
        if (upper.contains("POSTGRESQL"))                          return DbType.POSTGRESQL;
        if (upper.contains("ORACLE"))                              return DbType.ORACLE;
        if (upper.contains("SQL SERVER") || upper.contains("MICROSOFT")) return DbType.SQLSERVER;
        if (upper.contains("H2"))                                  return DbType.H2;
        if (upper.contains("CUSTOM"))                              return DbType.CUSTOM;
        return DbType.UNKNOWN;
    }

    public static DbType parseDbType(String dbType) {
        DbType type = tryParseDbType(dbType);
        if (type == DbType.UNKNOWN) {
            throw new UpsertException("Unknown db-type: " + dbType);
        }
        return type;
    }

    public static DbType parseDbTypeByJdbcUrl(String jdbcUrl) {
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

    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return NORMALIZE_PATTERN.matcher(raw.trim().toLowerCase()).replaceAll("_");
    }
}
