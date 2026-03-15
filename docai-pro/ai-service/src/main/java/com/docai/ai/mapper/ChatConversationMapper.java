package com.docai.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.docai.ai.entity.ChatConversationEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatConversationMapper extends BaseMapper<ChatConversationEntity> {
}
