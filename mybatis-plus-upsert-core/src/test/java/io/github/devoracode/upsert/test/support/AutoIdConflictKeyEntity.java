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
 * 非法注解组合的测试实体：@ConflictKey 落在 IdType.AUTO 主键上，
 * 解析时必须快速失败。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("t_bad_conflict")
public class AutoIdConflictKeyEntity {

    @ConflictKey
    @TableId(type = IdType.AUTO)
    private Long id;

    private String email;
}
