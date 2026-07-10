package io.github.devoracode.upsert.test.support;

import com.baomidou.mybatisplus.annotation.*;
import io.github.devoracode.upsert.annotation.ConflictKey;
import io.github.devoracode.upsert.annotation.IgnoreOnUpdate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("t_user")
public class UserEntity {

    @ConflictKey
    @TableId(type = IdType.INPUT)
    private String id;

    @TableField("name")
    private String name;

    @TableField("age")
    private Integer age;

    @TableField("email")
    private String email;

    @TableField("cell_phone")
    private String cellPhone;

    @TableField("id_card_no")
    private String idCardNo;

    @TableField("address")
    private String address;

    @TableField("province")
    private String province;

    @TableField("license_plate")
    private String licensePlate;

    @IgnoreOnUpdate
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
