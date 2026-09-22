package io.github.devoracode.upsert.autoconfigure;

import io.github.devoracode.upsert.core.fill.FillStrategy;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code mybatis-plus.upsert} 前缀下的配置属性。
 *
 * @author devoracode
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "mybatis-plus.upsert")
public class UpsertProperties {

    /** 是否启用 upsert 支持，默认 true。 */
    private boolean enabled = true;

    /** 数据库类型（如 {@code mysql}、{@code postgresql}）；未配置时从 JDBC URL 推断。 */
    private String dbType;

    /** MySQL 是否使用 8.0.19+ 的 AS 别名语法，默认 false（旧版 {@code VALUES()} 语法），仅在数据库类型为 MySQL 时生效。 */
    private boolean useNewMysqlSyntax = false;

    /**
     * SQL 绑定前应用的自动填充策略；未配置时由 {@link #resolveFillStrategy()} 给出默认值。
     *
     * @since 1.6.0
     */
    private FillStrategy fillStrategy;

    /**
     * @return 显式配置的 {@link #fillStrategy}，未配置时为 {@link FillStrategy#INSERT_UPDATE}
     * @since 1.6.0
     */
    public FillStrategy resolveFillStrategy() {
        return fillStrategy != null ? fillStrategy : FillStrategy.INSERT_UPDATE;
    }
}