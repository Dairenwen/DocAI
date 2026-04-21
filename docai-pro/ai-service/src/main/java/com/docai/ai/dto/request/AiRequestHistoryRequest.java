package com.docai.ai.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI历史记录请求参数DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiRequestHistoryRequest {

    // 页码
    @Builder.Default
    private Integer pageNum = 1;

    // 每页大小
    @Builder.Default
    private Integer pageSize = 10;

    // 文件ID
    private Long fileId;
}
