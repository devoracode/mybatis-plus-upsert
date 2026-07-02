package io.github.devoracode.upsert.util;

import io.github.devoracode.upsert.exception.UpsertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

public class DbTypeDetector {

    private static final Logger log = LoggerFactory.getLogger(DbTypeDetector.class);

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

    public static DbType tryParseDbType(String value) {
        if (value == null) {
            return DbType.UNKNOWN;
        }
        String upper = value.toUpperCase();
        if (upper.contains("MYSQL") || upper.contains("MARIADB")) return DbType.MYSQL;
        if (upper.contains("POSTGRESQL"))                          return DbType.POSTGRESQL;
        if (upper.contains("ORACLE"))                              return DbType.ORACLE;
        if (upper.contains("SQL SERVER") || upper.contains("MICROSOFT")) return DbType.SQLSERVER;
        if (upper.contains("H2"))                                  return DbType.H2;
        if (upper.contains("CUSTOM"))                              return DbType.CUSTOM;
        return DbType.UNKNOWN;
    }

    public static DbType parseDbType(String value) {
        DbType type = tryParseDbType(value);
        if (type == DbType.UNKNOWN) {
            throw new UpsertException("Unknown db-type: " + value);
        }
        return type;
    }

    public static String normalize(String raw) {
        return NORMALIZE_PATTERN.matcher(raw.trim().toLowerCase()).replaceAll("_");
    }
}
