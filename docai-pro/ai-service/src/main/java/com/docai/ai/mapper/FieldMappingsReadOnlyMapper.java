package com.docai.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.docai.file.entity.FieldMappingEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 只读FieldMappings表
 */
@Mapper
public interface FieldMappingsReadOnlyMapper extends BaseMapper<FieldMappingEntity> {
}
