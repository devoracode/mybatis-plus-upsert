package io.github.devoracode.upsert.test.support;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.devoracode.upsert.annotation.ConflictKey;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 完全没有 @TableId 主键的测试实体：注入时必须保持 NoKeyGenerator，
 * 不能因为缺少主键而报错或误配回填。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("t_no_id_user")
public class NoIdEntity {

    @ConflictKey
    private String username;

    private String email;
}
