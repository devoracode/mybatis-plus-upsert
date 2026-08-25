package io.github.devoracode.upsert.test.autoconfigure;

import io.github.devoracode.upsert.autoconfigure.UpsertDynamicProperties;
import io.github.devoracode.upsert.core.fill.FillStrategy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 动态数据源属性中 {@code fill-strategy}
 * 解析逻辑的单元测试（无需 Spring 上下文）。
 */
class UpsertDynamicPropertiesTest {

    @Test
    void default_resolves_to_insert_update() {
        assertThat(new UpsertDynamicProperties().resolveFillStrategy()).isEqualTo(FillStrategy.INSERT_UPDATE);
    }

    @Test
    void null_strategy_defaults_to_insert_update() {
        UpsertDynamicProperties properties = new UpsertDynamicProperties();
        properties.setFillStrategy(null);
        assertThat(properties.resolveFillStrategy()).isEqualTo(FillStrategy.INSERT_UPDATE);
    }

    @Test
    void explicit_fill_strategy_is_returned_as_is() {
        UpsertDynamicProperties properties = new UpsertDynamicProperties();
        properties.setFillStrategy(FillStrategy.NONE);
        assertThat(properties.resolveFillStrategy()).isEqualTo(FillStrategy.NONE);

        properties.setFillStrategy(FillStrategy.INSERT_UPDATE);
        assertThat(properties.resolveFillStrategy()).isEqualTo(FillStrategy.INSERT_UPDATE);
    }
}
