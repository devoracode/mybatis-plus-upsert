package io.github.devoracode.upsert.test.support;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.devoracode.upsert.annotation.ConflictKey;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 没有主键的实体（t_keyless_user）：冲突键 username 同时是表的唯一约束，
 * 用于验证实体缺少主键时 Upsert 不报错、也不尝试回填。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("t_keyless_user")
public class KeylessUserEntity {

    @ConflictKey
    private String username;

    private String email;
}
