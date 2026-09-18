package io.github.devoracode.upsert.test.support;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.devoracode.upsert.annotation.ConflictKey;
import lombok.Getter;
import lombok.Setter;

/**
 * 仅含主键与冲突键、无任何可更新字段的实体，
 * 用于验证 {@code UpsertMetaParser} 在启动阶段即拒绝该配置。
 */
@Getter
@Setter
@TableName("t_conflict_only")
public class ConflictOnlyEntity {

    @TableId
    private Long id;

    @ConflictKey
    private String username;
}
