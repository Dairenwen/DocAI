package com.docai.ai.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.docai.ai.dto.request.AiChatRequest;
import com.docai.ai.dto.request.AiRequestHistoryRequest;
import com.docai.ai.dto.request.SendContentEmailRequest;
import com.docai.ai.dto.request.SendEmailRequest;
import com.docai.ai.dto.response.AiRequestHistoryResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * AI服务业务接口
 */
public interface AiService {
    void streamUnifiedChat(AiChatRequest aiChatRequest, Long userId, SseEmitter sseEmitter);

    IPage<AiRequestHistoryResponse> getRequestHistory(AiRequestHistoryRequest aiRequestHistoryRequest, Long userId);

    Boolean sendEmailWithExcel(SendEmailRequest sendEmailRequest);

    Boolean sendContentEmail(SendContentEmailRequest request);

    Map<String, String> applyDocumentEdit(Long docId, Long userId, String content);
}
