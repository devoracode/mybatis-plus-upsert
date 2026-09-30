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
import io.github.devoracode.upsert.util.LogSanitizer;
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

import java.util.Collections;
import java.util.Map;

/**
 * 动态数据源 upsert 支持的自动配置。多数据源路由、方言注册与自定义方言的用法见 README「多数据源支持」一节。
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

    public DynamicUpsertAutoConfiguration(UpsertDynamicProperties properties,
                                          DynamicDataSourceProperties dynamicDataSourceProperties,
                                          ConfigurableListableBeanFactory beanFactory) {
        this.upsertDynamicProperties = properties;
        this.dynamicDataSourceProperties = dynamicDataSourceProperties;
        this.beanFactory = beanFactory;
    }

    /**
     * 为 {@code spring.datasource.dynamic.datasource} 里的每个数据源推断（或按配置取用）数据库类型并注册方言，随后校验主数据源也在其中。
     *
     * <p>该配置为空时不阻塞启动：数据源也可能由 {@code DynamicDataSourceProvider} 从配置中心下发、
     * 或运行期 {@code addDataSource} 加入，这些来源在启动期拿不到 JDBC URL，无法推断 db-type。
     *
     * @return DynamicUpsertDialect 实例
     */
    @Bean
    @ConditionalOnMissingBean(DynamicUpsertDialect.class)
    public DynamicUpsertDialect dynamicUpsertDialect() {
        DynamicUpsertDialectImpl dynamicDialect = new DynamicUpsertDialectImpl();

        Map<String, DataSourceProperty> allDatasources = dynamicDataSourceProperties.getDatasource();
        if (allDatasources == null || allDatasources.isEmpty()) {
            dynamicDialect.setPrimary(dynamicDataSourceProperties.getPrimary());
            log.warn("No data sources declared in spring.datasource.dynamic.datasource. If your data sources come from a"
                    + " DynamicDataSourceProvider or are added at runtime, register their upsert dialects with"
                    + " DynamicUpsertDialect.addDialect(name, dialect) before calling upsert.");
            return dynamicDialect;
        }

        Map<String, UpsertDynamicProperties.DataSourceConfig> upsertConfigs = upsertDynamicProperties.getDatasource();
        if (upsertConfigs == null) {
            upsertConfigs = java.util.Collections.emptyMap();
        }

        for (Map.Entry<String, DataSourceProperty> entry : allDatasources.entrySet()) {
            String dsName = entry.getKey();
            UpsertDynamicProperties.DataSourceConfig upsertConfig = upsertConfigs.get(dsName);

            DbTypeDetector.DbType dbType = inferDbType(dsName, entry.getValue(), upsertConfig);
            UpsertDialect dialect = resolveDialect(dsName, upsertConfig, dbType,
                    resolveUseNewMysqlSyntax(upsertConfig), upsertDynamicProperties.isSqlserverHoldlock());
            dynamicDialect.addDialect(dsName, dialect);
            log.info("Registered upsert dialect {} for data source '{}'", dialect.getClass().getSimpleName(), dsName);
        }

        for (String configuredDs : upsertConfigs.keySet()) {
            if (!allDatasources.containsKey(configuredDs)) {
                log.warn("Upsert configuration exists for data source '{}' but it is not configured in spring.datasource.dynamic.datasource", configuredDs);
            }
        }

        String primaryDs = dynamicDataSourceProperties.getPrimary();
        if (!dynamicDialect.getDialectMap().containsKey(primaryDs)) {
            throw new UpsertException("Primary data source '" + primaryDs
                    + "' is not configured for upsert. Available upsert data sources: " + dynamicDialect.getDialectMap().keySet());
        }
        dynamicDialect.setPrimary(primaryDs);
        return dynamicDialect;
    }

    /**
     * 推断单个数据源的数据库类型：显式配置的 {@code db-type} 优先，否则从 JDBC URL 推断。
     *
     * @param config 该数据源的 upsert 配置，可为 null
     * @throws UpsertException 显式配置无法识别，或 JDBC URL 无法推断
     */
    private DbTypeDetector.DbType inferDbType(String dsName, DataSourceProperty dsProp,
                                              UpsertDynamicProperties.DataSourceConfig config) {
        if (config != null && StringUtils.hasText(config.getDbType())) {
            DbTypeDetector.DbType dbType = DbTypeDetector.tryParseDbType(config.getDbType());
            if (dbType == DbTypeDetector.DbType.UNKNOWN) {
                throw new UpsertException("Unknown db-type '" + config.getDbType() + "' for data source '" + dsName + "'");
            }
            return dbType;
        }
        String url = dsProp.getUrl();
        DbTypeDetector.DbType dbType = DbTypeDetector.parseDbTypeByJdbcUrl(url);
        if (dbType == DbTypeDetector.DbType.UNKNOWN) {
            // URL 常带账号口令，回显前脱敏；异常会进启动日志与 CI 产物
            throw new UpsertException("Cannot infer db-type from JDBC URL '" + LogSanitizer.redactJdbcUrl(url)
                    + "' for data source '" + dsName
                    + "'. Please configure db-type explicitly in mybatis-plus.upsert.dynamic.datasource." + dsName
                    + ". If the database has no built-in dialect yet, use db-type=custom with a dialect-ref"
                    + " pointing at your own UpsertDialect bean.");
        }
        return dbType;
    }

    /**
     * 解析 MySQL 语法开关：只有显式声明该开关的数据源才覆盖全局，未声明（null）时继承全局值。
     */
    private boolean resolveUseNewMysqlSyntax(UpsertDynamicProperties.DataSourceConfig config) {
        if (config != null && config.getUseNewMysqlSyntax() != null) {
            return config.getUseNewMysqlSyntax();
        }
        return upsertDynamicProperties.isUseNewMysqlSyntax();
    }

    /**
     * 解析单个数据源的方言：{@code dbType} 为 CUSTOM 时按 {@code dialect-ref} 从容器取用户 Bean，
     * 否则用 {@link DialectFactory} 创建内置方言。
     *
     * @param config 该数据源的 upsert 配置，可为 null
     * @throws UpsertException custom 缺少 dialect-ref、引用的 Bean 不存在或类型不符，
     *         或内置类型无法创建方言
     */
    public UpsertDialect resolveDialect(String dsName,
                                        UpsertDynamicProperties.DataSourceConfig config,
                                        DbTypeDetector.DbType dbType,
                                        boolean useNewMysqlSyntax,
                                        boolean sqlserverHoldlock) {
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
        return DialectFactory.create(dbType, useNewMysqlSyntax, sqlserverHoldlock);
    }

    /**
     * 注册携带动态方言与 {@code fill-strategy} 的 {@link UpsertSqlInjector}。
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
