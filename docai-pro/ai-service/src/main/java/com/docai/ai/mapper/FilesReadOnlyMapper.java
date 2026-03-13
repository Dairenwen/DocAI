package com.docai.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.docai.file.entity.FilesEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 只读files表
 */
@Mapper
public interface FilesReadOnlyMapper extends BaseMapper<FilesEntity> {
}
