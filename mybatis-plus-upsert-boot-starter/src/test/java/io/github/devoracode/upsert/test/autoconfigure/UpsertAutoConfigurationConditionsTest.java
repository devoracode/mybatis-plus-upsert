package io.github.devoracode.upsert.test.autoconfigure;

import io.github.devoracode.upsert.autoconfigure.UpsertAutoConfiguration;
import io.github.devoracode.upsert.dialect.H2UpsertDialect;
import io.github.devoracode.upsert.dialect.UpsertDialect;
import io.github.devoracode.upsert.injector.UpsertSqlInjector;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 自动配置的退避条件：starter 被传递依赖带进不使用关系型数据库的应用时不应拖垮启动。
 */
class UpsertAutoConfigurationConditionsTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner();

    @Test
    void backs_off_when_no_datasource_is_defined() {
        runner.withConfiguration(AutoConfigurations.of(UpsertAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(UpsertDialect.class);
                    assertThat(context).doesNotHaveBean(UpsertSqlInjector.class);
                });
    }

    /**
     * 只装本 starter、没装 MyBatis-Plus 的应用：MP 在本模块是 provided，
     * 自动配置类必须靠 @ConditionalOnClass 提前退避，否则加载时就会找不到 MP 的类型。
     */
    @Test
    void backs_off_when_mybatis_plus_is_absent() {
        runner.withClassLoader(new FilteredClassLoader("com.baomidou.mybatisplus"))
                .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class, UpsertAutoConfiguration.class))
                .withPropertyValues("spring.datasource.url=jdbc:h2:mem:backoff;DB_CLOSE_DELAY=-1")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(UpsertDialect.class);
                });
    }

    @Test
    void registers_dialect_and_injector_when_datasource_is_present() {
        runner.withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class, UpsertAutoConfiguration.class))
                .withPropertyValues("spring.datasource.url=jdbc:h2:mem:backoff;DB_CLOSE_DELAY=-1")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).getBean(UpsertDialect.class).isInstanceOf(H2UpsertDialect.class);
                    assertThat(context).hasSingleBean(UpsertSqlInjector.class);
                });
    }

    /**
     * 多数据源 starter 在 classpath 上时本自动配置必须退避，否则两个注入器争
     * {@code @ConditionalOnMissingBean(ISqlInjector)} 的胜负取决于求值顺序。
     *
     * <p>结构性断言：本模块不依赖多数据源 starter（否则就成了编译期依赖），
     * 条件只能用字符串形式，因此这里只钉住类名——写错则退避静默失效。
     */
    @Test
    void backs_off_when_dynamic_datasource_starter_is_present() {
        ConditionalOnMissingClass condition =
                UpsertAutoConfiguration.class.getAnnotation(ConditionalOnMissingClass.class);

        assertThat(condition).isNotNull();
        assertThat(condition.value())
                .containsExactly("io.github.devoracode.upsert.autoconfigure.DynamicUpsertDialectImpl");
    }

    /** {@code db-type=custom} 但应用没提供方言 Bean 时，报错要指向自定义方言而不是 Spring 默认文案。 */
    @Test
    void custom_db_type_without_dialect_bean_reports_how_to_supply_one() {
        runner.withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class, UpsertAutoConfiguration.class))
                .withPropertyValues("mybatis-plus.upsert.db-type=custom",
                        "spring.datasource.url=jdbc:h2:mem:backoff;DB_CLOSE_DELAY=-1")
                .run(context -> assertThat(context).getFailure()
                        .hasMessageContaining("Expected exactly one UpsertDialect bean, found 0")
                        .hasMessageContaining("db-type=custom"));
    }

    /** 应用自带方言 Bean 时直接用它，不受 db-type 影响。 */
    @Test
    void application_supplied_dialect_bean_is_used() {
        runner.withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class, UpsertAutoConfiguration.class))
                .withBean("clickHouseDialect", UpsertDialect.class,
                        () -> new io.github.devoracode.upsert.dialect.H2UpsertDialect())
                .withPropertyValues("mybatis-plus.upsert.db-type=custom",
                        "spring.datasource.url=jdbc:h2:mem:backoff;DB_CLOSE_DELAY=-1")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(UpsertSqlInjector.class);
                });
    }
}
