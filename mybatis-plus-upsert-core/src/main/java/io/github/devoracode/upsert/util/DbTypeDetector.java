package io.github.devoracode.upsert.util;

import io.github.devoracode.upsert.exception.UpsertException;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Pattern;

/**
 * Utility class for detecting database types from configuration strings or JDBC URLs.
 * All methods are static and thread-safe.
 *
 * @author devoracode
 * @since 1.0.0
 */
@Slf4j
public class DbTypeDetector {

    private static final Pattern NORMALIZE_PATTERN = Pattern.compile("[^a-z0-9./-]+");

    /**
     * Database type enumeration.
     */
    public enum DbType {
        /**
         * MySQL or MariaDB.
         */
        MYSQL,
        /**
         * PostgreSQL.
         */
        POSTGRESQL,
        /**
         * Oracle.
         */
        ORACLE,
        /**
         * Microsoft SQL Server.
         */
        SQLSERVER,
        /**
         * H2 database.
         */
        H2,
        /**
         * Custom dialect (user-provided).
         */
        CUSTOM,
        /**
         * Unknown or unrecognized database type.
         */
        UNKNOWN
    }

    /**
     * Tries to parse a database type string into a {@link DbType} enum.
     * Returns {@link DbType#UNKNOWN} if the string does not match any known type.
     *
     * @param dbType the database type string (case-insensitive, may contain partial matches)
     * @return the parsed DbType, or UNKNOWN if not recognized
     */
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

    /**
     * Parses a database type string into a {@link DbType} enum.
     * Throws {@link UpsertException} if the string does not match any known type.
     *
     * @param dbType the database type string (case-insensitive)
     * @return the parsed DbType
     * @throws UpsertException if the database type is unknown
     */
    public static DbType parseDbType(String dbType) {
        DbType type = tryParseDbType(dbType);
        if (type == DbType.UNKNOWN) {
            throw new UpsertException("Unknown db-type: " + dbType);
        }
        return type;
    }

    /**
     * Parses a JDBC URL to determine the database type.
     * Matches prefixes like {@code jdbc:mysql:}, {@code jdbc:postgresql:}, etc.
     *
     * @param jdbcUrl the JDBC URL (e.g., {@code jdbc:mysql://localhost:3306/db})
     * @return the detected DbType, or UNKNOWN if the URL does not match any known type
     */
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

    /**
     * Normalizes a raw string by replacing non-alphanumeric characters (except dot, slash, hyphen)
     * with underscores and converting to lowercase.
     *
     * @param raw the raw string to normalize (may be null)
     * @return the normalized string, or empty string if input is null
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return NORMALIZE_PATTERN.matcher(raw.trim().toLowerCase()).replaceAll("_");
    }
}