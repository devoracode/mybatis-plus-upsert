package io.github.devoracode.upsert.autoconfigure;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import io.github.devoracode.upsert.dialect.UpsertDialect;
import io.github.devoracode.upsert.injector.UpsertSqlInjector;
import io.github.devoracode.upsert.util.DialectFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(UpsertProperties.class)
@ConditionalOnProperty(prefix = "mybatis-plus.upsert", name = "enabled", havingValue = "true", matchIfMissing = true)
@AutoConfigureBefore(MybatisPlusAutoConfiguration.class)
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
public class UpsertAutoConfiguration {

    private final UpsertProperties properties;

    public UpsertAutoConfiguration(UpsertProperties properties) {
        this.properties = properties;
    }

    @Bean
    @ConditionalOnMissingBean(UpsertDialect.class)
    @Conditional(ConditionalOnNotCustomDbType.class)
    public UpsertDialect upsertDialect() {
        return DialectFactory.create(properties.getDbType(), properties.isUseNewMysqlSyntax());
    }

    @Bean
    @ConditionalOnMissingBean(com.baomidou.mybatisplus.core.injector.ISqlInjector.class)
    public UpsertSqlInjector upsertSqlInjector(UpsertDialect dialect) {
        return new UpsertSqlInjector(dialect);
    }
}
