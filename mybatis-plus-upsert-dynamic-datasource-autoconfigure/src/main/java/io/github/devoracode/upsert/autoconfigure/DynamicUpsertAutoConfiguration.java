package io.github.devoracode.upsert.autoconfigure;

import io.github.devoracode.upsert.dialect.DynamicUpsertDialect;
import io.github.devoracode.upsert.dialect.UpsertDialect;
import io.github.devoracode.upsert.injector.UpsertSqlInjector;
import io.github.devoracode.upsert.util.DialectFactory;
import io.github.devoracode.upsert.util.DbTypeDetector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * Auto-configuration for dynamic datasource upsert support.
 * Must be loaded before MybatisPlusAutoConfiguration to register UpsertSqlInjector.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@AutoConfigureBefore(name = "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration")
@ConditionalOnProperty(prefix = "mybatis-plus.upsert.dynamic", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DynamicUpsertAutoConfiguration {

    private final UpsertDynamicProperties properties;

    @Bean
    @ConditionalOnMissingBean(DynamicUpsertDialect.class)
    public DynamicUpsertDialect dynamicUpsertDialect() {
        DynamicUpsertDialectImpl dynamicDialect = new DynamicUpsertDialectImpl();

        Map<String, UpsertDynamicProperties.DataSourceConfig> datasourceConfigs = properties.getDatasources();
        if (datasourceConfigs == null || datasourceConfigs.isEmpty()) {
            log.warn("No datasource configurations found for mybatis-plus.upsert.dynamic.datasources, using default dialect");
        } else {
            // Create dialect for each configured data source
            for (Map.Entry<String, UpsertDynamicProperties.DataSourceConfig> entry : datasourceConfigs.entrySet()) {
                String dsName = entry.getKey();
                UpsertDynamicProperties.DataSourceConfig config = entry.getValue();

                if (!StringUtils.hasText(config.getDbType())) {
                    log.warn("No db-type configured for data source '{}', skipping", dsName);
                    continue;
                }

                DbTypeDetector.DbType dbType = DbTypeDetector.tryParseDbType(config.getDbType());
                if (dbType == DbTypeDetector.DbType.UNKNOWN) {
                    log.warn("Unknown db-type '{}' for data source '{}', skipping", config.getDbType(), dsName);
                    continue;
                }

                UpsertDialect dialect = DialectFactory.create(dbType, config.isUseNewMysqlSyntax());

                if (dialect != null) {
                    dynamicDialect.addDialect(dsName, dialect);
                    log.info("Registered upsert dialect {} for data source '{}'", dialect.getClass().getSimpleName(), dsName);
                }
            }
        }

        // Set primary dialect (first configured or MySQL as fallback)
        if (dynamicDialect.getDialectMap().isEmpty()) {
            // Fallback to MySQL if nothing configured
            UpsertDialect fallback = DialectFactory.create(DbTypeDetector.DbType.MYSQL, false);
            if (fallback != null) {
                dynamicDialect.addDialect("__fallback__", fallback);
                log.warn("No dialects configured, using MySQL as fallback dialect");
            }
        } else {
            // Use the first configured dialect as primary/fallback
            UpsertDialect primary = dynamicDialect.getDialectMap().values().iterator().next();
            dynamicDialect.setPrimaryDialect(primary);
            log.info("Using {} as primary/fallback dialect", primary.getClass().getSimpleName());
        }

        return dynamicDialect;
    }

    @Bean
    @ConditionalOnMissingBean(name = "sqlInjector")
    public UpsertSqlInjector upsertSqlInjector(DynamicUpsertDialect dynamicDialect) {
        log.info("Registering UpsertSqlInjector with DynamicUpsertDialect");
        return new UpsertSqlInjector(dynamicDialect);
    }
}