package io.github.devoracode.upsert.test.support;

import io.github.devoracode.upsert.mapper.UpsertMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * AUTO 主键叠加 {@code @KeySequence} 的测试 Mapper，仅用于注入层测试。
 */
@Mapper
public interface AutoIdWithSequenceMapper extends UpsertMapper<AutoIdWithSequenceEntity> {
}
