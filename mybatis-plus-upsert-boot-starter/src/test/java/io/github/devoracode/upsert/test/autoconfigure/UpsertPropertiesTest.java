package io.github.devoracode.upsert.test.autoconfigure;

import io.github.devoracode.upsert.autoconfigure.UpsertProperties;
import io.github.devoracode.upsert.core.fill.FillStrategy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code fill-strategy}
 * 解析逻辑的单元测试（无需 Spring 上下文）。
 */
class UpsertPropertiesTest {

    @Test
    void default_resolves_to_insert_update() {
        assertThat(new UpsertProperties().resolveFillStrategy()).isEqualTo(FillStrategy.INSERT_UPDATE);
    }

    @Test
    void null_strategy_defaults_to_insert_update() {
        UpsertProperties properties = new UpsertProperties();
        properties.setFillStrategy(null);
        assertThat(properties.resolveFillStrategy()).isEqualTo(FillStrategy.INSERT_UPDATE);
    }

    @Test
    void explicit_fill_strategy_is_returned_as_is() {
        UpsertProperties properties = new UpsertProperties();
        properties.setFillStrategy(FillStrategy.INSERT);
        assertThat(properties.resolveFillStrategy()).isEqualTo(FillStrategy.INSERT);

        properties.setFillStrategy(FillStrategy.NONE);
        assertThat(properties.resolveFillStrategy()).isEqualTo(FillStrategy.NONE);
    }
}
