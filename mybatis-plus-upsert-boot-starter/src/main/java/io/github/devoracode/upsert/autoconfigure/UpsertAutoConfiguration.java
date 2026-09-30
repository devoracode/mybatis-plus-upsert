package io.github.devoracode.upsert.autoconfigure;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import io.github.devoracode.upsert.dialect.UpsertDialect;
import io.github.devoracode.upsert.exception.UpsertException;
import io.github.devoracode.upsert.injector.UpsertSqlInjector;
import io.github.devoracode.upsert.util.DialectFactory;
import io.github.devoracode.upsert.util.DbTypeDetector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;

/**
 * 单数据源 Upsert 的自动配置：解析数据库类型、创建 {@link UpsertDialect}、注册 {@link UpsertSqlInjector}。
 *
 * <p>本 starter 可能只是被传递依赖带进某个应用（该应用并未使用关系型数据库），
 * 因此在缺少 MyBatis-Plus 或缺少 {@link DataSource} Bean 时整类退避，不参与装配。
 *
 * <p>多数据源 starter 在 classpath 上时也整类退避：两个自动配置都注册
 * {@code UpsertSqlInjector}，靠 {@code @ConditionalOnMissingBean} 决出胜负时结果取决于
 * 条件求值顺序，选错会让多数据源路由静默失效。退避后由多数据源 starter 接管，
 * 结果与求值顺序无关。
 *
 * @author devoracode
 * @since 1.0.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(MybatisPlusAutoConfiguration.class)
@ConditionalOnMissingClass("io.github.devoracode.upsert.autoconfigure.DynamicUpsertDialectImpl")
@ConditionalOnBean(DataSource.class)
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

    /**
     * 注册内置方言：{@code db-type} 显式配置时直接用它，否则从 JDBC URL 推断。
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
                throw new UpsertException("Cannot infer db-type from data source. Please configure"
                        + " mybatis-plus.upsert.db-type explicitly. If the database has no built-in dialect yet,"
                        + " set db-type=custom and provide an UpsertDialect bean instead.");
            }
            log.info("Auto-inferred db-type as '{}' from JDBC URL", dbTypeEnum.name().toLowerCase());
        }

        return DialectFactory.create(dbTypeEnum,
                properties.isUseNewMysqlSyntax(), properties.isSqlserverHoldlock());
    }

    /**
     * 注册 Upsert 注入器，并把 {@code fill-strategy} 解析出的策略交给它。
     *
     * <p>方言用 {@link ObjectProvider} 取：{@code db-type=custom} 且应用没有提供
     * {@link UpsertDialect} Bean 时，这里给出可操作的报错，而不是 Spring 默认的
     * "No qualifying bean"。
     *
     * @param dialectProvider 用于 SQL 生成的 upsert 方言，由内置自动配置或应用自身提供
     * @return 配置好的 UpsertSqlInjector
     * @throws UpsertException 未注册任何 {@link UpsertDialect}
     */
    @Bean
    @ConditionalOnMissingBean(com.baomidou.mybatisplus.core.injector.ISqlInjector.class)
    public UpsertSqlInjector upsertSqlInjector(ObjectProvider<UpsertDialect> dialectProvider) {
        UpsertDialect dialect = dialectProvider.getIfUnique();
        if (dialect == null) {
            // 零个和多个候选都会走到这里，getIfUnique() 对两者都返回 null
            throw new UpsertException("Expected exactly one UpsertDialect bean, found "
                    + dialectProvider.orderedStream().count() + ". mybatis-plus.upsert.db-type=custom means the"
                    + " dialect is supplied by the application: declare an UpsertDialect bean (for example"
                    + " @Component on your dialect class) or point db-type at a built-in database type.");
        }
        return new UpsertSqlInjector(dialect, properties.resolveFillStrategy());
    }
}
