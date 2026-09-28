package io.github.devoracode.upsert.test.support;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.devoracode.upsert.annotation.ConflictKey;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("t_filled_conflict")
public class FilledConflictKeyEntity {

    @TableId(type = IdType.INPUT)
    private Long id;

    @ConflictKey
    @TableField(fill = FieldFill.INSERT)
    private String code;

    private String value;
}
