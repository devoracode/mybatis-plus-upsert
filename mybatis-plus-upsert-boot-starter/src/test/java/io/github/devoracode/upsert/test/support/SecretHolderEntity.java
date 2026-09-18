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
 * 更新列全部可空的实体（t_secret_holder），
 * 用于在 H2（MySQL 模式）上验证“全部动态更新字段为 null”的兜底路径：
 * 该模拟实现会对缺失的 NOT NULL 列（如 t_user.name）先行校验而不放行冲突更新分支。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("t_secret_holder")
public class SecretHolderEntity {

    @TableId(type = IdType.INPUT)
    private Long id;

    @ConflictKey
    private String code;

    private String secret;

    private String visible;
}
