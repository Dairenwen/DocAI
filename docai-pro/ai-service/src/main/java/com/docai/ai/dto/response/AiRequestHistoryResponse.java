package com.docai.ai.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * AI历史记录响应参数DTO
 */
@Data
@Builder
public class AiRequestHistoryResponse {

    // 请求ID
    private Long id;

    // 文件ID
    private Long fileId;

    // 文件名
    private String fileName;

    // 用户输入
    private String userInput;

    // AI响应
    private String aiResponse;

    // 请求类型
    @Builder.Default
    private String requestType = "AI";

    // 请求状态
    private Integer status;
}
