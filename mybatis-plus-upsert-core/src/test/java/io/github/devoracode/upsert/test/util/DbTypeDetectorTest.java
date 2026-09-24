package io.github.devoracode.upsert.test.util;

import io.github.devoracode.upsert.util.DbTypeDetector;
import io.github.devoracode.upsert.util.DbTypeDetector.DbType;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class DbTypeDetectorTest {

    @Test
    void try_parse_db_type_returns_unknown_instead_of_throwing() {
        assertThat(DbTypeDetector.tryParseDbType("DB2/NT64")).isEqualTo(DbType.UNKNOWN);
        assertThat(DbTypeDetector.tryParseDbType("DM DBMS")).isEqualTo(DbType.UNKNOWN);
        assertThat(DbTypeDetector.tryParseDbType(null)).isEqualTo(DbType.UNKNOWN);
    }

    @Test
    void try_parse_db_type_still_recognizes_built_in_types() {
        assertThat(DbTypeDetector.tryParseDbType("MySQL")).isEqualTo(DbType.MYSQL);
        assertThat(DbTypeDetector.tryParseDbType("PostgreSQL")).isEqualTo(DbType.POSTGRESQL);
        assertThat(DbTypeDetector.tryParseDbType("Oracle")).isEqualTo(DbType.ORACLE);
        assertThat(DbTypeDetector.tryParseDbType("Microsoft SQL Server")).isEqualTo(DbType.SQLSERVER);
        assertThat(DbTypeDetector.tryParseDbType("H2")).isEqualTo(DbType.H2);
    }

    @Test
    void try_parse_db_type_accepts_readme_documented_spellings() {
        // README 中文档化的合法值必须全部可解析
        assertThat(DbTypeDetector.tryParseDbType("sqlserver")).isEqualTo(DbType.SQLSERVER);
        assertThat(DbTypeDetector.tryParseDbType("sql-server")).isEqualTo(DbType.SQLSERVER);
        assertThat(DbTypeDetector.tryParseDbType("sql server")).isEqualTo(DbType.SQLSERVER);
        assertThat(DbTypeDetector.tryParseDbType("SQLSERVER")).isEqualTo(DbType.SQLSERVER);
        assertThat(DbTypeDetector.tryParseDbType("postgres")).isEqualTo(DbType.POSTGRESQL);
        assertThat(DbTypeDetector.tryParseDbType("mariadb")).isEqualTo(DbType.MYSQL);
        assertThat(DbTypeDetector.tryParseDbType("custom")).isEqualTo(DbType.CUSTOM);
    }

    @Test
    void parse_db_type_by_jdbc_url_handles_null_url() {
        assertThat(DbTypeDetector.parseDbTypeByJdbcUrl(null)).isEqualTo(DbType.UNKNOWN);
    }

    @Test
    void parse_db_type_still_throws_for_user_supplied_configuration_errors() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> DbTypeDetector.parseDbType("oceanbase"))
                .isInstanceOf(io.github.devoracode.upsert.exception.UpsertException.class);
    }

    @Test
    void try_parse_db_type_is_independent_of_default_locale() {
        Locale originalLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));

            assertThat(DbTypeDetector.tryParseDbType("mariadb")).isEqualTo(DbType.MYSQL);
            assertThat(DbTypeDetector.tryParseDbType("Microsoft")).isEqualTo(DbType.SQLSERVER);
        } finally {
            Locale.setDefault(originalLocale);
        }
    }

    @Test
    void parse_db_type_by_jdbc_url_is_independent_of_default_locale() {
        Locale originalLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));

            assertThat(DbTypeDetector.parseDbTypeByJdbcUrl("jdbc:MARIADB://localhost:3306/test"))
                    .isEqualTo(DbType.MYSQL);
        } finally {
            Locale.setDefault(originalLocale);
        }
    }
}
