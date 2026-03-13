package com.docai.ai.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

// AI图表生成响应DTO
@Data
@Builder
public class AiChartResponse {
    // 图表ID
    private String chartId;

    // 图表名称
    private String chartName;

    // 图表类型
    private String chartType;

    // 图表描述
    private String chartDescription;

    // 生成的sql
    private String generatedSql;

    // 原始的列表集合数据
    private List<Map<String, Object>> chartData;

    // X轴通常类别
    private String xlabel;

    // Y轴通常是数值
    private String ylabel;

    // 图表标题
    private String chartTitle;

    // 文件ID
    private Long fileId;

    // 原始文件名
    private String fileName;

}
