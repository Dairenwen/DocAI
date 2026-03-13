package com.docai.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.docai.file.entity.FileTableMappingEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文件与表映射mapper
 */
@Mapper
public interface FileTableMappingMapper extends BaseMapper<FileTableMappingEntity> {
}
