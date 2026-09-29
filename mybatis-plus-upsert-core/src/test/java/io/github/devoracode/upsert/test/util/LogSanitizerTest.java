package io.github.devoracode.upsert.test.util;

import io.github.devoracode.upsert.util.LogSanitizer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogSanitizerTest {

    @Test
    void masks_query_string_credentials_and_keeps_other_params() {
        String url = "jdbc:mysql://host:3306/db?user=root&password=s3cret&useSSL=false";

        assertThat(LogSanitizer.redactJdbcUrl(url))
                .isEqualTo("jdbc:mysql://host:3306/db?user=***&password=***&useSSL=false");
    }

    @Test
    void masks_semicolon_separated_credentials() {
        String url = "jdbc:sqlserver://host:1433;databaseName=db;user=sa;password=P@ss;encrypt=true";

        assertThat(LogSanitizer.redactJdbcUrl(url))
                .isEqualTo("jdbc:sqlserver://host:1433;databaseName=db;user=***;password=***;encrypt=true");
    }

    @Test
    void masks_oracle_inline_user_password() {
        assertThat(LogSanitizer.redactJdbcUrl("jdbc:oracle:thin:scott/tiger@//host:1521/orcl"))
                .isEqualTo("jdbc:oracle:thin:***@//host:1521/orcl");
        assertThat(LogSanitizer.redactJdbcUrl("jdbc:oracle:oci8:scott/tiger@host:1521/orcl"))
                .isEqualTo("jdbc:oracle:oci8:***@host:1521/orcl");
    }

    @Test
    void keeps_url_without_credentials_intact() {
        String url = "jdbc:postgresql://host:5432/db?ApplicationName=upsert&currentSchema=public";

        assertThat(LogSanitizer.redactJdbcUrl(url)).isEqualTo(url);
    }

    @Test
    void drops_control_chars_that_could_forge_extra_log_lines() {
        assertThat(LogSanitizer.redactJdbcUrl("jdbc:mysql://host/db\nERROR: forged line"))
                .doesNotContain("\n", "\r");
    }

    @Test
    void null_stays_null() {
        assertThat(LogSanitizer.redactJdbcUrl(null)).isNull();
    }
}
