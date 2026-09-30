package io.github.devoracode.upsert.util;

import java.util.regex.Pattern;

/**
 * 把 JDBC URL 写进日志或异常消息前的脱敏工具。URL 允许把账号口令写在参数里或
 * userinfo 段里，原样回显会把凭据带进日志、异常栈与 CI 产物。
 *
 * <p>约定：<b>任何 JDBC URL 进入日志或异常消息前都必须经过 {@link #redactJdbcUrl}</b>，
 * 临时排查用的 log 语句同样适用——凭据一旦落到日志里就只能靠轮转日志来清理。
 *
 * @author devoracode
 * @since 1.7.1
 */
public final class LogSanitizer {

    private static final String MASK = "***";

    /** Unicode 行/段分隔符：多数日志采集与终端把它们当换行，能伪装出额外的日志行。 */
    private static final char LINE_SEPARATOR = 0x2028;

    private static final char PARAGRAPH_SEPARATOR = 0x2029;

    /** 凭据类参数名，命中即把取值整体替换；分隔符同时覆盖 {@code ?}、{@code &}、{@code ;} 三种 URL 写法。 */
    private static final Pattern CREDENTIAL_PARAM =
            Pattern.compile("(?i)([?&;])(password|passwd|pwd|pass|user|username|secret|token)=[^&;]*");

    /** Oracle 的 {@code user/password@host} 写法没有 key=value，凭据直接嵌在路径里。 */
    private static final Pattern ORACLE_INLINE_CREDENTIAL =
            Pattern.compile("(?i)(jdbc:oracle:(?:thin|oci|oci8):)([^@]*)@");

    /**
     * 通用 userinfo 写法 {@code jdbc:<sub>://user:password@host/db}，PostgreSQL URI 即此形式。
     * 命中范围限定在 authority 段：用户名不允许出现 {@code :}，口令不允许出现
     * {@code /}、{@code @} 与空白，因此 {@code host:port/db} 这类端口写法不会被误伤。
     */
    private static final Pattern URI_USERINFO =
            Pattern.compile("(?i)(jdbc:[a-z0-9]+://)([^/@:\\s]+):([^/@\\s]*)@");

    private LogSanitizer() {
    }

    /**
     * 脱敏 JDBC URL：凭据类参数、Oracle 内联凭据与 userinfo 段的口令替换为 {@code ***}，
     * 并去掉控制字符，其余部分原样保留。
     *
     * @param url JDBC URL，可为 null
     * @return 可安全写入日志的 URL；{@code url} 为 null 时返回 null
     */
    public static String redactJdbcUrl(String url) {
        if (url == null) {
            return null;
        }
        String sanitized = stripControlChars(url);
        // 顺序要紧：key=value 与 Oracle 形式会消掉 userinfo 规则依赖的 '@'，
        // 否则 password=p@ss 这类取值会被 userinfo 规则从中间截断成半截 URL
        sanitized = CREDENTIAL_PARAM.matcher(sanitized).replaceAll("$1$2=" + MASK);
        sanitized = ORACLE_INLINE_CREDENTIAL.matcher(sanitized).replaceAll("$1" + MASK + "@");
        return URI_USERINFO.matcher(sanitized).replaceAll("$1$2:" + MASK + "@");
    }

    /** 去掉控制字符与 Unicode 行分隔符，避免回显内容里的换行把一条日志伪装成多条。 */
    private static String stripControlChars(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (isPrintable(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static boolean isPrintable(char c) {
        if (c < ' ' || c == 0x7F) {
            return false;
        }
        return c != LINE_SEPARATOR && c != PARAGRAPH_SEPARATOR;
    }
}
