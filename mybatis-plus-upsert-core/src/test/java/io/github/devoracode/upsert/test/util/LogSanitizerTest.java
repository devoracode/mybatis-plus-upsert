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

    /** PostgreSQL URI 的标准写法：凭据在 userinfo 段，不带 key=value。 */
    @Test
    void masks_uri_userinfo_password() {
        assertThat(LogSanitizer.redactJdbcUrl("jdbc:postgresql://appuser:s3cret@db.internal:5432/orders"))
                .isEqualTo("jdbc:postgresql://appuser:***@db.internal:5432/orders");
        assertThat(LogSanitizer.redactJdbcUrl("jdbc:postgresql://appuser@db.internal:5432/orders"))
                .isEqualTo("jdbc:postgresql://appuser@db.internal:5432/orders");
    }

    /**
     * 口令里的 {@code @} 未转义时只遮住第一段——这是已知且刻意保留的行为。
     *
     * <p>userinfo 规则的口令部分不允许出现 {@code @}，因此匹配停在第一个 {@code @}。
     * 这种 URL 本身不是合法 URI（口令中的 {@code @} 应写成 {@code %40}），真实驱动也解析不了，
     * 为它把规则改成"遮到最后一个 @"反而会误伤 {@code user@host} 这类无口令写法。
     * 用例锁住现状：哪天有人改这条规则，这里会先红。
     */
    @Test
    void userinfo_password_with_unescaped_at_sign_is_only_partially_masked() {
        assertThat(LogSanitizer.redactJdbcUrl("jdbc:postgresql://alice:p@ss@db/mydb"))
                .isEqualTo("jdbc:postgresql://alice:***@ss@db/mydb");
    }

    @Test
    void masks_pass_named_parameter() {
        assertThat(LogSanitizer.redactJdbcUrl("jdbc:oracle:thin:@//host:1521/orcl?pass=s3cret&user=scott"))
                .isEqualTo("jdbc:oracle:thin:***@//host:1521/orcl?pass=***&user=***");
    }

    /** 口令里的 '@' 不能把 userinfo 规则带偏：key=value 规则必须先跑。 */
    @Test
    void password_containing_at_sign_is_masked_as_a_whole() {
        assertThat(LogSanitizer.redactJdbcUrl("jdbc:sqlserver://host:1433;user=sa;password=p@ss"))
                .isEqualTo("jdbc:sqlserver://host:1433;user=***;password=***");
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
        // U+2028 / U+2029 多数日志采集与终端按换行处理，同样能伪造日志行
        assertThat(LogSanitizer.redactJdbcUrl("jdbc:mysql://host/db\u2028ERROR: forged\u2029end"))
                .doesNotContain("\u2028", "\u2029");
    }

    @Test
    void null_stays_null() {
        assertThat(LogSanitizer.redactJdbcUrl(null)).isNull();
    }
}
