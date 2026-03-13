package com.docai.ai.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 图表标签配置
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChartLabels {

    // X轴通常类别
    private String xlabel;

    // Y轴通常是数值
    private String ylabel;
}
