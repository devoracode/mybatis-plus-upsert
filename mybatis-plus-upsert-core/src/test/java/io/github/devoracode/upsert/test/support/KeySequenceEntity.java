package io.github.devoracode.upsert.test.support;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.devoracode.upsert.annotation.ConflictKey;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 序列主键测试实体：{@code @KeySequence} + {@code IdType.INPUT}，
 * 主键值由数据库序列生成，用于验证注入层复用 MyBatis-Plus 既有的
 * selectKey 主键生成机制。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@KeySequence("seq_key_seq_user")
@TableName("t_key_seq_user")
public class KeySequenceEntity {

    @TableId(type = IdType.INPUT)
    private Long id;

    @ConflictKey
    private String username;

    private String email;
}
