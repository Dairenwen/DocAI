package com.docai.file.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 文件预览响应
 */
@Data
@Builder
public class ExcelPreviewResponse {

    private ExcelInfo excelInfo;

    private List<SheetInfo> sheets;

    private Integer currentSheetIndex;

    private List<ColumnHeader> headers;

    private List<Map<String, Object>> dataRows;

    private PaginationInfo paginationInfo;




    @Data
    @Builder
    public static class ExcelInfo {
        // 文件ID
        private Long fileId;

        // 文件名
        private String fileName;

        // 文件大小
        private Long fileSize;

        // 总行数
        private Long totalRows;

        // 总列数
        private Long totalColumns;
    }

    @Data
    @Builder
    public static class SheetInfo {
        // sheet索引
        private Integer sheetIndex;

        // sheet名称
        private String sheetName;

        // 对应的mysql表名
        private String tableName;

        // 总行数
        private Long totalRows;

        // 总列数
        private Long totalColumns;
    }

    @Data
    @Builder
    public static class  ColumnHeader {
        // 数据库字段名
        private String dbFieldName;

        // 原始excel列名
        private String originalHeader;

    }

    @Data
    @Builder
    public static  class  PaginationInfo {
        // 当前页
        private Integer currentPage;

        // 每页的大小
        private Integer pageSize;

        // 总页数
        private Long totalPages;

        // 总记录数
        private Long totalRecords;

        // 是否有下一页
        private Boolean hasNext;

        // 是否有上一页
        private Boolean hasPrevious;

    }

}
