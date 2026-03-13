package com.docai.ai.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 流式事件DTO
 */
@Data
@Builder
public class StreamProcessEvent {

    // 事件类型
    private String eventType;

    // 处理阶段
    private String stage;

    // 进度百分比
    private Integer progress;

    // 消息内容
    private String message;

    // 详细消息
    private String detail;

    // 是否完成
    private Boolean completed;

    // 最终的数据结果
    private AiUnifiedResponse result;

    // 错误信息
    private String error;

    // SQL语句
    private String sqlQuery;

    // 查询结果预览
    private List<Map<String, Object>> resultPreview;

    // 查询总数
    private Integer resultCount;

    // AI响应内容
    private String aiResponseContent;

}
