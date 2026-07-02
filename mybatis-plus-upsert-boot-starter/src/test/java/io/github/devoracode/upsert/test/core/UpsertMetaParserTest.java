package io.github.devoracode.upsert.test.core;

import io.github.devoracode.upsert.core.FieldMeta;
import io.github.devoracode.upsert.core.UpsertMeta;
import io.github.devoracode.upsert.core.UpsertMetaParser;
import io.github.devoracode.upsert.test.TestApplication;
import io.github.devoracode.upsert.test.support.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TestApplication.class)
class UpsertMetaParserTest {

    @Test
    void parse_default_update_columns() {
        UpsertMeta meta = UpsertMetaParser.getMeta(UserEntity.class);

        assertThat(meta.getTableName()).isEqualTo("t_user");
        assertThat(meta.getConflictColumns()).containsExactly("username");
        assertThat(meta.getUpdateColumns()).contains("email", "age", "update_time");
        assertThat(meta.getUpdateColumns()).doesNotContain("username", "create_time");
    }

    @Test
    void parse_populates_field_to_column_map() {
        UpsertMeta meta = UpsertMetaParser.getMeta(UserEntity.class);
        assertThat(meta.getFieldToColumnMap()).containsKey("username");
        assertThat(meta.getFieldToColumnMap().get("updateTime")).isEqualTo("update_time");
    }

    @Test
    void parse_default_field_strategy_follows_mp_global_not_null() {
        UpsertMeta meta = UpsertMetaParser.getMeta(UserEntity.class);

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
        UpsertMeta meta = UpsertMetaParser.getMeta(UserEntity.class);
        boolean idDynamic = meta.getInsertFieldMetas().stream()
                .filter(fm -> "id".equals(fm.getProperty()))
                .findFirst().orElseThrow(() -> new AssertionError("FieldMeta not found")).isDynamic();
        assertThat(idDynamic).isFalse();
    }
}
