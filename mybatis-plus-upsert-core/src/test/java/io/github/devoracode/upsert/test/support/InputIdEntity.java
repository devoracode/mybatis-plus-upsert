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
 * IdType.INPUT 主键的测试实体：主键由用户提供，
 * 注入时必须保持 NoKeyGenerator（不回填）。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("t_input_user")
public class InputIdEntity {

    @TableId(type = IdType.INPUT)
    private String id;

    @ConflictKey
    private String username;

    private String email;
}
