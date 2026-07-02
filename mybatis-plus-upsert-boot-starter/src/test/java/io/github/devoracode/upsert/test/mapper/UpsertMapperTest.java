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
        UserEntity user = buildUser(1L, "alice", "alice@example.com", 25);
        int rows = userMapper.upsert(user);
        assertThat(rows).isGreaterThan(0);

        UserEntity saved = userMapper.selectById(1L);
        assertThat(saved).isNotNull();
        assertThat(saved.getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void upsert_update_when_conflict() {
        userMapper.upsert(buildUser(1L, "alice", "old@example.com", 25));

        UserEntity updated = buildUser(2L, "alice", "new@example.com", 30);
        userMapper.upsert(updated);

        List<UserEntity> users = userMapper.selectList(null);
        assertThat(users).hasSize(1);
        assertThat(users.get(0).getEmail()).isEqualTo("new@example.com");
        assertThat(users.get(0).getAge()).isEqualTo(30);
    }

    @Test
    void upsertBatch_insert_and_update() {
        userMapper.upsert(buildUser(1L, "alice", "old@example.com", 20));

        List<UserEntity> list = Arrays.asList(
                buildUser(1L, "alice", "new@example.com", 99),
                buildUser(2L, "bob",   "bob@example.com", 30)
        );
        userMapper.upsertBatch(list);

        List<UserEntity> all = userMapper.selectList(null);
        assertThat(all).hasSize(2);

        UserEntity alice = all.stream().filter(u -> "alice".equals(u.getUsername())).findFirst()
                .orElseThrow(() -> new AssertionError("alice not found"));
        assertThat(alice.getEmail()).isEqualTo("new@example.com");
        assertThat(alice.getAge()).isEqualTo(99);
    }

    @Test
    void upsertCollection_executes_via_batch_executor() {
        List<UserEntity> list = Arrays.asList(
                buildUser(1L, "alice", "a@example.com", 20),
                buildUser(2L, "bob",   "b@example.com", 30),
                buildUser(3L, "carol", "c@example.com", 40)
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
        userMapper.upsert(buildUser(1L, "alice", "original@example.com", 25));

        UserEntity partialUpdate = UserEntity.builder()
                .id(2L)
                .username("alice")
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
                .id(1L)
                .username("dave")
                .email("dave@example.com")
                .age(null)
                .createTime(NOW)
                .updateTime(NOW)
                .build();
        userMapper.upsert(user);

        UserEntity saved = userMapper.selectById(1L);
        assertThat(saved.getAge()).isNull();
    }

    private UserEntity buildUser(Long id, String username, String email, int age) {
        return UserEntity.builder()
                .id(id)
                .username(username)
                .email(email)
                .age(age)
                .createTime(NOW)
                .updateTime(NOW)
                .build();
    }
}
