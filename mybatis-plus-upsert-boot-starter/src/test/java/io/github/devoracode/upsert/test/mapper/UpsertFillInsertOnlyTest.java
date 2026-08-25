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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code mybatis-plus.upsert.fill-strategy=insert} 下的填充行为：
 * SQL 绑定前只执行 {@code insertFill}；预绑定处理器从不调用 {@code updateFill}。
 */
@SpringBootTest(classes = TestApplication.class,
        properties = "mybatis-plus.upsert.fill-strategy=insert")
@Transactional
@Rollback
class UpsertFillInsertOnlyTest {

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
    void insert_strategy_invokes_insertFill_but_not_updateFill() {
        UserEntity user = UserEntity.builder()
                .id("1")
                .name("insertonly")
                .email("insertonly@example.com")
                .age(20)
                .createTime(null)
                .updateTime(null)
                .build();

        userMapper.upsert(user);

        // insertFill 会在预绑定（处理器）时执行一次，并再次被原生机制执行（严格空操作）；
        // INSERT 策略会完全跳过 updateFill。
        assertThat(countingHandler.getInsertFillCount()).isGreaterThanOrEqualTo(1);
        assertThat(countingHandler.getUpdateFillCount()).isZero();

        // 预绑定的 insertFill 仍然覆盖动态 NOT_NULL 列。
        UserEntity saved = userMapper.selectById("1");
        assertThat(saved.getCreateTime()).isNotNull();
        assertThat(saved.getUpdateTime()).isNotNull();
    }

    @Test
    void insert_strategy_skips_updateFill_on_conflict_update() {
        userMapper.upsert(UserEntity.builder()
                .id("1")
                .name("conflictuser")
                .email("old@example.com")
                .age(25)
                .build());

        countingHandler.reset();

        userMapper.upsert(UserEntity.builder()
                .id("1")
                .name("conflictuser")
                .email("new@example.com")
                .age(30)
                .build());

        assertThat(countingHandler.getInsertFillCount()).isGreaterThanOrEqualTo(1);
        assertThat(countingHandler.getUpdateFillCount()).isZero();
    }
}
