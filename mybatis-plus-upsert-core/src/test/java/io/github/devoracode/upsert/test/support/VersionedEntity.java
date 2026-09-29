package io.github.devoracode.upsert.test.support;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import io.github.devoracode.upsert.annotation.ConflictKey;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 含 {@code @Version} 的非法 upsert 配置，用于锁定启动期快速失败行为。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("t_versioned_user")
public class VersionedEntity {

    @TableId
    private Long id;

    @ConflictKey
    private String username;

    @Version
    private Integer version;

    private String email;
}
