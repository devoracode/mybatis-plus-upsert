package io.github.devoracode.upsert.test.support;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.devoracode.upsert.annotation.ConflictKey;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 序列主键实体（t_key_seq_user）：主键值由数据库序列 {@code seq_key_seq_user} 生成，
 * 用于验证 Upsert 复用 MyBatis-Plus 既有的 {@code @KeySequence} selectKey 机制。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@KeySequence("seq_key_seq_user")
@TableName("t_key_seq_user")
public class KeySeqUserEntity {

    @TableId(type = IdType.INPUT)
    private Long id;

    @ConflictKey
    private String username;

    private String email;
}
