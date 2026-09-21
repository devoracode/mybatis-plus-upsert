package io.github.devoracode.upsert.test.mapper;

import io.github.devoracode.upsert.test.TestApplication;
import io.github.devoracode.upsert.test.support.AutoUserEntity;
import io.github.devoracode.upsert.test.support.AutoUserMapper;
import io.github.devoracode.upsert.test.support.UserEntity;
import io.github.devoracode.upsert.test.support.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 数据库生成主键的回填行为测试（H2 MySQL 模式）。
 *
 * <p>设计承诺：
 * <ul>
 *   <li>单条 {@code upsert}：走 MyBatis-Plus 原生 {@code Jdbc3KeyGenerator} 机制回填 AUTO 主键；</li>
 *   <li>{@code upsert(Collection)}：逐条单行 SQL 在 BATCH 执行器下提交，主键在批次刷新后回填；</li>
 *   <li>无主键生成策略的实体（INPUT 主键）：用户提供的 id 原样保留。</li>
 * </ul>
 */
@SpringBootTest(classes = TestApplication.class)
@Transactional
@Rollback
class UpsertAutoIdBackfillTest {

    @Autowired
    private AutoUserMapper autoUserMapper;

    @Autowired
    private UserMapper userMapper;

    @BeforeEach
    void clean() {
        autoUserMapper.delete(null);
        userMapper.delete(null);
    }

    @Test
    void single_upsert_backfills_database_generated_id() {
        AutoUserEntity user = AutoUserEntity.builder()
                .username("alice")
                .email("alice@example.com")
                .build();
        assertThat(user.getId()).isNull();

        autoUserMapper.upsert(user);

        assertThat(user.getId()).isNotNull();
        assertThat(autoUserMapper.selectById(user.getId())).isNotNull();
    }

    @Test
    void single_upsert_on_conflict_still_updates_and_keeps_existing_row_id() {
        AutoUserEntity inserted = AutoUserEntity.builder().username("bob").email("old@example.com").build();
        autoUserMapper.upsert(inserted);
        Long originalId = inserted.getId();
        assertThat(originalId).isNotNull();

        AutoUserEntity conflicting = AutoUserEntity.builder().username("bob").email("new@example.com").build();
        int rows = autoUserMapper.upsert(conflicting);

        List<AutoUserEntity> all = autoUserMapper.selectList(null);
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getId()).isEqualTo(originalId);
        assertThat(all.get(0).getEmail()).isEqualTo("new@example.com");
        assertThat(rows).isGreaterThanOrEqualTo(0);
    }

    @Test
    void collection_upsert_backfills_ids_after_batch_flush() {
        List<AutoUserEntity> users = Arrays.asList(
                AutoUserEntity.builder().username("carol").email("carol@example.com").build(),
                AutoUserEntity.builder().username("dave").email("dave@example.com").build());

        autoUserMapper.upsert(users);

        // BATCH 执行器下生成键在 flushStatements 时写回，执行完毕后两条主键均应可见
        assertThat(users).allSatisfy(u -> assertThat(u.getId()).isNotNull());
        assertThat(users.get(0).getId()).isNotEqualTo(users.get(1).getId());
    }

    @Test
    void collection_upsert_persists_every_row_with_distinct_generated_ids() {
        List<AutoUserEntity> users = Arrays.asList(
                AutoUserEntity.builder().username("erin").email("erin@example.com").build(),
                AutoUserEntity.builder().username("frank").email("frank@example.com").build());

        autoUserMapper.upsert(users);

        // 逐条提交的都是单行语句，因此每一行都拿到自己的生成主键并落库
        assertThat(autoUserMapper.selectList(null)).hasSize(2)
                .extracting(AutoUserEntity::getId)
                .doesNotContainNull().doesNotHaveDuplicates();
    }

    @Test
    void non_auto_id_entity_keeps_its_user_provided_id() {
        UserEntity user = UserEntity.builder().id("fixed-id").name("grace").age(30).build();

        userMapper.upsert(user);

        // 无自增主键：不配置 KeyGenerator，用户提供的 id 原样保留
        assertThat(user.getId()).isEqualTo("fixed-id");
        assertThat(userMapper.selectById("fixed-id")).isNotNull();
    }
}
