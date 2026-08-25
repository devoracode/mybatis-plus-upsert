package io.github.devoracode.upsert.autoconfigure;

import io.github.devoracode.upsert.core.fill.FillStrategy;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 单数据源 upsert 支持的配置属性。
 *
 * <p>这些属性绑定到 {@code mybatis-plus.upsert} 前缀下。
 * {@code db-type} 属性为可选 —— 如果未指定，库会尝试从 JDBC URL 自动推断。
 *
 * @author devoracode
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "mybatis-plus.upsert")
public class UpsertProperties {

    /**
     * 是否启用 upsert 支持。默认为 true。
     */
    private boolean enabled = true;

    /**
     * 数据库类型（例如："mysql"、"postgresql"）。可选 —— 如果未指定，则从 JDBC URL 自动推断。
     */
    private String dbType;

    /**
     * 是否在 MySQL upsert 中使用新的 MySQL 8.0.20+ 语法（AS 别名）。
     * 仅在数据库类型为 MySQL 时生效。默认为 false（使用旧版 VALUES() 语法）。
     */
    private boolean useNewMysqlSyntax = false;

    /**
     * 在 upsert SQL 绑定前应用的自动填充策略。
     * 默认解析为 {@code insert_update}（参见 {@link #resolveFillStrategy()}）。
     *
     * @since 1.6.0
     */
    private FillStrategy fillStrategy;

    /**
     * 解析有效的填充策略：显式配置的
     * {@link #fillStrategy} 优先；未配置时默认返回
     * {@link FillStrategy#INSERT_UPDATE}。
     *
     * @return 有效的填充策略
     * @since 1.6.0
     */
    public FillStrategy resolveFillStrategy() {
        return fillStrategy != null ? fillStrategy : FillStrategy.INSERT_UPDATE;
    }
}