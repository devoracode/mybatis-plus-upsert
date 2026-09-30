package io.github.devoracode.upsert.test.support;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import io.github.devoracode.upsert.annotation.ConflictKey;
import io.github.devoracode.upsert.annotation.IgnoreOnUpdate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * {@code @Version} 同时标注 {@code @IgnoreOnUpdate} 的实体：冲突分支不写 version 列，
 * 属于显式知情声明，解析期放行并告警。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("t_versioned_ignored_user")
public class VersionedIgnoredEntity {

    @TableId
    private Long id;

    @ConflictKey
    private String username;

    @Version
    @IgnoreOnUpdate
    private Integer version;

    private String email;
}
