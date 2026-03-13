package com.docai.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.docai.ai.entity.AiRequestEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI请求记录的Mapper
 */
@Mapper
public interface AiRequestMapper extends BaseMapper<AiRequestEntity> {
}
