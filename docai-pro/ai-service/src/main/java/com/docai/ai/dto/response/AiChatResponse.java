package com.docai.ai.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * AI对话的响应
 */
@Builder
@Data
public class AiChatResponse {

    // 记录请求ID
    private Long requestId;

    // 记录AI响应
    private String aiResponse;

    // 当前要执行的sql
    private String sqlQuery;

    // 查询原始数据
    private List<Map<String, Object>> resultData;

    // 查询结果数量
    private Integer resultCount;

    // 处理状态
    private Integer status;

    // 错误信息
    private String errorMessage;

    // 是否为修改请求
    private Boolean isModificationRequest;

    // 修改后的下载地址
    private String modifiedExcelUrl;
}
