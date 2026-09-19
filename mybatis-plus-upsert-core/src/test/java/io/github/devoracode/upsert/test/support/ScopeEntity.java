package io.github.devoracode.upsert.test.support;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.github.devoracode.upsert.annotation.ConflictKey;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 上下文作用域测试实体：不标注 @TableName，
 * 表名由注册所在 Configuration 的 table-prefix + 类名推导，
 * 用于验证同一实体类在多个 Configuration 下解析出各自独立的 UpsertMeta。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScopeEntity {

    @TableId(type = IdType.INPUT)
    private String id;

    @ConflictKey
    private String username;

    private String email;
}
