package io.github.devoracode.upsert.test.core;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import io.github.devoracode.upsert.core.UpsertMeta;
import io.github.devoracode.upsert.core.UpsertMetaParser;
import io.github.devoracode.upsert.test.support.ScopeEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link UpsertMetaParser} 的上下文隔离：同一实体类在多个
 * MyBatis {@code Configuration}（多个 Spring ApplicationContext 的等价物）下
 * 各自解析出各自上下文的元数据，绝不串用。
 */
class UpsertMetaParserScopeTest {

    /*
     * ScopeEntity 不带 @TableName：表名 = 该 Configuration 的 table-prefix + 类名推导
     * （MP 仅在 @TableName 缺失或留空时追加全局前缀），因此不同前缀的 Configuration
     * 注册出的 TableInfo 表名可区分。MP 的全局注册表按实体类后注册覆盖，
     * 注册完成后全局注册表持有最后一次注册的那份 TableInfo。
     */
    private static TableInfo initUnder(String tablePrefix) {
        MybatisConfiguration configuration = new MybatisConfiguration();
        if (tablePrefix != null) {
            GlobalConfigUtils.getGlobalConfig(configuration).getDbConfig().setTablePrefix(tablePrefix);
        }
        return TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), ScopeEntity.class);
    }

    @Test
    void same_entity_two_configurations_resolve_independently() {
        TableInfo infoA = initUnder("a_");
        TableInfo infoB = initUnder("b_");
        // 此时 MP 全局注册表已被后注册的 infoB 覆盖；
        // 解析只认递入的 TableInfo，不回查注册表，因此 infoA 必须仍解析出自己的表名
        UpsertMeta metaA = UpsertMetaParser.getMeta(infoA);
        UpsertMeta metaB = UpsertMetaParser.getMeta(infoB);

        assertThat(metaA.getTableName()).isEqualTo("a_scope_entity");
        assertThat(metaB.getTableName()).isEqualTo("b_scope_entity");

        // 同一 TableInfo 重复解析结果稳定，且互不串用
        assertThat(UpsertMetaParser.getMeta(infoA).getTableName()).isEqualTo("a_scope_entity");
        assertThat(UpsertMetaParser.getMeta(infoB).getTableName()).isEqualTo("b_scope_entity");
    }

    @Test
    void concurrent_parsing_keeps_each_configuration_isolated() throws Exception {
        final TableInfo infoA = initUnder("c_");
        final TableInfo infoB = initUnder("d_");
        final int rounds = 8;
        final CyclicBarrier barrier = new CyclicBarrier(rounds * 2);
        final List<Future<String>> futures = new ArrayList<>();

        ExecutorService pool = Executors.newFixedThreadPool(rounds * 2);
        try {
            for (int i = 0; i < rounds; i++) {
                futures.add(pool.submit(() -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    return UpsertMetaParser.getMeta(infoA).getTableName();
                }));
                futures.add(pool.submit(() -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    return UpsertMetaParser.getMeta(infoB).getTableName();
                }));
            }
            for (int i = 0; i < futures.size(); i++) {
                String tableName = futures.get(i).get(10, TimeUnit.SECONDS);
                assertThat(tableName).isEqualTo(i % 2 == 0 ? "c_scope_entity" : "d_scope_entity");
            }
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * 结构性守卫：解析器不得重新引入任何静态可变状态（缓存），
     * 否则跨 Configuration 串用的通道会复活。jacoco 等工具注入的合成字段除外。
     */
    @Test
    void parser_holds_no_static_state() {
        for (Field field : UpsertMetaParser.class.getDeclaredFields()) {
            if (field.isSynthetic()) {
                continue;
            }
            assertThat(Modifier.isStatic(field.getModifiers()))
                    .as("UpsertMetaParser 应为无状态类，不得有静态字段: " + field.getName())
                    .isFalse();
        }
    }
}
