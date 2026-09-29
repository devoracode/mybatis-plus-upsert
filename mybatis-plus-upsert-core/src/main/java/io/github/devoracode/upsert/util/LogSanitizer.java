package io.github.devoracode.upsert.util;

import java.util.regex.Pattern;

/**
 * 把 JDBC URL 写进日志或异常消息前的脱敏工具。URL 允许把账号口令写在参数里，
 * 原样回显会把凭据带进日志、异常栈与 CI 产物。
 *
 * @author devoracode
 * @since 1.7.1
 */
public final class LogSanitizer {

    private static final String MASK = "***";

    /** 凭据类参数名，命中即把取值整体替换；分隔符同时覆盖 {@code ?}、{@code &}、{@code ;} 三种 URL 写法。 */
    private static final Pattern CREDENTIAL_PARAM =
            Pattern.compile("(?i)([?&;])(password|passwd|pwd|user|username|secret|token)=[^&;]*");

    /** Oracle 的 {@code user/password@host} 写法没有 key=value，凭据直接嵌在路径里。 */
    private static final Pattern ORACLE_INLINE_CREDENTIAL =
            Pattern.compile("(?i)(jdbc:oracle:(?:thin|oci|oci8):)([^@]*)@");

    private LogSanitizer() {
    }

    /**
     * 脱敏 JDBC URL：凭据类参数与 Oracle 内联凭据的取值替换为 {@code ***}，并去掉控制字符，
     * 其余部分原样保留。
     *
     * @param url JDBC URL，可为 null
     * @return 可安全写入日志的 URL；{@code url} 为 null 时返回 null
     */
    public static String redactJdbcUrl(String url) {
        if (url == null) {
            return null;
        }
        String sanitized = stripControlChars(url);
        sanitized = ORACLE_INLINE_CREDENTIAL.matcher(sanitized).replaceAll("$1" + MASK + "@");
        return CREDENTIAL_PARAM.matcher(sanitized).replaceAll("$1$2=" + MASK);
    }

    /** 去掉控制字符，避免回显内容里的换行把一条日志伪装成多条。 */
    private static String stripControlChars(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= ' ' && c != 0x7F) {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
