package io.github.devoracode.upsert.test.support;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.devoracode.upsert.annotation.ConflictKey;
import lombok.Getter;
import lombok.Setter;

/**
 * 多字段联合冲突键的测试实体（对应 UNIQUE(tenant_id, biz_code)）。
 */
@Getter
@Setter
@TableName("t_multi_conflict")
public class MultiConflictKeyEntity {

    @TableId
    private Long id;

    @ConflictKey(order = 0)
    private String tenantId;

    @ConflictKey(order = 1)
    private String bizCode;

    private String name;
}
