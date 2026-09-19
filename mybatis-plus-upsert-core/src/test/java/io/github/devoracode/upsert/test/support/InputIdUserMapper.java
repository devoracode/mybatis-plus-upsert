package io.github.devoracode.upsert.test.support;

import io.github.devoracode.upsert.mapper.UpsertMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * IdType.INPUT 主键实体的测试 Mapper，仅用于注入层测试。
 */
@Mapper
public interface InputIdUserMapper extends UpsertMapper<InputIdEntity> {
}
