package com.docai.file.dto.request;

import lombok.Builder;
import lombok.Data;

/**
 * 文件预览的请求
 */
@Data
@Builder
public class ExcelPreviewRequest {

    /**
     * 页码
     */
    @Builder.Default
    private Integer page = 1;


    /**
     * 每页的行数
     */
    @Builder.Default
    private Integer pageSize = 20;

    /**
     * sheet索引
     */
    private Integer sheetIndex;
}
