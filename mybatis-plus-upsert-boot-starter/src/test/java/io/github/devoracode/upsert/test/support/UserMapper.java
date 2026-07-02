package io.github.devoracode.upsert.test.support;

import io.github.devoracode.upsert.mapper.UpsertMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends UpsertMapper<UserEntity> {
}
