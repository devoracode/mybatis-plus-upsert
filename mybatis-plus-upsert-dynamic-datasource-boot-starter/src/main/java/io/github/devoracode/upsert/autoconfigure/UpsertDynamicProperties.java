package io.github.devoracode.upsert.autoconfigure;

import io.github.devoracode.upsert.core.fill.FillStrategy;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * 动态数据源 upsert 支持的配置属性。
 *
 * <p>这些属性绑定到 {@code mybatis-plus.upsert.dynamic} 前缀。
 * 按数据源的 {@code db-type} 是可选的——如果未指定，库会尝试从
 * {@code spring.datasource.dynamic.datasource} 中定义的 JDBC URL 自动推断。
 *
 * @author devoracode
 * @since 1.2.0
 */
@Data
@ConfigurationProperties(prefix = "mybatis-plus.upsert.dynamic")
public class UpsertDynamicProperties {

    /**
     * 是否启用动态 upsert 支持。默认为 true。
     */
    private boolean enabled = true;

    /**
     * MySQL 语法风格的默认全局设置。应用于所有 MySQL 数据源中未在
     * 按数据源级别覆盖此设置的数据源。默认为 false。
     */
    private boolean useNewMysqlSyntax = false;

    /**
     * 在 upsert SQL 绑定之前应用的自动填充策略。
     * 默认解析为 {@code insert_update}（参见 {@link #resolveFillStrategy()}）。
     *
     * @since 1.6.0
     */
    private FillStrategy fillStrategy;

    /**
     * 按数据源的 upsert 配置映射。
     * 键与 {@code spring.datasource.dynamic.datasource} 中的数据源名称匹配。
     */
    private Map<String, DataSourceConfig> datasource = new HashMap<>();

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

    /**
     * 按数据源的 upsert 配置。
     *
     * @author devoracode
     * @since 1.2.0
     */
    @Data
    public static class DataSourceConfig {
        /**
         * 此数据源的数据库类型。可选——如果未指定，则从 JDBC URL 自动推断。
         */
        private String dbType;
        /**
         * 此数据源是否使用 MySQL 8.0.19+ 引入的新语法（AS 别名）。
         * 未显式配置（{@code null}）时继承全局 {@code use-new-mysql-syntax}；
         * 显式声明为 true / false 时覆盖全局设置。
         */
        private Boolean useNewMysqlSyntax;
        /**
         * 当 {@code db-type} 设置为 {@code custom} 时使用的用户自定义
         * {@code UpsertDialect} Bean 名称。对于内置数据库类型无效。
         */
        private String dialectRef;
    }
}