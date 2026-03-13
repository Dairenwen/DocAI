package com.docai.file.dto.request;


import lombok.Builder;
import lombok.Data;

/**
 * 文件查询请求类
 */
@Data
@Builder
public class FileListRequest {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 页码
     */
    private Integer pageNum = 1;

    /**
     * 每页的大小
     */
    private Integer pageSize = 10;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 上传状态
     */
    private Integer uploadStatus;

}
