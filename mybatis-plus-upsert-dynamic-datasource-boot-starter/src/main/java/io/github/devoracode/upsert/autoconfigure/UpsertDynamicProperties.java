package io.github.devoracode.upsert.autoconfigure;

import io.github.devoracode.upsert.core.fill.FillStrategy;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * {@code mybatis-plus.upsert.dynamic} 前缀下的配置属性。
 *
 * <p>按数据源的 {@code db-type} 可省略，此时从
 * {@code spring.datasource.dynamic.datasource} 里该数据源的 JDBC URL 推断。
 *
 * @author devoracode
 * @since 1.2.0
 */
@Data
@ConfigurationProperties(prefix = "mybatis-plus.upsert.dynamic")
public class UpsertDynamicProperties {

    /** 是否启用动态 upsert 支持，默认 true。 */
    private boolean enabled = true;

    /** MySQL 语法风格的全局默认值（各数据源未覆盖时生效），默认 false。 */
    private boolean useNewMysqlSyntax = false;

    /**
     * SQL 绑定前应用的自动填充策略；未配置时由 {@link #resolveFillStrategy()} 给出默认值。
     *
     * @since 1.6.0
     */
    private FillStrategy fillStrategy;

    /** 按数据源的配置，键为 {@code spring.datasource.dynamic.datasource} 中的数据源名。 */
    private Map<String, DataSourceConfig> datasource = new HashMap<>();

    /**
     * @return 显式配置的 {@link #fillStrategy}，未配置时为 {@link FillStrategy#INSERT_UPDATE}
     * @since 1.6.0
     */
    public FillStrategy resolveFillStrategy() {
        return fillStrategy != null ? fillStrategy : FillStrategy.INSERT_UPDATE;
    }

    /**
     * 单个数据源的 upsert 配置。
     *
     * @author devoracode
     * @since 1.2.0
     */
    @Data
    public static class DataSourceConfig {
        /** 该数据源的数据库类型；未配置时从其 JDBC URL 推断。 */
        private String dbType;
        /**
         * 该数据源的 MySQL 是否使用 8.0.19+ 的 AS 别名语法。
         * 为 {@code null}（未配置）时继承全局 {@code use-new-mysql-syntax}，显式声明则覆盖。
         */
        private Boolean useNewMysqlSyntax;
        /**
         * {@code db-type} 为 {@code custom} 时引用的用户自定义 {@code UpsertDialect} Bean 名；
         * 对内置数据库类型无效。
         */
        private String dialectRef;
    }
}