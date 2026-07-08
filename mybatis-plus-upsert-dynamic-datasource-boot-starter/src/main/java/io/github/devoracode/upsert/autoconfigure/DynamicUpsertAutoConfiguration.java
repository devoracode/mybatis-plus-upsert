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
            if (upsertConfig != null) {
                useNewMysqlSyntax = upsertConfig.isUseNewMysqlSyntax();
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

    @Bean
    @ConditionalOnMissingBean(name = "sqlInjector")
    public UpsertSqlInjector upsertSqlInjector(DynamicUpsertDialect dynamicDialect) {
        log.info("Registering UpsertSqlInjector with DynamicUpsertDialect");
        return new UpsertSqlInjector(dynamicDialect);
    }
}
