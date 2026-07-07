package io.github.devoracode.upsert.autoconfigure;

import com.baomidou.dynamic.datasource.spring.boot.autoconfigure.DynamicDataSourceAutoConfiguration;
import com.baomidou.dynamic.datasource.spring.boot.autoconfigure.DynamicDataSourceProperties;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import io.github.devoracode.upsert.dialect.DynamicUpsertDialect;
import io.github.devoracode.upsert.dialect.UpsertDialect;
import io.github.devoracode.upsert.exception.UpsertException;
import io.github.devoracode.upsert.injector.UpsertSqlInjector;
import io.github.devoracode.upsert.util.DialectFactory;
import io.github.devoracode.upsert.util.DbTypeDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class DynamicUpsertAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DynamicUpsertAutoConfiguration.class);

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

        Map<String, UpsertDynamicProperties.DataSourceConfig> datasourceConfigs = upsertDynamicProperties.getDatasource();
        if (datasourceConfigs == null || datasourceConfigs.isEmpty()) {
            throw new UpsertException("No datasource configurations found for mybatis-plus.upsert.dynamic.datasource");
        }

        for (Map.Entry<String, UpsertDynamicProperties.DataSourceConfig> entry : datasourceConfigs.entrySet()) {
            String dsName = entry.getKey();
            UpsertDynamicProperties.DataSourceConfig config = entry.getValue();

            if (!StringUtils.hasText(config.getDbType())) {
                throw new UpsertException("No db-type configured for data source '" + dsName + "'");
            }

            DbTypeDetector.DbType dbType = DbTypeDetector.tryParseDbType(config.getDbType());
            if (dbType == DbTypeDetector.DbType.UNKNOWN) {
                throw new UpsertException("Unknown db-type '" + config.getDbType() + "' for data source '" + dsName + "'");
            }

            UpsertDialect dialect = resolveDialect(dsName, config, dbType);
            dynamicDialect.addDialect(dsName, dialect);
            log.info("Registered upsert dialect {} for data source '{}'", dialect.getClass().getSimpleName(), dsName);
        }

        if (dynamicDialect.getDialectMap().isEmpty()) {
            throw new UpsertException("No valid upsert dialects were registered from datasource configurations");
        }

        String primaryDsName = dynamicDataSourceProperties.getPrimary();
        UpsertDialect primary = dynamicDialect.getDialectMap().get(primaryDsName);

        if (primary == null) {
            throw new UpsertException("No upsert dialect registered for primary data source '" + primaryDsName + "'");
        }

        dynamicDialect.setPrimaryDialect(primary);
        log.info("Using {} as primary dialect", primary.getClass().getSimpleName());

        return dynamicDialect;
    }

    /**
     * Resolve the {@link UpsertDialect} for one data source.
     *
     * <p>Built-in db types are instantiated by {@link DialectFactory}. When
     * {@code db-type} is {@code custom}, the dialect is fetched from the Spring
     * container by the bean name configured via {@code dialect-ref}.
     */
    public UpsertDialect resolveDialect(String dsName,
                                        UpsertDynamicProperties.DataSourceConfig config,
                                        DbTypeDetector.DbType dbType) {
        if (dbType == DbTypeDetector.DbType.CUSTOM) {
            String ref = config.getDialectRef();
            if (!StringUtils.hasText(ref)) {
                throw new UpsertException("Data source '" + dsName
                        + "' uses db-type=custom but no dialect-ref is configured. "
                        + "Provide a dialect-ref pointing to a user-defined UpsertDialect bean.");
            }
            if (!beanFactory.containsBean(ref)) {
                throw new UpsertException("Data source '" + dsName
                        + "': dialect-ref '" + ref + "' not found in Spring container. "
                        + "Ensure an UpsertDialect bean with that name exists (e.g. @Component(\"" + ref + "\")).");
            }
            Object bean = beanFactory.getBean(ref);
            if (!(bean instanceof UpsertDialect)) {
                throw new UpsertException("Data source '" + dsName
                        + "': bean '" + ref + "' (type " + bean.getClass().getName()
                        + ") does not implement UpsertDialect.");
            }
            return (UpsertDialect) bean;
        }
        UpsertDialect dialect = DialectFactory.create(dbType, config.isUseNewMysqlSyntax());
        if (dialect == null) {
            throw new UpsertException("Failed to create upsert dialect for db-type '" + config.getDbType()
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
