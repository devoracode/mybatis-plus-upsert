package io.github.devoracode.upsert.test.dialect;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import io.github.devoracode.upsert.core.FieldMeta;
import io.github.devoracode.upsert.core.UpsertMeta;
import io.github.devoracode.upsert.core.UpsertMetaParser;
import io.github.devoracode.upsert.dialect.*;
import io.github.devoracode.upsert.test.support.UserEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DialectSqlTest {

    // 基于真实解析器的渲染测试需要 UserEntity 的 TableInfo；
    // 解析器无全局缓存、只解析递入的 TableInfo，因此这里手动构造并持有引用，
    // 与 UpsertMetaParserTest 相同，保证本测试可独立运行
    private static TableInfo userInfo;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        userInfo = TableInfoHelper.initTableInfo(assistant, UserEntity.class);
    }

    // 全静态字段元数据（所有 FieldMeta.dynamic=false），用于验证基础 SQL 结构不受动态逻辑影响
    private UpsertMeta staticMeta;

    // 含动态字段的元数据：email 使用 NOT_NULL（dynamic=true, !checkEmpty），
    // updateTime 始终追加（非动态），用于验证 <if>/<trim> 生成是否正确
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

    // --- MySQL：静态字段场景（基础结构不变） ---

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

    // --- MySQL：动态字段场景 ---

    @Test
    void mysql_legacy_single_sql_wraps_dynamic_field_with_if() {
        String sql = new MysqlLegacyUpsertDialect().buildUpsertSql(dynamicMeta);
        assertThat(sql).contains("<if test=\"email != null\">email, </if>");
        assertThat(sql).contains("<if test=\"email != null\">#{email}, </if>");
        assertThat(sql).contains("update_time, ");
        assertThat(sql).doesNotContain("<if test=\"updateTime");
        assertThat(sql).contains("<trim suffixOverrides=\",\">");
    }

    @Test
    void mysql_legacy_single_sql_update_set_dynamic_field() {
        String sql = new MysqlLegacyUpsertDialect().buildUpsertSql(dynamicMeta);
        assertThat(sql).contains("<if test=\"email != null\">email = VALUES(email), </if>");
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
        assertThat(sql).contains("<if test=\"email != null\">email, </if>");
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
        // UPDATE SET 引用 src 别名（与 INSERT VALUES 子句同一行引用）
        assertThat(sql).contains("email = src.email");
    }

    @Test
    void oracle_single_sql_dynamic_field_consistent_across_src_and_insert() {
        String sql = new OracleUpsertDialect().buildUpsertSql(dynamicMeta);
        // email 必须在四个位置使用相同的 <if> 条件包裹：
        // src 子查询列、INSERT 列名、INSERT 值、UPDATE SET。
        // 否则列数量不匹配会导致生成非法 SQL。
        long ifCount = sql.split("<if test=\"email != null\">", -1).length - 1;
        assertThat(ifCount).isEqualTo(4); // src 列、INSERT 列名、INSERT 值、UPDATE SET
    }

    // --- SQL Server ---

    @Test
    void sqlserver_single_sql() {
        String sql = new SqlServerUpsertDialect().buildUpsertSql(staticMeta);
        assertThat(sql).containsIgnoringCase("MERGE INTO t_user AS t");
        assertThat(sql).containsIgnoringCase("AS src");
        assertThat(sql).containsIgnoringCase("WHEN MATCHED THEN UPDATE SET");
        assertThat(sql.trim()).endsWith(";");
        // UPDATE SET 引用 src 别名（与 INSERT VALUES 子句同一行引用）
        assertThat(sql).contains("email = src.email");
    }

    @Test
    void sqlserver_single_sql_uses_select_based_src_for_dynamic_support() {
        String sql = new SqlServerUpsertDialect().buildUpsertSql(dynamicMeta);
        // 单行场景使用基于 SELECT 的 src（而非 VALUES(...) AS src(cols)）以支持动态列
        assertThat(sql).containsIgnoringCase("USING (SELECT");
        assertThat(sql).contains("<if test=\"email != null\">");
    }

    // --- H2 ---

    @Test
    void h2_single_sql() {
        String sql = new H2UpsertDialect().buildUpsertSql(staticMeta);
        assertThat(sql).containsIgnoringCase("MERGE INTO t_user");
        assertThat(sql).containsIgnoringCase("KEY(username)");
        assertThat(sql).contains("#{email}");
    }

    @Test
    void h2_single_sql_dynamic_field() {
        String sql = new H2UpsertDialect().buildUpsertSql(dynamicMeta);
        assertThat(sql).contains("<if test=\"email != null\">email, </if>");
        assertThat(sql).contains("<if test=\"email != null\">#{email}, </if>");
    }

    // --- checkEmpty（NOT_EMPTY 策略） ---

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
        assertThat(sql).contains("name != null and name != ''");
    }

    // --- 多列冲突键处理 ---

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
        assertThat(new SqlServerUpsertDialect().buildUpsertSql(multiColumnMeta))
                .contains("ON (t.tenant_id = src.tenant_id AND t.biz_code = src.biz_code)");
        assertThat(new OracleUpsertDialect().buildUpsertSql(multiColumnMeta))
                .contains("ON (t.tenant_id = src.tenant_id AND t.biz_code = src.biz_code)");
        // 静态元数据下 INSERT 列清单原样带出全部列，不含多列拼接产生的空位
        assertThat(new MysqlLegacyUpsertDialect().buildUpsertSql(multiColumnMeta))
                .contains("(<trim suffixOverrides=\",\">id, tenant_id, biz_code, name, </trim>)");
    }

    @Test
    void single_conflict_column_produces_no_separator_artifacts() {
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
            String sql = dialect.buildUpsertSql(singleColumnMeta);
            assertThat(sql).as(dialect.getClass().getSimpleName() + " single SQL")
                    .doesNotContain("(, ").doesNotContain(", ,");
        }
    }

    // --- 更新但不插入的字段（insertStrategy=NEVER）：SET 赋值必须回退为参数引用 ---

    /**
     * memo 为只更新不插入的字段（paramRef=true）：INSERT 列不含 memo，
     * UPDATE SET 不能用行引用（new./EXCLUDED./src./VALUES()），必须用 #{memo}。
     */
    private UpsertMeta paramRefMeta() {
        Map<String, String> map = new HashMap<>();
        map.put("id", "id");
        map.put("username", "username");
        map.put("memo", "memo");
        return UpsertMeta.builder()
                .tableName("t_param_ref")
                .insertColumns(Arrays.asList("id", "username"))
                .insertFields(Arrays.asList("id", "username"))
                .conflictColumns(Collections.singletonList("username"))
                .updateColumns(Collections.singletonList("memo"))
                .updateFields(Collections.singletonList("memo"))
                .insertFieldMetas(Arrays.asList(
                        FieldMeta.builder().column("id").property("id").dynamic(false).build(),
                        FieldMeta.builder().column("username").property("username").dynamic(false).build()))
                .updateFieldMetas(Collections.singletonList(
                        FieldMeta.builder().column("memo").property("memo").dynamic(false).paramRef(true).build()))
                .fieldToColumnMap(map)
                .build();
    }

    @Test
    void update_only_field_falls_back_to_param_reference_in_single_sql() {
        UpsertMeta meta = paramRefMeta();
        assertThat(new MysqlLegacyUpsertDialect().buildUpsertSql(meta))
                .contains("memo = #{memo}").doesNotContain("memo = VALUES(");
        assertThat(new MysqlUpsertDialect().buildUpsertSql(meta))
                .contains("memo = #{memo}").doesNotContain("memo = new.memo");
        assertThat(new PostgresUpsertDialect().buildUpsertSql(meta))
                .contains("memo = #{memo}").doesNotContain("memo = EXCLUDED.memo");
        assertThat(new OracleUpsertDialect().buildUpsertSql(meta))
                .contains("memo = #{memo}").doesNotContain("memo = src.memo");
        assertThat(new SqlServerUpsertDialect().buildUpsertSql(meta))
                .contains("memo = #{memo}").doesNotContain("memo = src.memo");
    }

    // --- 全动态更新字段为空：冲突时跳过更新 ---

    /** 表名带 schema 前缀，验证各方言的条件分支不依赖 schema 目标限定符。 */
    private UpsertMeta allDynamicUpdateMeta() {
        Map<String, String> map = new HashMap<>();
        map.put("id", "id");
        map.put("username", "username");
        map.put("email", "email");
        map.put("age", "age");
        return UpsertMeta.builder()
                .tableName("public.t_user_all_dyn")
                .insertColumns(Arrays.asList("id", "username", "email", "age"))
                .insertFields(Arrays.asList("id", "username", "email", "age"))
                .conflictColumns(Collections.singletonList("username"))
                .updateColumns(Arrays.asList("email", "age"))
                .updateFields(Arrays.asList("email", "age"))
                .insertFieldMetas(Arrays.asList(
                        FieldMeta.builder().column("id").property("id").dynamic(false).build(),
                        FieldMeta.builder().column("username").property("username").dynamic(false).build(),
                        FieldMeta.builder().column("email").property("email").dynamic(true).build(),
                        FieldMeta.builder().column("age").property("age").dynamic(true).build()))
                .updateFieldMetas(Arrays.asList(
                        FieldMeta.builder().column("email").property("email").dynamic(true).build(),
                        FieldMeta.builder().column("age").property("age").dynamic(true).build()))
                .fieldToColumnMap(map)
                .build();
    }

    /** 仅一个动态更新字段（email），其余为冲突键与静态主键。 */
    private UpsertMeta singleDynamicUpdateMeta() {
        Map<String, String> map = new HashMap<>();
        map.put("username", "username");
        map.put("email", "email");
        return UpsertMeta.builder()
                .tableName("t_user_one_dyn")
                .insertColumns(Arrays.asList("username", "email"))
                .insertFields(Arrays.asList("username", "email"))
                .conflictColumns(Collections.singletonList("username"))
                .updateColumns(Collections.singletonList("email"))
                .updateFields(Collections.singletonList("email"))
                .insertFieldMetas(Arrays.asList(
                        FieldMeta.builder().column("username").property("username").dynamic(false).build(),
                        FieldMeta.builder().column("email").property("email").dynamic(true).build()))
                .updateFieldMetas(Collections.singletonList(
                        FieldMeta.builder().column("email").property("email").dynamic(true).build()))
                .fieldToColumnMap(map)
                .build();
    }

    @Test
    void all_dynamic_update_fields_use_no_op_conflict_action_per_dialect() {
        UpsertMeta meta = allDynamicUpdateMeta();
        String emptyCondition = "email == null and age == null";
        String presentCondition = "email != null or age != null";

        assertThat(new MysqlLegacyUpsertDialect().buildUpsertSql(meta))
                .contains("<if test=\"" + emptyCondition + "\">IGNORE </if>")
                .contains("<if test=\"" + presentCondition + "\"> ON DUPLICATE KEY UPDATE ")
                .doesNotContain("email = email");
        assertThat(new MysqlUpsertDialect().buildUpsertSql(meta))
                .contains("<if test=\"" + emptyCondition + "\">IGNORE </if>")
                .contains("<if test=\"" + presentCondition + "\"> AS new ON DUPLICATE KEY UPDATE ")
                .doesNotContain("email = email");
        assertThat(new PostgresUpsertDialect().buildUpsertSql(meta))
                .contains("<when test=\"" + presentCondition + "\">DO UPDATE SET")
                .contains("<otherwise>DO NOTHING</otherwise>");
        assertThat(new OracleUpsertDialect().buildUpsertSql(meta))
                .contains("<if test=\"" + presentCondition + "\"> WHEN MATCHED THEN UPDATE SET");
        assertThat(new SqlServerUpsertDialect().buildUpsertSql(meta))
                .contains("<if test=\"" + presentCondition + "\"> WHEN MATCHED THEN UPDATE SET");
    }

    @Test
    void single_dynamic_update_field_uses_no_op_conflict_action() {
        String sql = new MysqlLegacyUpsertDialect().buildUpsertSql(singleDynamicUpdateMeta());
        assertThat(sql).contains("<if test=\"email == null\">IGNORE </if>")
                .contains("<if test=\"email != null\"> ON DUPLICATE KEY UPDATE ")
                .doesNotContain("email = email");
    }

    @Test
    void not_empty_update_condition_covers_empty_string() {
        UpsertMeta meta = allDynamicUpdateMeta();
        FieldMeta nickname = FieldMeta.builder().column("nickname").property("nickname")
                .dynamic(true).checkEmpty(true).build();
        UpsertMeta withNotEmpty = UpsertMeta.builder()
                .tableName(meta.getTableName())
                .insertColumns(meta.getInsertColumns())
                .insertFields(meta.getInsertFields())
                .conflictColumns(meta.getConflictColumns())
                .updateColumns(Arrays.asList("email", "age", "nickname"))
                .updateFields(Arrays.asList("email", "age", "nickname"))
                .insertFieldMetas(meta.getInsertFieldMetas())
                .updateFieldMetas(Arrays.asList(
                        meta.getUpdateFieldMetas().get(0), meta.getUpdateFieldMetas().get(1), nickname))
                .fieldToColumnMap(meta.getFieldToColumnMap())
                .build();
        assertThat(new MysqlLegacyUpsertDialect().buildUpsertSql(withNotEmpty))
                .contains("<if test=\"email == null and age == null"
                        + " and (nickname == null or nickname == '')\">IGNORE </if>")
                .doesNotContain("email = email");
    }

    @Test
    void mixed_dynamic_and_static_update_fields_keep_update_clause() {
        // dynamicMeta：email 动态 + update_time 静态，SET 恒非空，保持正常更新子句
        UserEntity entity = new UserEntity();
        entity.setUsername("alice");

        String mysqlLegacy = renderSingleSql(new MysqlLegacyUpsertDialect().buildUpsertSql(dynamicMeta), entity);
        assertThat(mysqlLegacy).contains("ON DUPLICATE KEY UPDATE")
                .contains("update_time = VALUES(update_time)")
                .doesNotContain("INSERT IGNORE");
        String mysqlAlias = renderSingleSql(new MysqlUpsertDialect().buildUpsertSql(dynamicMeta), entity);
        assertThat(mysqlAlias).contains("AS new ON DUPLICATE KEY UPDATE")
                .contains("update_time = new.update_time")
                .doesNotContain("INSERT IGNORE");
        String postgres = renderSingleSql(new PostgresUpsertDialect().buildUpsertSql(dynamicMeta), entity);
        assertThat(postgres).contains("DO UPDATE SET")
                .contains("update_time = EXCLUDED.update_time")
                .doesNotContain("DO NOTHING");
        String oracle = renderSingleSql(new OracleUpsertDialect().buildUpsertSql(dynamicMeta), entity);
        assertThat(oracle).contains("WHEN MATCHED THEN UPDATE SET")
                .contains("update_time = src.update_time");
        String sqlServer = renderSingleSql(new SqlServerUpsertDialect().buildUpsertSql(dynamicMeta), entity);
        assertThat(sqlServer).contains("WHEN MATCHED THEN UPDATE SET")
                .contains("update_time = src.update_time");
    }

    @Test
    void all_static_update_fields_keep_update_clause() {
        // staticMeta：全部静态赋值，生成 SQL 与旧版完全一致
        String mysql = new MysqlLegacyUpsertDialect().buildUpsertSql(staticMeta);
        assertThat(mysql).doesNotContain("email = email")
                .contains("email = VALUES(email), update_time = VALUES(update_time)");
        String oracle = new OracleUpsertDialect().buildUpsertSql(staticMeta);
        assertThat(oracle).doesNotContain("email = t.email");
    }

    @Test
    void empty_update_field_metas_use_no_op_conflict_action() {
        // 手造元数据允许 updateFieldMetas 为空（解析器会在启动期拦截该配置），
        // 方言分支仍应生成可执行的冲突无操作路径。
        UpsertMeta meta = allDynamicUpdateMeta();
        UpsertMeta emptyUpdate = UpsertMeta.builder()
                .tableName(meta.getTableName())
                .insertColumns(meta.getInsertColumns())
                .insertFields(meta.getInsertFields())
                .conflictColumns(meta.getConflictColumns())
                .updateColumns(Collections.emptyList())
                .updateFields(Collections.emptyList())
                .insertFieldMetas(meta.getInsertFieldMetas())
                .updateFieldMetas(Collections.emptyList())
                .fieldToColumnMap(meta.getFieldToColumnMap())
                .build();
        assertThat(new MysqlLegacyUpsertDialect().buildUpsertSql(emptyUpdate))
                .contains("<if test=\"true\">IGNORE </if>")
                .contains("<if test=\"false\"> ON DUPLICATE KEY UPDATE ")
                .contains("<trim suffixOverrides=\",\"></trim>");
    }

    // --- BoundSql 渲染验证：动态字段全被过滤时最终 SQL 仍语法完整 ---

    private static String renderSingleSql(String script, UserEntity entity) {
        Configuration configuration = new Configuration();
        SqlSource sqlSource = new XMLLanguageDriver()
                .createSqlSource(configuration, "<script>" + script + "</script>", UserEntity.class);
        return sqlSource.getBoundSql(entity).getSql().replaceAll("\\s+", " ").trim();
    }

    @Test
    void rendered_mysql_sql_with_all_dynamic_updates_null_uses_no_op_conflict_action() {
        UserEntity entity = new UserEntity();
        entity.setUsername("alice"); // email / age 均为 null

        String rendered = renderSingleSql(
                new MysqlLegacyUpsertDialect().buildUpsertSql(allDynamicUpdateMeta()), entity);
        assertThat(rendered).contains("INSERT IGNORE INTO public.t_user_all_dyn")
                .doesNotContain("ON DUPLICATE KEY UPDATE")
                .doesNotContain("email = email");

        rendered = renderSingleSql(
                new MysqlUpsertDialect().buildUpsertSql(allDynamicUpdateMeta()), entity);
        assertThat(rendered).contains("INSERT IGNORE INTO public.t_user_all_dyn")
                .doesNotContain("ON DUPLICATE KEY UPDATE")
                .doesNotContain("AS new")
                .doesNotContain("email = email");
    }

    @Test
    void rendered_postgres_and_merge_sql_with_all_dynamic_updates_null_skip_update_action() {
        UserEntity entity = new UserEntity();
        entity.setUsername("alice");

        assertThat(renderSingleSql(
                new PostgresUpsertDialect().buildUpsertSql(allDynamicUpdateMeta()), entity))
                .endsWith("ON CONFLICT (username) DO NOTHING");

        String oracle = renderSingleSql(
                new OracleUpsertDialect().buildUpsertSql(allDynamicUpdateMeta()), entity);
        assertThat(oracle).doesNotContain("WHEN MATCHED")
                .contains("WHEN NOT MATCHED");

        String sqlServer = renderSingleSql(
                new SqlServerUpsertDialect().buildUpsertSql(allDynamicUpdateMeta()), entity);
        assertThat(sqlServer).doesNotContain("WHEN MATCHED")
                .contains("WHEN NOT MATCHED");
    }

    @Test
    void rendered_sql_keeps_dynamic_semantics_when_some_fields_have_values() {
        UserEntity entity = new UserEntity();
        entity.setUsername("alice");
        entity.setAge(30); // 仅 age 有值，email 仍被动态排除

        String rendered = renderSingleSql(
                new MysqlLegacyUpsertDialect().buildUpsertSql(allDynamicUpdateMeta()), entity);
        // 有任一字段命中动态条件时，冲突更新子句只包含该字段的真实赋值
        assertThat(rendered).endsWith("ON DUPLICATE KEY UPDATE age = VALUES(age)");
        assertThat(rendered).doesNotContain("email = VALUES(email)");
        // 插入侧的动态列裁剪行为不变：email 不出现在 INSERT 列表中
        assertThat(rendered).contains("INSERT INTO public.t_user_all_dyn ( id, username, age )");
    }

    // --- 冲突键不可被动态 INSERT 策略剔除（基于真实解析器的端到端渲染验证） ---

    /**
     * 实体全字段为 null（含冲突键 username）：普通字段照常被动态排除，
     * 但冲突键是 Upsert 语义必需字段，必须始终出现在 INSERT 列、参数与 ON 条件中。
     * 修复前 username 被默认 NOT_NULL 策略包上 <if>，为 null 时从 SQL 中静默消失。
     */
    @Test
    void rendered_sql_keeps_conflict_key_when_null_across_dialects() {
        UpsertMeta meta = UpsertMetaParser.getMeta(userInfo);
        UserEntity entity = new UserEntity();

        String mysql = renderSingleSql(new MysqlLegacyUpsertDialect().buildUpsertSql(meta), entity);
        assertThat(mysql).contains("INSERT IGNORE INTO t_user ( id, username )")
                .contains("VALUES ( ?, ? )")
                .doesNotContain("( id, username, email")
                .doesNotContain("ON DUPLICATE KEY UPDATE");

        assertThat(renderSingleSql(new MysqlUpsertDialect().buildUpsertSql(meta), entity))
                .contains("INSERT IGNORE INTO t_user ( id, username )")
                .doesNotContain("ON DUPLICATE KEY UPDATE");

        assertThat(renderSingleSql(new PostgresUpsertDialect().buildUpsertSql(meta), entity))
                .contains("INSERT INTO t_user ( id, username )")
                .endsWith("ON CONFLICT (username) DO NOTHING");

        String oracle = renderSingleSql(new OracleUpsertDialect().buildUpsertSql(meta), entity);
        assertThat(oracle).contains("AS id, ? AS username FROM dual")
                .contains("ON (t.username = src.username)")
                .doesNotContain("WHEN MATCHED");

        String sqlServer = renderSingleSql(new SqlServerUpsertDialect().buildUpsertSql(meta), entity);
        assertThat(sqlServer).contains("USING (SELECT ? AS id, ? AS username ) AS src")
                .contains("ON (t.username = src.username)")
                .doesNotContain("WHEN MATCHED");

        assertThat(renderSingleSql(new H2UpsertDialect().buildUpsertSql(meta), entity))
                .contains("MERGE INTO t_user ( id, username )")
                .contains("KEY(username)")
                .contains("VALUES ( ?, ? )");
    }

    /**
     * 冲突键有值时行为不变：与普通字段一样原样出现在 SQL 中。
     */
    @Test
    void rendered_sql_keeps_conflict_key_when_present() {
        UpsertMeta meta = UpsertMetaParser.getMeta(userInfo);
        UserEntity entity = new UserEntity();
        entity.setUsername("alice");

        String mysql = renderSingleSql(new MysqlLegacyUpsertDialect().buildUpsertSql(meta), entity);
        assertThat(mysql).contains("INSERT IGNORE INTO t_user ( id, username )")
                .contains("VALUES ( ?, ? )");
    }
}
