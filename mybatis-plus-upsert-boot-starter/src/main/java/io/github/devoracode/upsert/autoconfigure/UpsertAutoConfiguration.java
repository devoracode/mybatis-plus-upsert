package io.github.devoracode.upsert.autoconfigure;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
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

@Configuration
@EnableConfigurationProperties(UpsertProperties.class)
@ConditionalOnProperty(prefix = "mybatis-plus.upsert", name = "enabled", havingValue = "true", matchIfMissing = true)
@AutoConfigureBefore(MybatisPlusAutoConfiguration.class)
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
@Slf4j
public class UpsertAutoConfiguration {

    private final UpsertProperties properties;
    private final DataSourceProperties dataSourceProperties;

    public UpsertAutoConfiguration(UpsertProperties properties, DataSourceProperties dataSourceProperties) {
        this.properties = properties;
        this.dataSourceProperties = dataSourceProperties;
    }

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

    @Bean
    @ConditionalOnMissingBean(com.baomidou.mybatisplus.core.injector.ISqlInjector.class)
    public UpsertSqlInjector upsertSqlInjector(UpsertDialect dialect) {
        return new UpsertSqlInjector(dialect);
    }
}
