package io.github.devoracode.upsert.test.support;

import io.github.devoracode.upsert.mapper.UpsertMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 动态数据源路由测试用 Mapper。
 */
@Mapper
public interface RoutingUserMapper extends UpsertMapper<RoutingUserEntity> {
}
