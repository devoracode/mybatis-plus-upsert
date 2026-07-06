package io.github.devoracode.upsert.autoconfigure;

import com.baomidou.dynamic.datasource.spring.boot.autoconfigure.DynamicDataSourceProperties;
import io.github.devoracode.upsert.dialect.DynamicUpsertDialect;
import io.github.devoracode.upsert.dialect.UpsertDialect;
import io.github.devoracode.upsert.exception.UpsertException;
import io.github.devoracode.upsert.injector.UpsertSqlInjector;
import io.github.devoracode.upsert.util.DialectFactory;
import io.github.devoracode.upsert.util.DbTypeDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
@AutoConfigureBefore(name = "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration")
@AutoConfigureAfter(name = "com.baomidou.dynamic.datasource.spring.boot.autoconfigure.DruidDynamicDataSourceConfiguration")
@ConditionalOnProperty(prefix = "mybatis-plus.upsert.dynamic", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(value = {UpsertDynamicProperties.class})
public class DynamicUpsertAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DynamicUpsertAutoConfiguration.class);

    private final UpsertDynamicProperties upsertDynamicProperties;
    private final DynamicDataSourceProperties dynamicDataSourceProperties;

    public DynamicUpsertAutoConfiguration(UpsertDynamicProperties properties, DynamicDataSourceProperties dynamicDataSourceProperties) {
        this.upsertDynamicProperties = properties;
        this.dynamicDataSourceProperties = dynamicDataSourceProperties;
    }

    @Bean
    @ConditionalOnMissingBean(DynamicUpsertDialect.class)
    public DynamicUpsertDialect dynamicUpsertDialect() {
        DynamicUpsertDialectImpl dynamicDialect = new DynamicUpsertDialectImpl();

        Map<String, UpsertDynamicProperties.DataSourceConfig> datasourceConfigs = upsertDynamicProperties.getDatasources();
        if (datasourceConfigs == null || datasourceConfigs.isEmpty()) {
            throw new UpsertException("No datasource configurations found for mybatis-plus.upsert.dynamic.datasources");
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

            UpsertDialect dialect = DialectFactory.create(dbType, config.isUseNewMysqlSyntax());

            if (dialect != null) {
                dynamicDialect.addDialect(dsName, dialect);
                log.info("Registered upsert dialect {} for data source '{}'", dialect.getClass().getSimpleName(), dsName);
            } else {
                throw new UpsertException("Failed to create upsert dialect for db-type '" + config.getDbType() + "'");
            }
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

    @Bean
    @ConditionalOnMissingBean(name = "sqlInjector")
    public UpsertSqlInjector upsertSqlInjector(DynamicUpsertDialect dynamicDialect) {
        log.info("Registering UpsertSqlInjector with DynamicUpsertDialect");
        return new UpsertSqlInjector(dynamicDialect);
    }
}
