package io.github.devoracode.upsert.test.support;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.devoracode.upsert.annotation.ConflictKey;
import io.github.devoracode.upsert.annotation.IgnoreOnUpdate;
import lombok.*;

import java.time.LocalDateTime;

@TableName("t_user")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEntity {

    @TableId
    private Long id;

    @ConflictKey
    private String username;

    private String email;

    private Integer age;

    @IgnoreOnUpdate
    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
