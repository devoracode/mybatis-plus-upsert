package io.github.devoracode.upsert.test.core;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import io.github.devoracode.upsert.core.FieldMeta;
import io.github.devoracode.upsert.core.UpsertMeta;
import io.github.devoracode.upsert.core.UpsertMetaParser;
import io.github.devoracode.upsert.exception.UpsertMetaException;
import io.github.devoracode.upsert.test.support.AutoIdConflictKeyEntity;
import io.github.devoracode.upsert.test.support.AutoIdEntity;
import io.github.devoracode.upsert.test.support.ConflictOnlyEntity;
import io.github.devoracode.upsert.test.support.MultiConflictKeyEntity;
import io.github.devoracode.upsert.test.support.NeverInsertConflictKeyEntity;
import io.github.devoracode.upsert.test.support.UserEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UpsertMetaParserTest {

    // UpsertMetaParser 只解析注入期递入的 TableInfo（无全局缓存、不查注册表），
    // 因此这里用 initTableInfo 手动构造各实体的 TableInfo 并持有引用传给 getMeta；
    // 无需 Spring 上下文（也就无需 @SpringBootTest）。
    private static TableInfo userInfo;
    private static TableInfo autoIdInfo;
    private static TableInfo autoIdConflictKeyInfo;
    private static TableInfo conflictOnlyInfo;
    private static TableInfo neverInsertConflictKeyInfo;
    private static TableInfo multiConflictKeyInfo;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        userInfo = TableInfoHelper.initTableInfo(assistant, UserEntity.class);
        autoIdInfo = TableInfoHelper.initTableInfo(assistant, AutoIdEntity.class);
        autoIdConflictKeyInfo = TableInfoHelper.initTableInfo(assistant, AutoIdConflictKeyEntity.class);
        conflictOnlyInfo = TableInfoHelper.initTableInfo(assistant, ConflictOnlyEntity.class);
        neverInsertConflictKeyInfo = TableInfoHelper.initTableInfo(assistant, NeverInsertConflictKeyEntity.class);
        multiConflictKeyInfo = TableInfoHelper.initTableInfo(assistant, MultiConflictKeyEntity.class);
    }

    @Test
    void parse_default_update_columns() {
        UpsertMeta meta = UpsertMetaParser.getMeta(userInfo);

        assertThat(meta.getTableName()).isEqualTo("t_user");
        assertThat(meta.getConflictColumns()).containsExactly("username");
        assertThat(meta.getUpdateColumns()).contains("email", "age", "update_time");
        assertThat(meta.getUpdateColumns()).doesNotContain("username", "create_time");
    }

    @Test
    void parse_populates_field_to_column_map() {
        UpsertMeta meta = UpsertMetaParser.getMeta(userInfo);
        assertThat(meta.getFieldToColumnMap()).containsKey("username");
        assertThat(meta.getFieldToColumnMap().get("updateTime")).isEqualTo("update_time");
    }

    @Test
    void parse_default_field_strategy_follows_mp_global_not_null() {
        UpsertMeta meta = UpsertMetaParser.getMeta(userInfo);

        boolean emailDynamic = meta.getUpdateFieldMetas().stream()
                .filter(fm -> "email".equals(fm.getProperty()))
                .findFirst().orElseThrow(() -> new AssertionError("FieldMeta not found")).isDynamic();
        assertThat(emailDynamic).isTrue();

        boolean ageInsertDynamic = meta.getInsertFieldMetas().stream()
                .filter(fm -> "age".equals(fm.getProperty()))
                .findFirst().orElseThrow(() -> new AssertionError("FieldMeta not found")).isDynamic();
        assertThat(ageInsertDynamic).isTrue();
    }

    @Test
    void parse_primary_key_is_never_dynamic() {
        UpsertMeta meta = UpsertMetaParser.getMeta(userInfo);
        boolean idDynamic = meta.getInsertFieldMetas().stream()
                .filter(fm -> "id".equals(fm.getProperty()))
                .findFirst().orElseThrow(() -> new AssertionError("FieldMeta not found")).isDynamic();
        assertThat(idDynamic).isFalse();
    }

    @Test
    void parse_conflict_key_is_never_dynamic_insert() {
        // 冲突键是 Upsert 语义必需字段：不能被动态 INSERT 策略（含默认 NOT_NULL）
        // 包上 <if> 判空——否则冲突键为 null 时会从 INSERT 列与参数中被静默剔除，
        // 与 ON 冲突判断不一致
        UpsertMeta meta = UpsertMetaParser.getMeta(userInfo);
        boolean usernameDynamic = meta.getInsertFieldMetas().stream()
                .filter(fm -> "username".equals(fm.getProperty()))
                .findFirst().orElseThrow(() -> new AssertionError("FieldMeta not found")).isDynamic();
        assertThat(usernameDynamic).isFalse();
        assertThat(meta.getInsertColumns()).contains("username");
        assertThat(meta.getInsertFields()).contains("username");
    }

    @Test
    void parse_multiple_conflict_keys_respect_order() {
        UpsertMeta meta = UpsertMetaParser.getMeta(multiConflictKeyInfo);
        assertThat(meta.getConflictColumns()).containsExactly("tenant_id", "biz_code");
        // 多个冲突键同样必须始终出现在 INSERT 中
        assertThat(meta.getInsertFields()).contains("tenantId", "bizCode");
        assertThat(meta.getInsertFieldMetas().stream()
                .filter(fm -> "tenantId".equals(fm.getProperty()) || "bizCode".equals(fm.getProperty())))
                .allSatisfy(fm -> assertThat(fm.isDynamic()).isFalse());
    }

    @Test
    void insert_strategy_never_with_conflict_key_fails_fast() {
        // @ConflictKey 与 insertStrategy=NEVER 是非法组合：
        // 字段永不参与 INSERT 但又是冲突判断依据，必须在启动解析阶段报错
        assertThatThrownBy(() -> UpsertMetaParser.getMeta(neverInsertConflictKeyInfo))
                .isInstanceOf(UpsertMetaException.class)
                .hasMessageContaining("NeverInsertConflictKeyEntity")
                .hasMessageContaining("username")
                .hasMessageContaining("NEVER");
    }

    @Test
    void auto_increment_primary_key_is_excluded_from_insert_columns() {
        UpsertMeta meta = UpsertMetaParser.getMeta(autoIdInfo);
        // AUTO 主键由数据库生成，不得进入 INSERT 列表——
        // 显式插入 NULL 在 PostgreSQL serial 列等场景下会违反 NOT NULL 约束
        assertThat(meta.getInsertColumns()).doesNotContain("id");
        assertThat(meta.getInsertFields()).doesNotContain("id");
        assertThat(meta.getConflictColumns()).containsExactly("username");
    }

    @Test
    void conflict_key_on_auto_increment_primary_key_fails_fast() {
        assertThatThrownBy(() -> UpsertMetaParser.getMeta(autoIdConflictKeyInfo))
                .isInstanceOf(UpsertMetaException.class)
                .hasMessageContaining("auto-increment");
    }

    @Test
    void entity_without_any_updatable_column_fails_fast() {
        // 只有冲突键、无任何可更新字段的实体必然产生空 UPDATE SET——
        // 属于配置错误，应在启动解析阶段抛出，而不是留到运行期报 SQL 语法错误
        assertThatThrownBy(() -> UpsertMetaParser.getMeta(conflictOnlyInfo))
                .isInstanceOf(UpsertMetaException.class)
                .hasMessageContaining("no updatable column");
    }
}
