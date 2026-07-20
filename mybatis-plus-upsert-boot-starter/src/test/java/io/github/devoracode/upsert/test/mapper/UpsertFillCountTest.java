package io.github.devoracode.upsert.test.mapper;

import io.github.devoracode.upsert.test.TestApplication;
import io.github.devoracode.upsert.test.TestApplication.CountingMetaObjectHandler;
import io.github.devoracode.upsert.test.support.UserEntity;
import io.github.devoracode.upsert.test.support.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TestApplication.class)
@Transactional
@Rollback
class UpsertFillCountTest {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CountingMetaObjectHandler countingHandler;

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

        // UpsertFillInterceptor invokes updateFill before execution;
        // MyBatis-Plus' native MybatisParameterHandler invokes insertFill
        // during parameter processing. Each is invoked at least once.
        assertThat(countingHandler.getInsertFillCount()).isGreaterThanOrEqualTo(1);
        assertThat(countingHandler.getUpdateFillCount()).isGreaterThanOrEqualTo(1);

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
        assertThat(countingHandler.getUpdateFillCount()).isGreaterThanOrEqualTo(1);

        UserEntity saved = userMapper.selectById("1");
        assertThat(saved.getUpdateTime()).isNotNull();
    }
}
