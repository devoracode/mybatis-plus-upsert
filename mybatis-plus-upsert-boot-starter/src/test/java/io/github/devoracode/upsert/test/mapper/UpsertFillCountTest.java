package io.github.devoracode.upsert.test.mapper;

import io.github.devoracode.upsert.test.TestApplication;
import io.github.devoracode.upsert.test.TestApplication.CountingMetaObjectHandler;
import io.github.devoracode.upsert.test.support.NonUpsertEntity;
import io.github.devoracode.upsert.test.support.NonUpsertMapper;
import io.github.devoracode.upsert.test.support.UserEntity;
import io.github.devoracode.upsert.test.support.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TestApplication.class)
@Transactional
@Rollback
class UpsertFillCountTest {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CountingMetaObjectHandler countingHandler;

    @Autowired
    private NonUpsertMapper nonUpsertMapper;

    @BeforeEach
    void clean() {
        userMapper.delete(null);
        countingHandler.reset();
    }

    @Test
    void upsert_invokes_fill_handlers() {
        UserEntity user = UserEntity.builder()
                .id("1")
                .name("fillcount")
                .email("fillcount@example.com")
                .age(20)
                .createTime(null)
                .updateTime(null)
                .build();

        userMapper.upsert(user);

        // PreFillSqlSource 在 SQL 绑定前调用 insertFill 和 updateFill；
        // MyBatis-Plus 的原生 MybatisParameterHandler 还会在参数处理期间
        // 额外调用一次 insertFill（严格填充的空操作），因此
        // insertFill 计数“至少一次”，而 updateFill 恰好
        // 被调用一次——仅由预绑定处理器调用。
        assertThat(countingHandler.getInsertFillCount()).isGreaterThanOrEqualTo(1);
        assertThat(countingHandler.getUpdateFillCount()).isEqualTo(1);

        UserEntity saved = userMapper.selectById("1");
        assertThat(saved.getCreateTime()).isNotNull();
        assertThat(saved.getUpdateTime()).isNotNull();
    }

    @Test
    void upsert_updateFill_invoked_on_conflict_update() {
        userMapper.upsert(UserEntity.builder()
                .id("1")
                .name("conflictuser")
                .email("old@example.com")
                .age(25)
                .createTime(LocalDateTime.of(2024, 1, 1, 0, 0))
                .updateTime(LocalDateTime.of(2024, 1, 1, 0, 0))
                .build());

        countingHandler.reset();

        UserEntity conflict = UserEntity.builder()
                .id("1")
                .name("conflictuser")
                .email("new@example.com")
                .age(30)
                .createTime(null)
                .updateTime(null)
                .build();
        userMapper.upsert(conflict);

        assertThat(countingHandler.getInsertFillCount()).isGreaterThanOrEqualTo(1);
        assertThat(countingHandler.getUpdateFillCount()).isEqualTo(1);

        UserEntity saved = userMapper.selectById("1");
        assertThat(saved.getUpdateTime()).isNotNull();
    }

    @Test
    void upsertBatch_fills_every_entity_in_collection() {
        List<UserEntity> list = Arrays.asList(
                UserEntity.builder().id("1").name("batchfill1").email("bf1@example.com").age(20).build(),
                UserEntity.builder().id("2").name("batchfill2").email("bf2@example.com").age(30).build()
        );

        userMapper.upsertBatch(list);

        // 预绑定处理器遍历 "list" 集合，updateFill 每实体恰好一次（共 2 次）：
        // updateFill 对 INSERT 命令原生从不调用，本库预绑定是唯一调用点。
        // insertFill 则每实体两次（预绑定 + 原生遍历集合），故只断言下限。
        assertThat(countingHandler.getInsertFillCount()).isGreaterThanOrEqualTo(2);
        assertThat(countingHandler.getUpdateFillCount()).isEqualTo(2);

        assertThat(userMapper.selectById("1").getCreateTime()).isNotNull();
        assertThat(userMapper.selectById("2").getCreateTime()).isNotNull();
    }

    @Test
    void upsertBatch_insertFill_invoked_twice_per_entity_with_source_breakdown() {
        List<UserEntity> list = Arrays.asList(
                UserEntity.builder().id("1").name("once1").email("once1@example.com").age(20).build(),
                UserEntity.builder().id("2").name("once2").email("once2@example.com").age(21).build(),
                UserEntity.builder().id("3").name("once3").email("once3@example.com").age(22).build()
        );

        userMapper.upsertBatch(list);

        // 实测：批量 upsert 时 insertFill 每个实体仍被调用两次（3 实体 = 6 次）：
        // 一次来自本库预绑定处理器（UpsertFillProcessor），一次来自 MP 原生
        // MybatisParameterHandler——后者在 3.5.9 中同样会遍历集合参数。
        // 而 updateFill 只有预绑定处理器调用（每实体一次，共 3 次）。
        assertThat(countingHandler.getInsertFillCount()).isEqualTo(6);
        assertThat(countingHandler.getUpdateFillCount()).isEqualTo(3);

        // 验证调用来源分布：恰好 3 次 pre-bind + 3 次 native。
        // 两次调用均安全：strictInsertFill 跳过已有值的字段，native 那次是空操作。
        assertThat(countingHandler.getInsertFillSources())
                .hasSize(6)
                .containsOnly("pre-bind", "native")
                .filteredOn("pre-bind"::equals).hasSize(3)
                .hasSize(3);
        assertThat(countingHandler.getInsertFillSources())
                .filteredOn("native"::equals).hasSize(3);
    }

    @Test
    void unrelated_mapper_method_named_upsert_does_not_invoke_insert_fill() {
        userMapper.upsert(UserEntity.builder()
                .id("1")
                .name("original")
                .email("original@example.com")
                .age(20)
                .build());
        countingHandler.reset();

        NonUpsertEntity entity = new NonUpsertEntity();
        entity.setId("1");
        entity.setName("updated");

        nonUpsertMapper.upsert(entity);

        assertThat(countingHandler.getInsertFillCount()).isZero();
        assertThat(entity.getCreateTime()).isNull();
    }
}
