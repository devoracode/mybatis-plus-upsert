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
 * {@code mybatis-plus.upsert.fill-strategy=none} 下的填充行为：
 * 完全不进行预绑定填充。
 * MyBatis-Plus 的原生 {@code insertFill} 仍然会执行——但发生在 SQL 绑定之后，
 * 因此在绑定时空的填充注解字段会被动态 SQL 省略
 * （这正是在预绑定处理器要修复的问题）。
 */
@SpringBootTest(classes = TestApplication.class,
        properties = "mybatis-plus.upsert.fill-strategy=none")
@Transactional
@Rollback
class UpsertFillDisabledTest {

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
    void disabled_fill_invokes_no_updateFill_and_fills_after_binding() {
        UserEntity user = UserEntity.builder()
                .id("1")
                .name("nofill")
                .email("nofill@example.com")
                .age(20)
                .createTime(null)
                .updateTime(null)
                .build();

        userMapper.upsert(user);

        // 未安装预绑定处理器（策略 NONE）：updateFill 从不被调用，
        // 只有 MP 的原生绑定后 insertFill 会执行。
        // create_time 在 SQL 绑定时为 null，因此后绑定填充值
        // 永远无法进入生成的语句中——这正是预绑定处理器要修复的行为。
        assertThat(countingHandler.getInsertFillCount()).isGreaterThanOrEqualTo(1);
        assertThat(countingHandler.getUpdateFillCount()).isZero();
    }
}
