package io.github.devoracode.upsert.test.support;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.devoracode.upsert.annotation.ConflictKey;
import lombok.Getter;
import lombok.Setter;

/**
 * 非法注解组合的测试实体：@ConflictKey 字段同时声明 insertStrategy=NEVER
 * （永不参与 INSERT）——冲突判断与 INSERT 列集合必然不一致，
 * 解析时必须快速失败。
 */
@Getter
@Setter
@TableName("t_never_conflict")
public class NeverInsertConflictKeyEntity {

    @TableId
    private Long id;

    @ConflictKey
    @TableField(insertStrategy = FieldStrategy.NEVER)
    private String username;

    private String email;
}
