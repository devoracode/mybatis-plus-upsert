package io.github.devoracode.upsert.example.mysql;

import io.github.devoracode.upsert.example.mysql.entity.User;
import io.github.devoracode.upsert.example.mysql.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

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
        String userId = UUID.randomUUID().toString();
        User user = User.builder()
                .id(userId)
                .name("alice")
                .email("alice@example.com")
                .age(25)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        int result = userMapper.upsert(user);

        assertThat(result).isEqualTo(1);

        User saved = userMapper.selectList(null).get(0);
        assertThat(saved.getId()).isEqualTo(userId);
        assertThat(saved.getName()).isEqualTo("alice");
        assertThat(saved.getEmail()).isEqualTo("alice@example.com");
        assertThat(saved.getAge()).isEqualTo(25);
    }

    @Test
    void upsert_update_existing_user() {
        String userId = UUID.randomUUID().toString();
        User user = User.builder()
                .id(userId)
                .name("alice")
                .email("alice@example.com")
                .age(25)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        userMapper.insert(user);

        User updateUser = User.builder()
                .id(userId)
                .name("alice_updated")
                .age(30)
                .build();

        int result = userMapper.upsert(updateUser);

        assertThat(result).isEqualTo(1);

        User saved = userMapper.selectList(null).get(0);
        assertThat(saved.getId()).isEqualTo(userId);
        assertThat(saved.getName()).isEqualTo("alice_updated");
        assertThat(saved.getEmail()).isEqualTo("alice@example.com");
        assertThat(saved.getAge()).isEqualTo(30);
    }

    @Test
    void upsertBatch_insert_multiple_users() {
        String userId1 = UUID.randomUUID().toString();
        String userId2 = UUID.randomUUID().toString();
        User user1 = User.builder()
                .id(userId1)
                .name("alice")
                .email("alice@example.com")
                .age(25)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        User user2 = User.builder()
                .id(userId2)
                .name("bob")
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
        String userId1 = UUID.randomUUID().toString();
        User user1 = User.builder()
                .id(userId1)
                .name("alice")
                .email("alice@example.com")
                .age(25)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        userMapper.insert(user1);

        User updateUser = User.builder()
                .id(userId1)
                .name("alice_updated")
                .age(35)
                .build();

        String userId2 = UUID.randomUUID().toString();
        User newUser = User.builder()
                .id(userId2)
                .name("carol")
                .email("carol@example.com")
                .age(40)
                .build();

        int result = userMapper.upsertBatch(Arrays.asList(updateUser, newUser));

        assertThat(result).isEqualTo(1);

        List<User> users = userMapper.selectList(null);
        assertThat(users).hasSize(2);

        User alice = users.stream().filter(u -> userId1.equals(u.getId())).findFirst().orElse(null);
        assertThat(alice.getName()).isEqualTo("alice_updated");
        assertThat(alice.getAge()).isEqualTo(35);

        User carol = users.stream().filter(u -> "carol".equals(u.getName())).findFirst().orElse(null);
        assertThat(carol).isNotNull();
        assertThat(carol.getEmail()).isEqualTo("carol@example.com");
    }
}