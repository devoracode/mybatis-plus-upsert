package io.github.devoracode.upsert.example.postgresql;

import io.github.devoracode.upsert.example.postgresql.entity.User;
import io.github.devoracode.upsert.example.postgresql.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class UpsertMapperTest {

    @Autowired
    private UserMapper userMapper;

    @BeforeEach
    void setUp() {
        userMapper.delete(null);
    }

    @Test
    void upsert_insert_new_user() {
        User user = User.builder()
                .username("alice")
                .email("alice@example.com")
                .age(25)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        int result = userMapper.upsert(user);

        assertThat(result).isEqualTo(1);

        User saved = userMapper.selectList(null).get(0);
        assertThat(saved.getUsername()).isEqualTo("alice");
        assertThat(saved.getEmail()).isEqualTo("alice@example.com");
        assertThat(saved.getAge()).isEqualTo(25);
    }

    @Test
    void upsert_update_existing_user() {
        User user = User.builder()
                .username("alice")
                .email("alice@example.com")
                .age(25)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        userMapper.insert(user);

        User updateUser = User.builder()
                .username("alice")
                .email("new@example.com")
                .age(30)
                .build();

        int result = userMapper.upsert(updateUser);

        assertThat(result).isEqualTo(1);

        User saved = userMapper.selectList(null).get(0);
        assertThat(saved.getUsername()).isEqualTo("alice");
        assertThat(saved.getEmail()).isEqualTo("new@example.com");
        assertThat(saved.getAge()).isEqualTo(30);
    }

    @Test
    void upsertBatch_insert_multiple_users() {
        User user1 = User.builder()
                .username("alice")
                .email("alice@example.com")
                .age(25)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        User user2 = User.builder()
                .username("bob")
                .email("bob@example.com")
                .age(30)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        int result = userMapper.upsertBatch(Arrays.asList(user1, user2));

        assertThat(result).isEqualTo(1);

        List<User> users = userMapper.selectList(null);
        assertThat(users).hasSize(2);
    }

    @Test
    void upsertBatch_update_existing_users() {
        User user1 = User.builder()
                .username("alice")
                .email("alice@example.com")
                .age(25)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        userMapper.insert(user1);

        User updateUser = User.builder()
                .username("alice")
                .email("updated@example.com")
                .age(35)
                .build();

        User newUser = User.builder()
                .username("carol")
                .email("carol@example.com")
                .age(40)
                .build();

        int result = userMapper.upsertBatch(Arrays.asList(updateUser, newUser));

        assertThat(result).isEqualTo(1);

        List<User> users = userMapper.selectList(null);
        assertThat(users).hasSize(2);

        User alice = users.stream().filter(u -> "alice".equals(u.getUsername())).findFirst().orElse(null);
        assertThat(alice.getEmail()).isEqualTo("updated@example.com");
        assertThat(alice.getAge()).isEqualTo(35);

        User carol = users.stream().filter(u -> "carol".equals(u.getUsername())).findFirst().orElse(null);
        assertThat(carol).isNotNull();
        assertThat(carol.getEmail()).isEqualTo("carol@example.com");
    }
}