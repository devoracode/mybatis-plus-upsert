package io.github.devoracode.upsert.test.autoconfigure;

import com.baomidou.dynamic.datasource.spring.boot.autoconfigure.DynamicDataSourceProperties;
import com.baomidou.dynamic.datasource.creator.DataSourceProperty;
import io.github.devoracode.upsert.autoconfigure.DynamicUpsertAutoConfiguration;
import io.github.devoracode.upsert.autoconfigure.UpsertDynamicProperties;
import io.github.devoracode.upsert.dialect.UpsertDialect;
import io.github.devoracode.upsert.exception.UpsertException;
import io.github.devoracode.upsert.util.DbTypeDetector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DynamicUpsertAutoConfigurationTest {

    private ConfigurableListableBeanFactory beanFactory;
    private DynamicUpsertAutoConfiguration config;
    private DynamicDataSourceProperties dynamicDataSourceProperties;

    @BeforeEach
    void setUp() {
        beanFactory = new org.springframework.beans.factory.support.DefaultListableBeanFactory();
        
        beanFactory.registerSingleton("clickHouseDialect", new ClickHouseTestDialect());
        beanFactory.registerSingleton("wrongTypeBean", "not a dialect");
        
        dynamicDataSourceProperties = new DynamicDataSourceProperties();
        config = new DynamicUpsertAutoConfiguration(
                new UpsertDynamicProperties(),
                dynamicDataSourceProperties,
                beanFactory);
    }

    @Test
    void resolveDialect_builtin_mysql_returns_mysql_dialect() {
        UpsertDynamicProperties.DataSourceConfig cfg = new UpsertDynamicProperties.DataSourceConfig();
        cfg.setDbType("mysql");

        UpsertDialect dialect = config.resolveDialect("mysql", cfg, DbTypeDetector.DbType.MYSQL, false);

        assertThat(dialect).isNotNull();
        assertThat(dialect.getClass().getSimpleName()).contains("Mysql");
    }

    @Test
    void resolveDialect_builtin_postgresql_returns_postgres_dialect() {
        UpsertDynamicProperties.DataSourceConfig cfg = new UpsertDynamicProperties.DataSourceConfig();
        cfg.setDbType("postgresql");

        UpsertDialect dialect = config.resolveDialect("pg", cfg, DbTypeDetector.DbType.POSTGRESQL, false);

        assertThat(dialect).isNotNull();
        assertThat(dialect.getClass().getSimpleName()).contains("Postgres");
    }

    @Test
    void resolveDialect_custom_with_dialect_ref_returns_custom_bean() {
        UpsertDynamicProperties.DataSourceConfig cfg = new UpsertDynamicProperties.DataSourceConfig();
        cfg.setDbType("custom");
        cfg.setDialectRef("clickHouseDialect");

        UpsertDialect dialect = config.resolveDialect("clickhouse", cfg, DbTypeDetector.DbType.CUSTOM, false);

        assertThat(dialect).isNotNull();
        assertThat(dialect).isSameAs(beanFactory.getBean("clickHouseDialect"));
        assertThat(dialect.getClass().getSimpleName()).isEqualTo("ClickHouseTestDialect");
    }

    @Test
    void resolveDialect_custom_without_dialect_ref_throws() {
        UpsertDynamicProperties.DataSourceConfig cfg = new UpsertDynamicProperties.DataSourceConfig();
        cfg.setDbType("custom");

        assertThatThrownBy(() -> config.resolveDialect("custom", cfg, DbTypeDetector.DbType.CUSTOM, false))
                .isInstanceOf(UpsertException.class)
                .hasMessageContaining("dialect-ref is configured");
    }

    @Test
    void resolveDialect_custom_with_unknown_bean_name_throws() {
        UpsertDynamicProperties.DataSourceConfig cfg = new UpsertDynamicProperties.DataSourceConfig();
        cfg.setDbType("custom");
        cfg.setDialectRef("nonExistentBean");

        assertThatThrownBy(() -> config.resolveDialect("custom", cfg, DbTypeDetector.DbType.CUSTOM, false))
                .isInstanceOf(UpsertException.class)
                .hasMessageContaining("not found in Spring container");
    }

    @Test
    void resolveDialect_custom_with_wrong_bean_type_throws() {
        UpsertDynamicProperties.DataSourceConfig cfg = new UpsertDynamicProperties.DataSourceConfig();
        cfg.setDbType("custom");
        cfg.setDialectRef("wrongTypeBean");

        assertThatThrownBy(() -> config.resolveDialect("custom", cfg, DbTypeDetector.DbType.CUSTOM, false))
                .isInstanceOf(UpsertException.class)
                .hasMessageContaining("does not implement UpsertDialect");
    }

    @Test
    void resolveDialect_custom_with_null_config_throws() {
        assertThatThrownBy(() -> config.resolveDialect("custom", null, DbTypeDetector.DbType.CUSTOM, false))
                .isInstanceOf(UpsertException.class)
                .hasMessageContaining("requires custom dialect configuration");
    }

    @Test
    void resolveDialect_with_null_config_and_builtin_dbtype_works() {
        UpsertDialect dialect = config.resolveDialect("mysql", null, DbTypeDetector.DbType.MYSQL, false);

        assertThat(dialect).isNotNull();
        assertThat(dialect.getClass().getSimpleName()).contains("Mysql");
    }

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
