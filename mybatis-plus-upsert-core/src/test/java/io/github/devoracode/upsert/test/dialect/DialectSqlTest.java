package io.github.devoracode.upsert.test.dialect;

import io.github.devoracode.upsert.core.FieldMeta;
import io.github.devoracode.upsert.core.UpsertMeta;
import io.github.devoracode.upsert.dialect.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DialectSqlTest {

    // All static fields meta (all FieldMeta.dynamic=false), used to verify basic SQL structure is unaffected by dynamic logic
    private UpsertMeta staticMeta;

    // Meta with dynamic fields: email uses NOT_NULL (dynamic, !checkEmpty),
    // updateTime is always appended (non-dynamic), used to verify <if>/<trim> generation correctness
    private UpsertMeta dynamicMeta;

    @BeforeEach
    void setup() {

        Map<String, String> fieldToColumnMap = new HashMap<>();
        fieldToColumnMap.put("id",         "id");
        fieldToColumnMap.put("username",   "username");
        fieldToColumnMap.put("email",      "email");
        fieldToColumnMap.put("updateTime", "update_time");

        List<FieldMeta> staticInsertMetas = Arrays.asList(
                FieldMeta.builder().column("id").property("id").dynamic(false).build(),
                FieldMeta.builder().column("username").property("username").dynamic(false).build(),
                FieldMeta.builder().column("email").property("email").dynamic(false).build(),
                FieldMeta.builder().column("update_time").property("updateTime").dynamic(false).build()
        );
        List<FieldMeta> staticUpdateMetas = Arrays.asList(
                FieldMeta.builder().column("email").property("email").dynamic(false).build(),
                FieldMeta.builder().column("update_time").property("updateTime").dynamic(false).build()
        );

        staticMeta = UpsertMeta.builder()
                .tableName("t_user")
                .insertColumns(Arrays.asList("id", "username", "email", "update_time"))
                .insertFields(Arrays.asList("id", "username", "email", "updateTime"))
                .conflictColumns(Collections.singletonList("username"))
                .updateColumns(Arrays.asList("email", "update_time"))
                .updateFields(Arrays.asList("email", "updateTime"))
                .insertFieldMetas(staticInsertMetas)
                .updateFieldMetas(staticUpdateMetas)
                .fieldToColumnMap(fieldToColumnMap)
                .build();

        List<FieldMeta> dynamicInsertMetas = Arrays.asList(
                FieldMeta.builder().column("id").property("id").dynamic(false).build(),
                FieldMeta.builder().column("username").property("username").dynamic(false).build(),
                FieldMeta.builder().column("email").property("email").dynamic(true).checkEmpty(false).build(),
                FieldMeta.builder().column("update_time").property("updateTime").dynamic(false).build()
        );
        List<FieldMeta> dynamicUpdateMetas = Arrays.asList(
                FieldMeta.builder().column("email").property("email").dynamic(true).checkEmpty(false).build(),
                FieldMeta.builder().column("update_time").property("updateTime").dynamic(false).build()
        );

        dynamicMeta = UpsertMeta.builder()
                .tableName("t_user_dynamic")
                .insertColumns(Arrays.asList("id", "username", "email", "update_time"))
                .insertFields(Arrays.asList("id", "username", "email", "updateTime"))
                .conflictColumns(Collections.singletonList("username"))
                .updateColumns(Arrays.asList("email", "update_time"))
                .updateFields(Arrays.asList("email", "updateTime"))
                .insertFieldMetas(dynamicInsertMetas)
                .updateFieldMetas(dynamicUpdateMetas)
                .fieldToColumnMap(fieldToColumnMap)
                .build();
    }

    // --- MySQL: Static field scenarios (basic structure unchanged) ---

    @Test
    void mysql_legacy_single_sql() {
        String sql = new MysqlLegacyUpsertDialect().buildUpsertSql(staticMeta);
        assertThat(sql).containsIgnoringCase("INSERT INTO t_user");
        assertThat(sql).containsIgnoringCase("ON DUPLICATE KEY UPDATE");
        assertThat(sql).contains("VALUES(email)");
        assertThat(sql).contains("VALUES(update_time)");
        assertThat(sql).doesNotContain("AS new");
    }

    @Test
    void mysql_alias_single_sql() {
        String sql = new MysqlUpsertDialect().buildUpsertSql(staticMeta);
        assertThat(sql).containsIgnoringCase("INSERT INTO t_user");
        assertThat(sql).contains("AS new ON DUPLICATE KEY UPDATE");
        assertThat(sql).contains("new.email");
        assertThat(sql).contains("new.update_time");
    }

    @Test
    void mysql_legacy_batch_sql_uses_values_function() {
        String sql = new MysqlLegacyUpsertDialect().buildUpsertBatchSql(staticMeta);
        assertThat(sql).containsIgnoringCase("VALUES(email)");
        assertThat(sql).contains("<foreach");
        assertThat(sql).contains("#{item.email}");
        assertThat(sql).doesNotContain("AS new");
    }

    @Test
    void mysql_alias_batch_sql_uses_alias_syntax() {
        String sql = new MysqlUpsertDialect().buildUpsertBatchSql(staticMeta);
        assertThat(sql).contains("AS new ON DUPLICATE KEY UPDATE");
        assertThat(sql).contains("new.email");
        assertThat(sql).contains("new.update_time");
        assertThat(sql).doesNotContain("VALUES(email)");
        assertThat(sql).contains("<foreach");
        assertThat(sql).contains("#{item.email}");
    }

    // --- MySQL: Dynamic field scenarios ---

    @Test
    void mysql_legacy_single_sql_wraps_dynamic_field_with_if() {
        String sql = new MysqlLegacyUpsertDialect().buildUpsertSql(dynamicMeta);
        assertThat(sql).contains("<if test=\"et.email != null\">email, </if>");
        assertThat(sql).contains("<if test=\"et.email != null\">#{et.email}, </if>");
        assertThat(sql).contains("update_time, ");
        assertThat(sql).doesNotContain("<if test=\"et.updateTime");
        assertThat(sql).contains("<trim suffixOverrides=\",\">");
    }

    @Test
    void mysql_legacy_single_sql_update_set_dynamic_field() {
        String sql = new MysqlLegacyUpsertDialect().buildUpsertSql(dynamicMeta);
        assertThat(sql).contains("<if test=\"et.email != null\">email = VALUES(email), </if>");
    }

    @Test
    void mysql_legacy_batch_sql_unaffected_by_dynamic_meta() {
        String sql = new MysqlLegacyUpsertDialect().buildUpsertBatchSql(dynamicMeta);
        assertThat(sql).doesNotContain("<if test=");
        assertThat(sql).contains("VALUES(email)");
    }

    @Test
    void mysql_alias_batch_sql_unaffected_by_dynamic_meta() {
        String sql = new MysqlUpsertDialect().buildUpsertBatchSql(dynamicMeta);
        assertThat(sql).doesNotContain("<if test=");
        assertThat(sql).contains("new.email");
        assertThat(sql).doesNotContain("VALUES(email)");
    }

    // --- PostgreSQL ---

    @Test
    void postgres_single_sql() {
        String sql = new PostgresUpsertDialect().buildUpsertSql(staticMeta);
        assertThat(sql).containsIgnoringCase("ON CONFLICT (username)");
        assertThat(sql).containsIgnoringCase("DO UPDATE SET");
        assertThat(sql).contains("EXCLUDED.email");
    }

    @Test
    void postgres_single_sql_dynamic_field() {
        String sql = new PostgresUpsertDialect().buildUpsertSql(dynamicMeta);
        assertThat(sql).contains("<if test=\"et.email != null\">email, </if>");
        assertThat(sql).containsIgnoringCase("ON CONFLICT (username)");
    }

    @Test
    void postgres_batch_sql() {
        String sql = new PostgresUpsertDialect().buildUpsertBatchSql(staticMeta);
        assertThat(sql).contains("<foreach");
        assertThat(sql).contains("EXCLUDED.email");
        assertThat(sql).containsIgnoringCase("ON CONFLICT (username)");
    }

    // --- Oracle ---

    @Test
    void oracle_single_sql() {
        String sql = new OracleUpsertDialect().buildUpsertSql(staticMeta);
        assertThat(sql).containsIgnoringCase("MERGE INTO t_user t");
        assertThat(sql).containsIgnoringCase("USING (SELECT");
        assertThat(sql).containsIgnoringCase("FROM dual");
        assertThat(sql).containsIgnoringCase("WHEN MATCHED THEN UPDATE SET");
        assertThat(sql).containsIgnoringCase("WHEN NOT MATCHED THEN INSERT");
        // Single-row UPDATE SET now references the src alias (same as INSERT VALUES and batch)
        assertThat(sql).contains("email = src.email");
    }

    @Test
    void oracle_single_sql_dynamic_field_consistent_across_src_and_insert() {
        String sql = new OracleUpsertDialect().buildUpsertSql(dynamicMeta);
        // email must be wrapped with the same <if> condition in four places:
        // src subquery columns, INSERT column names, INSERT values, and UPDATE SET.
        // Otherwise, column count mismatch would generate invalid SQL.
        long ifCount = sql.split("<if test=\"et\\.email != null\">", -1).length - 1;
        assertThat(ifCount).isEqualTo(4); // src columns, INSERT column names, INSERT values, UPDATE SET
    }

    @Test
    void oracle_batch_sql_uses_foreach_separator() {
        String sql = new OracleUpsertDialect().buildUpsertBatchSql(staticMeta);
        assertThat(sql).contains("<foreach");
        assertThat(sql).contains("separator=\";\"");
        assertThat(sql).contains("#{item.email}");
    }

    @Test
    void oracle_batch_sql_unaffected_by_dynamic_meta() {
        String sql = new OracleUpsertDialect().buildUpsertBatchSql(dynamicMeta);
        assertThat(sql).doesNotContain("<if test=");
    }

    // --- SQL Server ---

    @Test
    void sqlserver_single_sql() {
        String sql = new SqlServerUpsertDialect().buildUpsertSql(staticMeta);
        assertThat(sql).containsIgnoringCase("MERGE INTO t_user AS t");
        assertThat(sql).containsIgnoringCase("AS src");
        assertThat(sql).containsIgnoringCase("WHEN MATCHED THEN UPDATE SET");
        assertThat(sql.trim()).endsWith(";");
        // Single-row UPDATE SET references the src alias (same as INSERT VALUES and batch)
        assertThat(sql).contains("email = src.email");
    }

    @Test
    void sqlserver_single_sql_uses_select_based_src_for_dynamic_support() {
        String sql = new SqlServerUpsertDialect().buildUpsertSql(dynamicMeta);
        // Single-row scenario uses SELECT-based src (instead of VALUES(...) AS src(cols)) to support dynamic columns
        assertThat(sql).containsIgnoringCase("USING (SELECT");
        assertThat(sql).contains("<if test=\"et.email != null\">");
    }

    @Test
    void sqlserver_batch_sql_uses_multi_row_values() {
        String sql = new SqlServerUpsertDialect().buildUpsertBatchSql(staticMeta);
        assertThat(sql).contains("<foreach");
        assertThat(sql).contains("#{item.email}");
        assertThat(sql.trim()).endsWith(";");
        // Batch scenarios still use VALUES(...) AS src(cols)) multi-row syntax
        assertThat(sql).containsIgnoringCase("USING (VALUES");
    }

    @Test
    void sqlserver_batch_sql_unaffected_by_dynamic_meta() {
        String sql = new SqlServerUpsertDialect().buildUpsertBatchSql(dynamicMeta);
        assertThat(sql).doesNotContain("<if test=");
    }

    // --- H2 ---

    @Test
    void h2_single_sql() {
        String sql = new H2UpsertDialect().buildUpsertSql(staticMeta);
        assertThat(sql).containsIgnoringCase("MERGE INTO t_user");
        assertThat(sql).containsIgnoringCase("KEY(username)");
        assertThat(sql).contains("#{et.email}");
    }

    @Test
    void h2_single_sql_dynamic_field() {
        String sql = new H2UpsertDialect().buildUpsertSql(dynamicMeta);
        assertThat(sql).contains("<if test=\"et.email != null\">email, </if>");
        assertThat(sql).contains("<if test=\"et.email != null\">#{et.email}, </if>");
    }

    @Test
    void h2_batch_sql_uses_foreach_separator() {
        String sql = new H2UpsertDialect().buildUpsertBatchSql(staticMeta);
        assertThat(sql).contains("<foreach");
        assertThat(sql).contains("separator=\";\"");
    }

    // --- checkEmpty (NOT_EMPTY strategy) ---

    @Test
    void not_empty_strategy_generates_blank_check() {
        Map<String, String> map = new HashMap<>();
        map.put("name", "name");
        UpsertMeta meta = UpsertMeta.builder()
                .tableName("t_x")
                .insertColumns(Collections.singletonList("name"))
                .insertFields(Collections.singletonList("name"))
                .conflictColumns(Collections.singletonList("name"))
                .updateColumns(Collections.emptyList())
                .updateFields(Collections.emptyList())
                .insertFieldMetas(Collections.singletonList(
                        FieldMeta.builder().column("name").property("name").dynamic(true).checkEmpty(true).build()))
                .updateFieldMetas(Collections.emptyList())
                .fieldToColumnMap(map)
                .build();
        String sql = new MysqlLegacyUpsertDialect().buildUpsertSql(meta);
        assertThat(sql).contains("et.name != null and et.name != ''");
    }

    // --- multi-column conflict handling ---

    @Test
    void multi_conflict_columns_are_comma_joined_across_dialects() {
        Map<String, String> fieldToColumnMap = new HashMap<>();
        fieldToColumnMap.put("id", "id");
        fieldToColumnMap.put("tenantId", "tenant_id");
        fieldToColumnMap.put("bizCode", "biz_code");
        fieldToColumnMap.put("name", "name");

        UpsertMeta multiColumnMeta = UpsertMeta.builder()
                .tableName("t_multi_join")
                .insertColumns(Arrays.asList("id", "tenant_id", "biz_code", "name"))
                .insertFields(Arrays.asList("id", "tenantId", "bizCode", "name"))
                .conflictColumns(Arrays.asList("tenant_id", "biz_code"))
                .updateColumns(Collections.singletonList("name"))
                .updateFields(Collections.singletonList("name"))
                .insertFieldMetas(Arrays.asList(
                        FieldMeta.builder().column("id").property("id").dynamic(false).build(),
                        FieldMeta.builder().column("tenant_id").property("tenantId").dynamic(false).build(),
                        FieldMeta.builder().column("biz_code").property("bizCode").dynamic(false).build(),
                        FieldMeta.builder().column("name").property("name").dynamic(false).build()))
                .updateFieldMetas(Collections.singletonList(
                        FieldMeta.builder().column("name").property("name").dynamic(false).build()))
                .fieldToColumnMap(fieldToColumnMap)
                .build();

        assertThat(new PostgresUpsertDialect().buildUpsertSql(multiColumnMeta))
                .containsIgnoringCase("ON CONFLICT (tenant_id, biz_code)");
        assertThat(new H2UpsertDialect().buildUpsertSql(multiColumnMeta))
                .containsIgnoringCase("KEY(tenant_id, biz_code)");
        assertThat(new MysqlLegacyUpsertDialect().buildUpsertBatchSql(multiColumnMeta))
                .contains("INSERT INTO t_multi_join (id, tenant_id, biz_code, name)");
        assertThat(new SqlServerUpsertDialect().buildUpsertBatchSql(multiColumnMeta))
                .contains("AS src(id, tenant_id, biz_code, name)");
    }

    @Test
    void single_item_list_produces_no_separator() {
        Map<String, String> singleMap = new HashMap<>();
        singleMap.put("id", "id");
        singleMap.put("code", "code");
        UpsertMeta singleColumnMeta = UpsertMeta.builder()
                .tableName("t_single_join")
                .insertColumns(Arrays.asList("id", "code"))
                .insertFields(Arrays.asList("id", "code"))
                .conflictColumns(Collections.singletonList("code"))
                .updateColumns(Collections.emptyList())
                .updateFields(Collections.emptyList())
                .insertFieldMetas(Arrays.asList(
                        FieldMeta.builder().column("id").property("id").dynamic(false).build(),
                        FieldMeta.builder().column("code").property("code").dynamic(false).build()))
                .updateFieldMetas(Collections.emptyList())
                .fieldToColumnMap(singleMap)
                .build();

        List<UpsertDialect> dialects = Arrays.asList(
                new MysqlLegacyUpsertDialect(), new PostgresUpsertDialect(), new H2UpsertDialect(),
                new OracleUpsertDialect(), new SqlServerUpsertDialect());
        for (UpsertDialect dialect : dialects) {
            String sql = dialect.buildUpsertBatchSql(singleColumnMeta);
            assertThat(sql).as(dialect.getClass().getSimpleName() + " batch SQL")
                    .doesNotContain("(, ").doesNotContain(", ,");
        }
    }
}
