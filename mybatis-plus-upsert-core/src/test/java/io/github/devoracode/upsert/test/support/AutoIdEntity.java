package io.github.devoracode.upsert.test.support;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.devoracode.upsert.annotation.ConflictKey;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * IdType.AUTO 主键的测试实体：主键由数据库生成，
 * 冲突键放在用户提供的 username 列上。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("t_auto_user")
public class AutoIdEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @ConflictKey
    private String username;

    private String email;
}
