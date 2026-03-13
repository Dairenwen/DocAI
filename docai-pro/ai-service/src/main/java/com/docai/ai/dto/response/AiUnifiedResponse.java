package com.docai.ai.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * AI处理混合类
 */
@Data
@Builder
public class AiUnifiedResponse {
    // 请求记录ID
    private Long requestId;

    // 记录AI响应
    private String aiResponse;

    // 执行的SQL语句
    private String sqlQuery;

    // SQL查询出来的数据
    private List<Map<String, Object>> resultData;

    // 查询总数
    private Integer resultCount;

    // 处理状态
    private Integer status;

    // 错误信息
    private String errorMessage;


    /**图表相关字段**/

    // 是否需要生成图表
    private Boolean needChart;

    // 图表ID
    private String chartId;

    // 图表类型
    private  String chartType;

    // 图表数据
    private String chartData;

    // 图表数据List
    private List<Map<String, Object>> chartDataList;

    // x轴
    private String xlabel;

    // y轴
    private String ylabel;

    // 关联文件名称
    private String fileName;

    /**修改相关字段**/

    // 是否为修改操作
    private Boolean isModificationRequest;

    // 修改后的excel下载链接
    private String modifiedExcelUrl;
}
