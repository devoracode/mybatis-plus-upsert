package io.github.devoracode.upsert.test.util;

import io.github.devoracode.upsert.util.DbTypeDetector;
import io.github.devoracode.upsert.util.DbTypeDetector.DbType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DbTypeDetectorTest {

    @Test
    void normalize_lowercases_and_collapses_whitespace() {
        assertThat(DbTypeDetector.normalize("DM DBMS")).isEqualTo("dm_dbms");
    }

    @Test
    void normalize_keeps_slash_for_db2_style_names() {
        assertThat(DbTypeDetector.normalize("DB2/NT64")).isEqualTo("db2/nt64");
    }

    @Test
    void normalize_strips_single_quote_to_avoid_breaking_ognl_string_literal() {
        assertThat(DbTypeDetector.normalize("Weird'DB")).isEqualTo("weird_db");
        assertThat(DbTypeDetector.normalize("Weird'DB")).doesNotContain("'");
    }

    @Test
    void normalize_strips_xml_special_characters() {
        assertThat(DbTypeDetector.normalize("<Some&DB>")).doesNotContain("<", ">", "&");
    }

    @Test
    void normalize_is_idempotent() {
        String once = DbTypeDetector.normalize("Weird'DB Name");
        String twice = DbTypeDetector.normalize(once);
        assertThat(once).isEqualTo(twice);
    }

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
    void parse_db_type_still_throws_for_user_supplied_configuration_errors() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> DbTypeDetector.parseDbType("oceanbase"))
                .isInstanceOf(io.github.devoracode.upsert.exception.UpsertException.class);
    }
}
