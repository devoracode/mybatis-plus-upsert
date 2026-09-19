package io.github.devoracode.upsert.test.support;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.devoracode.upsert.annotation.ConflictKey;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * IdType.AUTO 自增主键实体（t_auto_user）：主键由数据库生成，
 * 冲突键放在用户提供的 username 列上，用于验证主键回填。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("t_auto_user")
public class AutoUserEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @ConflictKey
    private String username;

    private String email;
}
