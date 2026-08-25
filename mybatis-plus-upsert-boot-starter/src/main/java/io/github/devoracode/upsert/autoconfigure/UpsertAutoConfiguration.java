package io.github.devoracode.upsert.autoconfigure;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import io.github.devoracode.upsert.core.fill.FillStrategy;
import io.github.devoracode.upsert.dialect.UpsertDialect;
import io.github.devoracode.upsert.exception.UpsertException;
import io.github.devoracode.upsert.injector.UpsertSqlInjector;
import io.github.devoracode.upsert.util.DialectFactory;
import io.github.devoracode.upsert.util.DbTypeDetector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * 单数据源 upsert 支持的自动配置类。
 *
 * <p>如果未显式配置，则从 JDBC URL 自动检测数据库类型，
 * 创建相应的 {@link UpsertDialect}，并注册 {@link UpsertSqlInjector}。
 *
 * <p>自动填充（当从 {@code fill-strategy} 解析为非
 * {@link FillStrategy#NONE} 的策略时）在注入的 upsert
 * SqlSources 动态 SQL 绑定之前执行 —— 不会注册全局 MyBatis 拦截器。
 *
 * @author devoracode
 * @since 1.0.0
 */
@Configuration
@EnableConfigurationProperties(UpsertProperties.class)
@ConditionalOnProperty(prefix = "mybatis-plus.upsert", name = "enabled", havingValue = "true", matchIfMissing = true)
@AutoConfigureBefore(MybatisPlusAutoConfiguration.class)
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
@Slf4j
public class UpsertAutoConfiguration {

    private final UpsertProperties properties;
    private final DataSourceProperties dataSourceProperties;

    /**
     * 创建一个新的 UpsertAutoConfiguration 实例。
     *
     * @param properties upsert 配置属性
     * @param dataSourceProperties 数据源属性（用于从 JDBC URL 推断数据库类型）
     */
    public UpsertAutoConfiguration(UpsertProperties properties, DataSourceProperties dataSourceProperties) {
        this.properties = properties;
        this.dataSourceProperties = dataSourceProperties;
    }

    /**
     * 创建 {@link UpsertDialect} Bean。
     *
     * <p>如果配置了 {@code mybatis-plus.upsert.db-type}，则直接使用它。
     * 否则，库会尝试从 JDBC URL 自动推断数据库类型。
     *
     * @return 配置好的 UpsertDialect
     */
    @Bean
    @ConditionalOnMissingBean(UpsertDialect.class)
    @Conditional(ConditionalOnNotCustomDbType.class)
    public UpsertDialect upsertDialect() {
        String dbType = properties.getDbType();
        DbTypeDetector.DbType dbTypeEnum;

        if (StringUtils.hasText(dbType)) {
            dbTypeEnum = DbTypeDetector.tryParseDbType(dbType);
            if (dbTypeEnum == DbTypeDetector.DbType.UNKNOWN) {
                throw new UpsertException("Unknown db-type: " + dbType);
            }
        } else {
            dbTypeEnum = DbTypeDetector.parseDbTypeByJdbcUrl(dataSourceProperties.getUrl());
            if (dbTypeEnum == DbTypeDetector.DbType.UNKNOWN) {
                throw new UpsertException("Cannot infer db-type from data source. Please configure mybatis-plus.upsert.db-type explicitly.");
            }
            log.info("Auto-inferred db-type as '{}' from JDBC URL", dbTypeEnum.name().toLowerCase());
        }

        UpsertDialect dialect = DialectFactory.create(dbTypeEnum, properties.isUseNewMysqlSyntax());
        if (dialect == null) {
            throw new UpsertException("Failed to create upsert dialect for db-type '" + dbTypeEnum.name().toLowerCase() + "'");
        }
        return dialect;
    }

    /**
     * 创建 {@link UpsertSqlInjector} Bean。
     *
     * <p>解析后的填充策略（来自 {@code fill-strategy}）
     * 由注入器携带，以便注入的 upsert
     * SqlSources 在动态 SQL 绑定前应用自动填充。
     *
     * @param dialect 用于 SQL 生成的 upsert 方言
     * @return 配置好的 UpsertSqlInjector
     */
    @Bean
    @ConditionalOnMissingBean(com.baomidou.mybatisplus.core.injector.ISqlInjector.class)
    public UpsertSqlInjector upsertSqlInjector(UpsertDialect dialect) {
        return new UpsertSqlInjector(dialect, properties.resolveFillStrategy());
    }
}
