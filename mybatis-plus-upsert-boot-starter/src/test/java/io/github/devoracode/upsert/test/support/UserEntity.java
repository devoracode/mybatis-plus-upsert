package io.github.devoracode.upsert.test.support;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.devoracode.upsert.annotation.ConflictKey;
import io.github.devoracode.upsert.annotation.IgnoreOnUpdate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_user")
public class UserEntity {

    @TableId
    private Long id;

    // 业务唯一键，触发冲突检测
    @ConflictKey
    private String username;

    private String email;

    private Integer age;

    // 仅在首次插入时写入，更新时忽略
    @IgnoreOnUpdate
    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
