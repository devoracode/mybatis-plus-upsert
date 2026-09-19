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
 * 同时标注 {@code IdType.AUTO} 与 {@code @KeySequence} 的测试实体，
 * 用于验证主键策略的判断顺序与 MyBatis-Plus 原生 {@code Insert} 一致：AUTO 优先。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@KeySequence("seq_auto_id_with_sequence")
@TableName("t_auto_seq_user")
public class AutoIdWithSequenceEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @ConflictKey
    private String username;

    private String email;
}
