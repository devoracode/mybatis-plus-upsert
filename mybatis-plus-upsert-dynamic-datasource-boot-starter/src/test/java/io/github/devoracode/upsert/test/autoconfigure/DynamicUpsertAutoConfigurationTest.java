package io.github.devoracode.upsert.test.autoconfigure;

import io.github.devoracode.upsert.autoconfigure.DynamicUpsertAutoConfiguration;
import io.github.devoracode.upsert.autoconfigure.UpsertDynamicProperties;
import io.github.devoracode.upsert.dialect.UpsertDialect;
import io.github.devoracode.upsert.exception.UpsertException;
import io.github.devoracode.upsert.util.DbTypeDetector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DynamicUpsertAutoConfiguration#resolveDialect(String, UpsertDynamicProperties.DataSourceConfig, DbTypeDetector.DbType)}.
 * These tests verify the custom dialect resolution logic without requiring full Spring context bootstrap.
 */
class DynamicUpsertAutoConfigurationTest {

    private ConfigurableListableBeanFactory beanFactory;
    private DynamicUpsertAutoConfiguration config;

    @BeforeEach
    void setUp() {
        // Use a simple test configuration that registers our test beans
        beanFactory = new org.springframework.beans.factory.support.DefaultListableBeanFactory();
        
        // Register custom dialect bean
        beanFactory.registerSingleton("clickHouseDialect", new ClickHouseTestDialect());
        beanFactory.registerSingleton("wrongTypeBean", "not a dialect");
        
        config = new DynamicUpsertAutoConfiguration(
                new UpsertDynamicProperties(),
                new com.baomidou.dynamic.datasource.spring.boot.autoconfigure.DynamicDataSourceProperties(),
                beanFactory);
    }

    @Test
    void resolveDialect_builtin_mysql_returns_mysql_dialect() {
        UpsertDynamicProperties.DataSourceConfig cfg = new UpsertDynamicProperties.DataSourceConfig();
        cfg.setDbType("mysql");
        cfg.setUseNewMysqlSyntax(false);

        UpsertDialect dialect = config.resolveDialect("mysql", cfg, DbTypeDetector.DbType.MYSQL);

        assertThat(dialect).isNotNull();
        assertThat(dialect.getClass().getSimpleName()).contains("Mysql");
    }

    @Test
    void resolveDialect_builtin_postgresql_returns_postgres_dialect() {
        UpsertDynamicProperties.DataSourceConfig cfg = new UpsertDynamicProperties.DataSourceConfig();
        cfg.setDbType("postgresql");

        UpsertDialect dialect = config.resolveDialect("pg", cfg, DbTypeDetector.DbType.POSTGRESQL);

        assertThat(dialect).isNotNull();
        assertThat(dialect.getClass().getSimpleName()).contains("Postgres");
    }

    @Test
    void resolveDialect_custom_with_dialect_ref_returns_custom_bean() {
        UpsertDynamicProperties.DataSourceConfig cfg = new UpsertDynamicProperties.DataSourceConfig();
        cfg.setDbType("custom");
        cfg.setDialectRef("clickHouseDialect");

        UpsertDialect dialect = config.resolveDialect("clickhouse", cfg, DbTypeDetector.DbType.CUSTOM);

        assertThat(dialect).isNotNull();
        assertThat(dialect).isSameAs(beanFactory.getBean("clickHouseDialect"));
        assertThat(dialect.getClass().getSimpleName()).isEqualTo("ClickHouseTestDialect");
    }

    @Test
    void resolveDialect_custom_without_dialect_ref_throws() {
        UpsertDynamicProperties.DataSourceConfig cfg = new UpsertDynamicProperties.DataSourceConfig();
        cfg.setDbType("custom");
        // no dialect-ref set

        assertThatThrownBy(() -> config.resolveDialect("custom", cfg, DbTypeDetector.DbType.CUSTOM))
                .isInstanceOf(UpsertException.class)
                .hasMessageContaining("dialect-ref is configured");
    }

    @Test
    void resolveDialect_custom_with_unknown_bean_name_throws() {
        UpsertDynamicProperties.DataSourceConfig cfg = new UpsertDynamicProperties.DataSourceConfig();
        cfg.setDbType("custom");
        cfg.setDialectRef("nonExistentBean");

        assertThatThrownBy(() -> config.resolveDialect("custom", cfg, DbTypeDetector.DbType.CUSTOM))
                .isInstanceOf(UpsertException.class)
                .hasMessageContaining("not found in Spring container");
    }

    @Test
    void resolveDialect_custom_with_wrong_bean_type_throws() {
        UpsertDynamicProperties.DataSourceConfig cfg = new UpsertDynamicProperties.DataSourceConfig();
        cfg.setDbType("custom");
        cfg.setDialectRef("wrongTypeBean");

        assertThatThrownBy(() -> config.resolveDialect("custom", cfg, DbTypeDetector.DbType.CUSTOM))
                .isInstanceOf(UpsertException.class)
                .hasMessageContaining("does not implement UpsertDialect");
    }

    // Minimal custom dialect for testing
    static class ClickHouseTestDialect implements UpsertDialect {
        @Override
        public String buildUpsertSql(io.github.devoracode.upsert.core.UpsertMeta meta) {
            return "INSERT INTO " + meta.getTableName() + " ... ON DUPLICATE KEY UPDATE ...";
        }

        @Override
        public String buildUpsertBatchSql(io.github.devoracode.upsert.core.UpsertMeta meta) {
            return "INSERT INTO " + meta.getTableName() + " ... ON DUPLICATE KEY UPDATE ...";
        }
    }
}