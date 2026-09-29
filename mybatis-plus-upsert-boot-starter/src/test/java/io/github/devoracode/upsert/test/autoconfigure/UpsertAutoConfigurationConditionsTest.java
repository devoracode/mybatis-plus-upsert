package io.github.devoracode.upsert.test.autoconfigure;

import io.github.devoracode.upsert.autoconfigure.UpsertAutoConfiguration;
import io.github.devoracode.upsert.dialect.H2UpsertDialect;
import io.github.devoracode.upsert.dialect.UpsertDialect;
import io.github.devoracode.upsert.injector.UpsertSqlInjector;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
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
}
