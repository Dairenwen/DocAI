package com.docai.ai.dto.request;

import lombok.Builder;
import lombok.Data;

/**
 * AI历史记录请求参数DTO
 */
@Data
@Builder
public class AiRequestHistoryRequest {

    // 页码
    private Integer pageNum = 1;

    // 每页大小
    private Integer pageSize = 10;

    // 文件ID
    private Long fileId;
}
