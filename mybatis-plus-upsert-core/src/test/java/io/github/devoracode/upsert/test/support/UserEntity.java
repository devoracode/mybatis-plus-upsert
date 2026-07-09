package io.github.devoracode.upsert.test.support;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.devoracode.upsert.annotation.ConflictKey;
import io.github.devoracode.upsert.annotation.IgnoreOnUpdate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Core-module test fixture for {@code UpsertMetaParser} unit tests.
 *
 * <p>This is a deliberately small, self-contained copy so the parser test does not depend on
 * the boot-starter module (which keeps its own {@code UserEntity} for integration tests). The
 * MyBatis-Plus {@code TableInfo} for this class is populated directly via
 * {@code TableInfoHelper.initTableInfo} in the test, so no Spring context is required.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("t_user")
public class UserEntity {

    @TableId
    private Long id;

    @ConflictKey
    private String username;

    private String email;

    private Integer age;

    @IgnoreOnUpdate
    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
