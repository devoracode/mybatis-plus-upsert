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
 * 非法注解组合的测试实体：@ConflictKey 落在 IdType.ASSIGN_ID 主键上。
 * MP 每次调用都新分配一个雪花 ID，用它做冲突键必然永不命中，解析时必须快速失败。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("t_bad_assign_id_conflict")
public class AssignIdConflictKeyEntity {

    @ConflictKey
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String email;
}
