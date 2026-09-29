package io.github.devoracode.upsert.test.autoconfigure;

import com.baomidou.dynamic.datasource.spring.boot.autoconfigure.DynamicDataSourceProperties;
import com.baomidou.dynamic.datasource.creator.DataSourceProperty;
import io.github.devoracode.upsert.autoconfigure.DynamicUpsertAutoConfiguration;
import io.github.devoracode.upsert.autoconfigure.UpsertDynamicProperties;
import io.github.devoracode.upsert.core.FieldMeta;
import io.github.devoracode.upsert.core.UpsertMeta;
import io.github.devoracode.upsert.dialect.DynamicUpsertDialect;
import io.github.devoracode.upsert.dialect.UpsertDialect;
import io.github.devoracode.upsert.exception.UpsertException;
import io.github.devoracode.upsert.util.DbTypeDetector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

import java.util.Arrays;
import java.util.Collections;
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

        UpsertDialect dialect = config.resolveDialect("mysql", cfg, DbTypeDetector.DbType.MYSQL, false, true);

        assertThat(dialect).isNotNull();
        assertThat(dialect.getClass().getSimpleName()).contains("Mysql");
    }

    @Test
    void resolveDialect_builtin_postgresql_returns_postgres_dialect() {
        UpsertDynamicProperties.DataSourceConfig cfg = new UpsertDynamicProperties.DataSourceConfig();
        cfg.setDbType("postgresql");

        UpsertDialect dialect = config.resolveDialect("pg", cfg, DbTypeDetector.DbType.POSTGRESQL, false, true);

        assertThat(dialect).isNotNull();
        assertThat(dialect.getClass().getSimpleName()).contains("Postgres");
    }

    @Test
    void resolveDialect_custom_with_dialect_ref_returns_custom_bean() {
        UpsertDynamicProperties.DataSourceConfig cfg = new UpsertDynamicProperties.DataSourceConfig();
        cfg.setDbType("custom");
        cfg.setDialectRef("clickHouseDialect");

        UpsertDialect dialect = config.resolveDialect("clickhouse", cfg, DbTypeDetector.DbType.CUSTOM, false, true);

        assertThat(dialect).isNotNull();
        assertThat(dialect).isSameAs(beanFactory.getBean("clickHouseDialect"));
        assertThat(dialect.getClass().getSimpleName()).isEqualTo("ClickHouseTestDialect");
    }

    @Test
    void resolveDialect_custom_without_dialect_ref_throws() {
        UpsertDynamicProperties.DataSourceConfig cfg = new UpsertDynamicProperties.DataSourceConfig();
        cfg.setDbType("custom");

        assertThatThrownBy(() -> config.resolveDialect("custom", cfg, DbTypeDetector.DbType.CUSTOM, false, true))
                .isInstanceOf(UpsertException.class)
                .hasMessageContaining("dialect-ref is configured");
    }

    @Test
    void resolveDialect_custom_with_unknown_bean_name_throws() {
        UpsertDynamicProperties.DataSourceConfig cfg = new UpsertDynamicProperties.DataSourceConfig();
        cfg.setDbType("custom");
        cfg.setDialectRef("nonExistentBean");

        assertThatThrownBy(() -> config.resolveDialect("custom", cfg, DbTypeDetector.DbType.CUSTOM, false, true))
                .isInstanceOf(UpsertException.class)
                .hasMessageContaining("not found in Spring container");
    }

    @Test
    void resolveDialect_custom_with_wrong_bean_type_throws() {
        UpsertDynamicProperties.DataSourceConfig cfg = new UpsertDynamicProperties.DataSourceConfig();
        cfg.setDbType("custom");
        cfg.setDialectRef("wrongTypeBean");

        assertThatThrownBy(() -> config.resolveDialect("custom", cfg, DbTypeDetector.DbType.CUSTOM, false, true))
                .isInstanceOf(UpsertException.class)
                .hasMessageContaining("does not implement UpsertDialect");
    }

    @Test
    void resolveDialect_custom_with_null_config_throws() {
        assertThatThrownBy(() -> config.resolveDialect("custom", null, DbTypeDetector.DbType.CUSTOM, false, true))
                .isInstanceOf(UpsertException.class)
                .hasMessageContaining("requires custom dialect configuration");
    }

    @Test
    void resolveDialect_with_null_config_and_builtin_dbtype_works() {
        UpsertDialect dialect = config.resolveDialect("mysql", null, DbTypeDetector.DbType.MYSQL, false, true);

        assertThat(dialect).isNotNull();
        assertThat(dialect.getClass().getSimpleName()).contains("Mysql");
    }

    // --- 推断失败时的消息脱敏 ---

    @Test
    void url_credentials_are_redacted_from_inference_error() {
        DynamicDataSourceProperties dsProps = new DynamicDataSourceProperties();
        dsProps.setPrimary("legacy");
        DataSourceProperty legacy = new DataSourceProperty();
        legacy.setUrl("jdbc:db2://localhost:50000/db;user=scott;password=s3cret");
        dsProps.getDatasource().put("legacy", legacy);

        assertThatThrownBy(() -> new DynamicUpsertAutoConfiguration(
                new UpsertDynamicProperties(), dsProps, beanFactory).dynamicUpsertDialect())
                .isInstanceOf(UpsertException.class)
                .hasMessageContaining("Cannot infer db-type from JDBC URL 'jdbc:db2://localhost:50000/db;user=***;password=***'")
                .hasMessageNotContaining("s3cret")
                .hasMessageNotContaining("scott");
    }

    // --- 静态数据源为空时的退避 ---

    /**
     * 数据源也可能由 DynamicDataSourceProvider（配置中心）下发或运行期 addDataSource 加入，
     * 这两种情况下静态配置为空属于正常用法，不能因此拒绝启动。
     */
    @Test
    void no_static_datasource_does_not_block_startup() {
        DynamicDataSourceProperties dsProps = new DynamicDataSourceProperties();
        dsProps.setPrimary("from-nacos");

        DynamicUpsertDialect dialect = new DynamicUpsertAutoConfiguration(
                new UpsertDynamicProperties(), dsProps, beanFactory).dynamicUpsertDialect();

        assertThat(dialect).isInstanceOf(io.github.devoracode.upsert.autoconfigure.DynamicUpsertDialectImpl.class);
        assertThat(((io.github.devoracode.upsert.autoconfigure.DynamicUpsertDialectImpl) dialect).getPrimary())
                .isEqualTo("from-nacos");
        assertThat(((io.github.devoracode.upsert.autoconfigure.DynamicUpsertDialectImpl) dialect).getDialectMap())
                .isEmpty();
    }

    // --- sqlserver-holdlock 开关是否真的落到 SQL 上 ---

    @Test
    void sqlserver_holdlock_is_on_by_default() {
        assertThat(new UpsertDynamicProperties().isSqlserverHoldlock()).isTrue();

        assertThat(upsertSqlForSqlServerDs(new UpsertDynamicProperties()))
                .contains("MERGE INTO t_user WITH (HOLDLOCK) AS t");
    }

    @Test
    void sqlserver_holdlock_disabled_removes_hint() {
        UpsertDynamicProperties props = new UpsertDynamicProperties();
        props.setSqlserverHoldlock(false);

        assertThat(upsertSqlForSqlServerDs(props))
                .contains("MERGE INTO t_user AS t")
                .doesNotContain("HOLDLOCK");
    }

    private String upsertSqlForSqlServerDs(UpsertDynamicProperties props) {
        DynamicDataSourceProperties dsProps = new DynamicDataSourceProperties();
        dsProps.setPrimary("sqlserver");
        DataSourceProperty sqlServer = new DataSourceProperty();
        sqlServer.setUrl("jdbc:sqlserver://localhost:1433;databaseName=db");
        dsProps.getDatasource().put("sqlserver", sqlServer);

        io.github.devoracode.upsert.autoconfigure.DynamicUpsertDialectImpl dialect =
                (io.github.devoracode.upsert.autoconfigure.DynamicUpsertDialectImpl)
                        new DynamicUpsertAutoConfiguration(props, dsProps, beanFactory).dynamicUpsertDialect();
        return dialect.getDialectMap().get("sqlserver").buildUpsertSql(minimalMeta());
    }

    /** 只够渲染单行语句的最小元数据：两个插入列、一个冲突键、无更新列。 */
    private UpsertMeta minimalMeta() {
        Map<String, String> fieldToColumn = new HashMap<>();
        fieldToColumn.put("id", "id");
        fieldToColumn.put("code", "code");
        FieldMeta id = FieldMeta.builder().column("id").property("id").dynamic(false).build();
        FieldMeta code = FieldMeta.builder().column("code").property("code").dynamic(false).build();
        return UpsertMeta.builder()
                .tableName("t_user")
                .insertColumns(Arrays.asList("id", "code"))
                .insertFields(Arrays.asList("id", "code"))
                .conflictColumns(Collections.singletonList("code"))
                .updateColumns(Collections.emptyList())
                .updateFields(Collections.emptyList())
                .insertFieldMetas(Arrays.asList(id, code))
                .updateFieldMetas(Collections.emptyList())
                .fieldToColumnMap(fieldToColumn)
                .build();
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
    }
}
