package io.github.devoracode.upsert.test.support;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface NonUpsertMapper extends BaseMapper<NonUpsertEntity> {

    @Update("UPDATE t_user SET name = #{et.name} WHERE id = #{et.id}")
    int upsert(@Param("et") NonUpsertEntity entity);
}
