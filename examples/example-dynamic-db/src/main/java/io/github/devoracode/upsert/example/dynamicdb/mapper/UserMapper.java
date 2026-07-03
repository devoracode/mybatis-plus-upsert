package io.github.devoracode.upsert.example.dynamicdb.mapper;

import io.github.devoracode.upsert.example.dynamicdb.entity.User;
import io.github.devoracode.upsert.mapper.UpsertMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends UpsertMapper<User> {
}