package io.github.devoracode.upsert.test.mapper;

import io.github.devoracode.upsert.test.TestApplication;
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
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TestApplication.class)
@Transactional
@Rollback
class UpsertMapperTest {

    @Autowired
    private UserMapper userMapper;

    private final LocalDateTime NOW = LocalDateTime.of(2024, 1, 1, 0, 0);

    @BeforeEach
    void clean() {
        userMapper.delete(null);
    }

    @Test
    void upsert_insert_when_not_exists() {
        UserEntity user = buildUser("1", "alice", "alice@example.com", 25);
        int rows = userMapper.upsert(user);
        assertThat(rows).isGreaterThan(0);

        UserEntity saved = userMapper.selectById("1");
        assertThat(saved).isNotNull();
        assertThat(saved.getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void upsert_update_when_conflict() {
        userMapper.upsert(buildUser("1", "alice", "old@example.com", 25));

        UserEntity updated = buildUser("1", "alice", "new@example.com", 30);
        userMapper.upsert(updated);

        List<UserEntity> users = userMapper.selectList(null);
        assertThat(users).hasSize(1);
        assertThat(users.get(0).getEmail()).isEqualTo("new@example.com");
        assertThat(users.get(0).getAge()).isEqualTo(30);
    }

    @Test
    void upsertBatch_insert_and_update() {
        userMapper.upsert(buildUser("1", "alice", "old@example.com", 20));

        List<UserEntity> list = Arrays.asList(
                buildUser("1", "alice", "new@example.com", 99),
                buildUser("2", "bob",   "bob@example.com", 30)
        );
        userMapper.upsertBatch(list);

        List<UserEntity> all = userMapper.selectList(null);
        assertThat(all).hasSize(2);

        UserEntity alice = all.stream().filter(u -> "alice".equals(u.getName())).findFirst()
                .orElseThrow(() -> new AssertionError("alice not found"));
        assertThat(alice.getEmail()).isEqualTo("new@example.com");
        assertThat(alice.getAge()).isEqualTo(99);
    }

    @Test
    void upsertCollection_executes_via_batch_executor() {
        List<UserEntity> list = Arrays.asList(
                buildUser("1", "alice", "a@example.com", 20),
                buildUser("2", "bob",   "b@example.com", 30),
                buildUser("3", "carol", "c@example.com", 40)
        );
        userMapper.upsert(list);
        assertThat(userMapper.selectList(null)).hasSize(3);
    }

    @Test
    void upsertCollection_empty_list_returns_empty_result() {
        assertThat(userMapper.upsert(Collections.emptyList())).isEmpty();
    }

    @Test
    void upsert_null_field_does_not_overwrite_existing_value() {
        userMapper.upsert(buildUser("1", "alice", "original@example.com", 25));

        UserEntity partialUpdate = UserEntity.builder()
                .id("1")
                .name("alice")
                .email(null)
                .age(99)
                .createTime(NOW)
                .updateTime(NOW)
                .build();
        userMapper.upsert(partialUpdate);

        UserEntity result = userMapper.selectList(null).get(0);
        assertThat(result.getEmail()).isEqualTo("original@example.com");
        assertThat(result.getAge()).isEqualTo(99);
    }

    @Test
    void upsert_insert_with_null_field_relies_on_database_default() {
        UserEntity user = UserEntity.builder()
                .id("1")
                .name("dave")
                .email("dave@example.com")
                .age(null)
                .createTime(NOW)
                .updateTime(NOW)
                .build();
        userMapper.upsert(user);

        UserEntity saved = userMapper.selectById("1");
        assertThat(saved.getAge()).isNull();
    }

    @Test
    void upsert_auto_fills_create_and_update_time_when_null() {
        UserEntity user = UserEntity.builder()
                .id("1")
                .name("filltest")
                .email("fill@example.com")
                .age(20)
                .createTime(null)
                .updateTime(null)
                .build();
        userMapper.upsert(user);

        UserEntity saved = userMapper.selectById("1");
        assertThat(saved.getCreateTime()).isNotNull();
        assertThat(saved.getUpdateTime()).isNotNull();
    }

    @Test
    void upsert_auto_fills_update_time_on_conflict_update() {
        userMapper.upsert(buildUser("1", "alice", "old@example.com", 25));

        UserEntity conflict = UserEntity.builder()
                .id("1")
                .name("alice")
                .email("new@example.com")
                .age(30)
                .createTime(null)
                .updateTime(null)
                .build();
        userMapper.upsert(conflict);

        UserEntity saved = userMapper.selectList(null).get(0);
        assertThat(saved.getUpdateTime()).isNotNull();
    }

    @Test
    void upsertBatch_auto_fills_when_null() {
        List<UserEntity> list = Arrays.asList(
                UserEntity.builder()
                        .id("1")
                        .name("batchfill1")
                        .email("bf1@example.com")
                        .age(20)
                        .createTime(null)
                        .updateTime(null)
                        .build(),
                UserEntity.builder()
                        .id("2")
                        .name("batchfill2")
                        .email("bf2@example.com")
                        .age(30)
                        .createTime(null)
                        .updateTime(null)
                        .build()
        );
        userMapper.upsertBatch(list);

        UserEntity u1 = userMapper.selectById("1");
        UserEntity u2 = userMapper.selectById("2");
        assertThat(u1.getCreateTime()).isNotNull();
        assertThat(u1.getUpdateTime()).isNotNull();
        assertThat(u2.getCreateTime()).isNotNull();
        assertThat(u2.getUpdateTime()).isNotNull();
    }

    private UserEntity buildUser(String id, String name, String email, int age) {
        return UserEntity.builder()
                .id(id)
                .name(name)
                .email(email)
                .age(age)
                .createTime(NOW)
                .updateTime(NOW)
                .build();
    }
}
