package io.github.devoracode.upsert.example.dynamicdbsb3;

import org.apache.ibatis.executor.BatchResult;

import io.github.devoracode.upsert.example.dynamicdbsb3.entity.User;
import io.github.devoracode.upsert.example.dynamicdbsb3.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("mysql")
class UpsertMapperTest {

    @Autowired
    private UserService userService;

    @BeforeEach
    void setUp() {
        userService.deleteAll();
    }

    @Test
    void upsert_insert_new_user() {
        User user = User.builder()
                .id(1L)
                .name("Test User")
                .age(25)
                .email("test@example.com")
                .cellPhone("13800138000")
                .idCardNo("110101199001011234")
                .address("Test Address")
                .province("Beijing")
                .licensePlate("京A12345")
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        int result = userService.upsert(user);

        assertThat(result).isGreaterThan(0);
        
        User found = userService.findByEmail("test@example.com");
        assertThat(found).isNotNull();
        assertThat(found.getName()).isEqualTo("Test User");
    }

    @Test
    void upsert_update_existing_user() {
        User user = User.builder()
                .id(2L)
                .name("Original Name")
                .age(30)
                .email("update@example.com")
                .cellPhone("13900139000")
                .idCardNo("110101198501011234")
                .address("Original Address")
                .province("Shanghai")
                .licensePlate("沪B67890")
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        userService.upsert(user);

        User updatedUser = User.builder()
                .id(2L)
                .name("Updated Name")
                .age(31)
                .email("update@example.com")
                .cellPhone("13900139000")
                .idCardNo("110101198501011234")
                .address("Updated Address")
                .province("Guangzhou")
                .licensePlate("粤C11111")
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        int result = userService.upsert(updatedUser);

        assertThat(result).isGreaterThan(0);
        
        User found = userService.findByEmail("update@example.com");
        assertThat(found).isNotNull();
        assertThat(found.getName()).isEqualTo("Updated Name");
        assertThat(found.getAge()).isEqualTo(31);
    }

    @Test
    void upsertBatch_insert_multiple_users() {
        List<User> users = new ArrayList<>();
        for (int i = 10; i < 13; i++) {
            users.add(User.builder()
                    .id((long) i)
                    .name("Batch User " + i)
                    .age(20 + i)
                    .email("batch" + i + "@example.com")
                    .cellPhone("1380000" + String.format("%04d", i))
                    .idCardNo("110101199" + String.format("%02d", i) + "011234")
                    .address("Batch Address " + i)
                    .province("Province " + i)
                    .licensePlate("京B" + String.format("%04d", i))
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build());
        }

        int result = userService.upsertBatch(users);

        assertThat(result).isGreaterThan(0);
    }

    @Test
    void upsertBatch_update_existing_users() {
        List<User> users = new ArrayList<>();
        for (int i = 20; i < 23; i++) {
            users.add(User.builder()
                    .id((long) i)
                    .name("Original " + i)
                    .age(25 + i)
                    .email("batchupdate" + i + "@example.com")
                    .cellPhone("1390000" + String.format("%04d", i))
                    .idCardNo("110101198" + String.format("%02d", i) + "011234")
                    .address("Original Address " + i)
                    .province("Original Province " + i)
                    .licensePlate("沪C" + String.format("%04d", i))
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build());
        }
        userService.upsertBatch(users);

        List<User> updatedUsers = new ArrayList<>();
        for (int i = 20; i < 23; i++) {
            updatedUsers.add(User.builder()
                    .id((long) i)
                    .name("Updated " + i)
                    .age(30 + i)
                    .email("batchupdate" + i + "@example.com")
                    .cellPhone("1390000" + String.format("%04d", i))
                    .idCardNo("110101198" + String.format("%02d", i) + "011234")
                    .address("Updated Address " + i)
                    .province("Updated Province " + i)
                    .licensePlate("粤D" + String.format("%04d", i))
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build());
        }

        int result = userService.upsertBatch(updatedUsers);

        assertThat(result).isGreaterThan(0);
    }

    @Test
    void upsertBatchWithResult_insert_multiple_users() {
        List<User> users = new ArrayList<>();
        for (int i = 30; i < 33; i++) {
            users.add(User.builder()
                    .id((long) i)
                    .name("Result User " + i)
                    .age(22 + i)
                    .email("result" + i + "@example.com")
                    .cellPhone("1370000" + String.format("%04d", i))
                    .idCardNo("110101199" + String.format("%02d", i) + "011234")
                    .address("Result Address " + i)
                    .province("Result Province " + i)
                    .licensePlate("京E" + String.format("%04d", i))
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build());
        }

        List<BatchResult> results = userService.upsertBatchWithResult(users);

        assertThat(results).isNotNull();
        assertThat(results).hasSizeGreaterThan(0);
    }

    @Test
    void upsertBatchWithResult_update_existing_users() {
        List<User> users = new ArrayList<>();
        for (int i = 40; i < 43; i++) {
            users.add(User.builder()
                    .id((long) i)
                    .name("Original Result " + i)
                    .age(28 + i)
                    .email("resultupdate" + i + "@example.com")
                    .cellPhone("1360000" + String.format("%04d", i))
                    .idCardNo("110101197" + String.format("%02d", i) + "011234")
                    .address("Original Result Address " + i)
                    .province("Original Result Province " + i)
                    .licensePlate("沪F" + String.format("%04d", i))
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build());
        }
        userService.upsertBatchWithResult(users);

        List<User> updatedUsers = new ArrayList<>();
        for (int i = 40; i < 43; i++) {
            updatedUsers.add(User.builder()
                    .id((long) i)
                    .name("Updated Result " + i)
                    .age(33 + i)
                    .email("resultupdate" + i + "@example.com")
                    .cellPhone("1360000" + String.format("%04d", i))
                    .idCardNo("110101197" + String.format("%02d", i) + "011234")
                    .address("Updated Result Address " + i)
                    .province("Updated Result Province " + i)
                    .licensePlate("粤G" + String.format("%04d", i))
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build());
        }

        List<BatchResult> results = userService.upsertBatchWithResult(updatedUsers);

        assertThat(results).isNotNull();
        assertThat(results).hasSizeGreaterThan(0);
    }

    @Test
    void findAll() {
        List<User> users = new ArrayList<>();
        for (int i = 50; i < 53; i++) {
            users.add(User.builder()
                    .id((long) i)
                    .name("Find All User " + i)
                    .age(35 + i)
                    .email("findall" + i + "@example.com")
                    .cellPhone("1350000" + String.format("%04d", i))
                    .idCardNo("110101196" + String.format("%02d", i) + "011234")
                    .address("Find All Address " + i)
                    .province("Find All Province " + i)
                    .licensePlate("京H" + String.format("%04d", i))
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build());
        }
        userService.upsertBatch(users);

        List<User> allUsers = userService.findAll();

        assertThat(allUsers).isNotNull();
        assertThat(allUsers.size()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void deleteAll() {
        User user = User.builder()
                .id(60L)
                .name("Delete User")
                .age(40)
                .email("delete@example.com")
                .cellPhone("13400000000")
                .idCardNo("110101195001011234")
                .address("Delete Address")
                .province("Delete Province")
                .licensePlate("京I00000")
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        userService.upsert(user);

        int result = userService.deleteAll();

        assertThat(result).isGreaterThan(0);
        assertThat(userService.findByEmail("delete@example.com")).isNull();
    }
}
