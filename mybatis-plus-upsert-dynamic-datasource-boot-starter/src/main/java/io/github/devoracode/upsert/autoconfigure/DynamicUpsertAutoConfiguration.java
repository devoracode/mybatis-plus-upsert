package io.github.devoracode.upsert.autoconfigure;

import com.baomidou.dynamic.datasource.spring.boot.autoconfigure.DynamicDataSourceAutoConfiguration;
import com.baomidou.dynamic.datasource.spring.boot.autoconfigure.DynamicDataSourceProperties;
import com.baomidou.dynamic.datasource.creator.DataSourceProperty;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import io.github.devoracode.upsert.dialect.DynamicUpsertDialect;
import io.github.devoracode.upsert.dialect.UpsertDialect;
import io.github.devoracode.upsert.exception.UpsertException;
import io.github.devoracode.upsert.injector.UpsertSqlInjector;
import io.github.devoracode.upsert.util.DialectFactory;
import io.github.devoracode.upsert.util.DbTypeDetector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 动态数据源 upsert 支持的自动配置。
 *
 * <p>自动为 {@code spring.datasource.dynamic.datasource} 中定义的每个数据源注册
 * {@link UpsertDialect}。如果未显式配置，则从 JDBC URL 推断数据库类型。支持通过
 * {@code db-type=custom} 和 {@code dialect-ref} 配置自定义方言 Bean。
 *
 * @author devoracode
 * @since 1.2.0
 */
@Configuration
@ConditionalOnProperty(prefix = "mybatis-plus.upsert.dynamic", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(value = {UpsertDynamicProperties.class})
@AutoConfigureBefore(MybatisPlusAutoConfiguration.class)
@AutoConfigureAfter(DynamicDataSourceAutoConfiguration.class)
@Slf4j
public class DynamicUpsertAutoConfiguration {

    private final UpsertDynamicProperties upsertDynamicProperties;
    private final DynamicDataSourceProperties dynamicDataSourceProperties;
    private final ConfigurableListableBeanFactory beanFactory;

    /**
     * 创建新的 DynamicUpsertAutoConfiguration 实例。
     *
     * @param properties upsert 动态配置属性
     * @param dynamicDataSourceProperties 动态数据源属性
     * @param beanFactory Spring Bean 工厂（用于解析自定义方言 Bean）
     */
    public DynamicUpsertAutoConfiguration(UpsertDynamicProperties properties,
                                          DynamicDataSourceProperties dynamicDataSourceProperties,
                                          ConfigurableListableBeanFactory beanFactory) {
        this.upsertDynamicProperties = properties;
        this.dynamicDataSourceProperties = dynamicDataSourceProperties;
        this.beanFactory = beanFactory;
    }

    /**
     * 创建 {@link DynamicUpsertDialect} Bean。
     *
     * <p>遍历 {@code spring.datasource.dynamic.datasource} 中定义的所有数据源，
     * 推断或使用配置的数据库类型，并注册相应的方言。
     *
     * @return DynamicUpsertDialect 实例
     */
    @Bean
    @ConditionalOnMissingBean(DynamicUpsertDialect.class)
    public DynamicUpsertDialect dynamicUpsertDialect() {
        DynamicUpsertDialectImpl dynamicDialect = new DynamicUpsertDialectImpl();

        Map<String, DataSourceProperty> allDatasources = dynamicDataSourceProperties.getDatasource();
        if (allDatasources == null || allDatasources.isEmpty()) {
            throw new UpsertException("No data sources configured in spring.datasource.dynamic.datasource");
        }

        Map<String, UpsertDynamicProperties.DataSourceConfig> upsertConfigs = upsertDynamicProperties.getDatasource();
        if (upsertConfigs == null) {
            upsertConfigs = java.util.Collections.emptyMap();
        }

        for (Map.Entry<String, DataSourceProperty> entry : allDatasources.entrySet()) {
            String dsName = entry.getKey();
            DataSourceProperty dsProp = entry.getValue();

            UpsertDynamicProperties.DataSourceConfig upsertConfig = upsertConfigs.get(dsName);

            DbTypeDetector.DbType dbType;
            if (upsertConfig != null && StringUtils.hasText(upsertConfig.getDbType())) {
                dbType = DbTypeDetector.tryParseDbType(upsertConfig.getDbType());
                if (dbType == DbTypeDetector.DbType.UNKNOWN) {
                    throw new UpsertException("Unknown db-type '" + upsertConfig.getDbType() + "' for data source '" + dsName + "'");
                }
            } else {
                String url = dsProp.getUrl();
                dbType = DbTypeDetector.parseDbTypeByJdbcUrl(url);
                if (dbType == DbTypeDetector.DbType.UNKNOWN) {
                    throw new UpsertException("Cannot infer db-type from JDBC URL '" + url + "' for data source '" + dsName
                            + "'. Please configure db-type explicitly in mybatis-plus.upsert.dynamic.datasource." + dsName);
                }
            }

            boolean useNewMysqlSyntax = upsertDynamicProperties.isUseNewMysqlSyntax();
            if (upsertConfig != null && upsertConfig.getUseNewMysqlSyntax() != null) {
                // 只有显式声明了该开关的数据源配置才覆盖全局；未声明时继承全局值
                useNewMysqlSyntax = upsertConfig.getUseNewMysqlSyntax();
            }

            UpsertDialect dialect = resolveDialect(dsName, upsertConfig, dbType, useNewMysqlSyntax);
            dynamicDialect.addDialect(dsName, dialect);
            log.info("Registered upsert dialect {} for data source '{}'", dialect.getClass().getSimpleName(), dsName);
        }

        for (String configuredDs : upsertConfigs.keySet()) {
            if (!allDatasources.containsKey(configuredDs)) {
                log.warn("Upsert configuration exists for data source '{}' but it is not configured in spring.datasource.dynamic.datasource", configuredDs);
            }
        }

        if (dynamicDialect.getDialectMap().isEmpty()) {
            throw new UpsertException("No valid upsert dialects were registered from datasource configurations");
        }
        String primaryDs = dynamicDataSourceProperties.getPrimary();
        if (!dynamicDialect.getDialectMap().containsKey(primaryDs)) {
            throw new UpsertException("Primary data source '" + primaryDs
                    + "' is not configured for upsert. Available upsert data sources: " + dynamicDialect.getDialectMap().keySet());
        }
        dynamicDialect.setPrimary(dynamicDataSourceProperties.getPrimary());
        return dynamicDialect;
    }

    /**
     * 为指定数据源解析 {@link UpsertDialect}。
     *
     * @param dsName 数据源名称
     * @param config 按数据源配置的参数（可能为 null）
     * @param dbType 检测到的或配置的数据库类型
     * @param useNewMysqlSyntax 是否使用 MySQL 8.0.19+ 引入的新语法
     * @return 解析出的 UpsertDialect 实例
     * @throws UpsertException 如果无法解析方言
     */
    public UpsertDialect resolveDialect(String dsName,
                                        UpsertDynamicProperties.DataSourceConfig config,
                                        DbTypeDetector.DbType dbType,
                                        boolean useNewMysqlSyntax) {
        if (dbType == DbTypeDetector.DbType.CUSTOM) {
            if (config == null) {
                throw new UpsertException("Data source '" + dsName + "' requires custom dialect configuration");
            }
            String ref = config.getDialectRef();
            if (!StringUtils.hasText(ref)) {
                throw new UpsertException("Data source '" + dsName + "' uses db-type=custom but no dialect-ref is configured. "
                        + "Provide a dialect-ref pointing to a user-defined UpsertDialect bean.");
            }
            if (!beanFactory.containsBean(ref)) {
                throw new UpsertException("Data source '" + dsName + "': dialect-ref '" + ref + "' not found in Spring container. "
                        + "Ensure an UpsertDialect bean with that name exists (e.g. @Component(\"" + ref + "\")).");
            }
            Object bean = beanFactory.getBean(ref);
            if (!(bean instanceof UpsertDialect)) {
                throw new UpsertException("Data source '" + dsName + "': bean '" + ref + "' (type " + bean.getClass().getName()
                        + ") does not implement UpsertDialect.");
            }
            return (UpsertDialect) bean;
        }
        UpsertDialect dialect = DialectFactory.create(dbType, useNewMysqlSyntax);
        if (dialect == null) {
            String dbTypeName = dbType.name().toLowerCase();
            throw new UpsertException("Failed to create upsert dialect for db-type '" + dbTypeName
                    + "' on data source '" + dsName + "'");
        }
        return dialect;
    }

    /**
     * 创建带有动态方言的 {@link UpsertSqlInjector} Bean。
     *
     * <p>解析的填充策略（来自 {@code fill-strategy}）
     * 由注入器携带，以便注入的 upsert
     * SqlSources 在动态 SQL 绑定之前、方言路由之前应用自动填充——
     * 不需要注册全局 MyBatis 拦截器。
     *
     * <p>按类型（{@code ISqlInjector}）做缺失检查，与单数据源 starter 的
     * {@code UpsertAutoConfiguration#upsertSqlInjector} 保持对称：
     * 用户已注册任何自定义 {@code ISqlInjector} bean 时（无论 bean 名是什么）
     * 都不再自动注册，避免两个注入器并存。
     *
     * @param dynamicDialect 动态 upsert 方言
     * @return 配置好的 UpsertSqlInjector
     */
    @Bean
    @ConditionalOnMissingBean(com.baomidou.mybatisplus.core.injector.ISqlInjector.class)
    public UpsertSqlInjector upsertSqlInjector(DynamicUpsertDialect dynamicDialect) {
        log.info("Registering UpsertSqlInjector with DynamicUpsertDialect");
        return new UpsertSqlInjector(dynamicDialect, upsertDynamicProperties.resolveFillStrategy());
    }
}
