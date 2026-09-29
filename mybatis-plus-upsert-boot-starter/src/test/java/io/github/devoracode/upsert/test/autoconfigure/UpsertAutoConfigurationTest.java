package io.github.devoracode.upsert.test.autoconfigure;

import io.github.devoracode.upsert.autoconfigure.UpsertAutoConfiguration;
import io.github.devoracode.upsert.autoconfigure.UpsertProperties;
import io.github.devoracode.upsert.core.FieldMeta;
import io.github.devoracode.upsert.core.UpsertMeta;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 单数据源自动配置把 {@code sqlserver-holdlock} 传到方言的链路（无需 Spring 上下文）。
 */
class UpsertAutoConfigurationTest {

    private static final DataSourceProperties SQLSERVER_DATA_SOURCE = sqlserverDataSource();

    @Test
    void holdlock_is_on_by_default() {
        assertThat(new UpsertProperties().isSqlserverHoldlock()).isTrue();

        assertThat(upsertSql(new UpsertProperties()))
                .contains("MERGE INTO t_user WITH (HOLDLOCK) AS t");
    }

    @Test
    void holdlock_disabled_removes_hint() {
        UpsertProperties properties = new UpsertProperties();
        properties.setSqlserverHoldlock(false);

        assertThat(upsertSql(properties))
                .contains("MERGE INTO t_user AS t")
                .doesNotContain("HOLDLOCK");
    }

    private String upsertSql(UpsertProperties properties) {
        return new UpsertAutoConfiguration(properties, SQLSERVER_DATA_SOURCE)
                .upsertDialect()
                .buildUpsertSql(minimalMeta());
    }

    private static DataSourceProperties sqlserverDataSource() {
        DataSourceProperties dataSourceProperties = new DataSourceProperties();
        dataSourceProperties.setUrl("jdbc:sqlserver://localhost:1433;databaseName=db");
        return dataSourceProperties;
    }

    /** 只够渲染单行语句的最小元数据：两个插入列、一个冲突键、无更新列。 */
    private static UpsertMeta minimalMeta() {
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
}
