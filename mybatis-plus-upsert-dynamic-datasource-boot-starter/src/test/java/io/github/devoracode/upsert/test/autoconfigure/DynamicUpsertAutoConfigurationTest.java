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

    // --- per-数据源 use-new-mysql-syntax 的继承语义 ---

    private DynamicUpsertAutoConfiguration wiredWith(UpsertDynamicProperties props) {
        DynamicDataSourceProperties dsProps = new DynamicDataSourceProperties();
        dsProps.setPrimary("mysql");
        DataSourceProperty mysql = new DataSourceProperty();
        mysql.setUrl("jdbc:mysql://localhost:3306/db");
        dsProps.getDatasource().put("mysql", mysql);
        return new DynamicUpsertAutoConfiguration(props, dsProps, beanFactory);
    }

    private UpsertDialect dialectForMySqlDs(UpsertDynamicProperties props) {
        io.github.devoracode.upsert.autoconfigure.DynamicUpsertDialectImpl dd =
                (io.github.devoracode.upsert.autoconfigure.DynamicUpsertDialectImpl)
                        wiredWith(props).dynamicUpsertDialect();
        return dd.getDialectMap().get("mysql");
    }

    @Test
    void ds_config_without_syntax_flag_inherits_global_true() {
        // 只声明了 db-type、未声明语法开关的数据源配置不得把全局 true 静默重置为 false
        UpsertDynamicProperties props = new UpsertDynamicProperties();
        props.setUseNewMysqlSyntax(true);
        UpsertDynamicProperties.DataSourceConfig cfg = new UpsertDynamicProperties.DataSourceConfig();
        cfg.setDbType("mysql");
        props.getDatasource().put("mysql", cfg);

        assertThat(dialectForMySqlDs(props).getClass().getSimpleName())
                .isEqualTo("MysqlUpsertDialect");
    }

    @Test
    void ds_config_explicit_false_overrides_global_true() {
        UpsertDynamicProperties props = new UpsertDynamicProperties();
        props.setUseNewMysqlSyntax(true);
        UpsertDynamicProperties.DataSourceConfig cfg = new UpsertDynamicProperties.DataSourceConfig();
        cfg.setDbType("mysql");
        cfg.setUseNewMysqlSyntax(false);
        props.getDatasource().put("mysql", cfg);

        assertThat(dialectForMySqlDs(props).getClass().getSimpleName())
                .isEqualTo("MysqlLegacyUpsertDialect");
    }

    @Test
    void ds_config_explicit_true_overrides_global_false() {
        UpsertDynamicProperties props = new UpsertDynamicProperties();
        UpsertDynamicProperties.DataSourceConfig cfg = new UpsertDynamicProperties.DataSourceConfig();
        cfg.setUseNewMysqlSyntax(true);
        props.getDatasource().put("mysql", cfg);

        assertThat(dialectForMySqlDs(props).getClass().getSimpleName())
                .isEqualTo("MysqlUpsertDialect");
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
