package com.docai.file.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * 文件上传的响应结果
 */
@Data
@Builder
public class FileUploadResponse {

    /**
     * 文件ID
     */
    private Long fileId;

    /**
     * 原始文件名
     */
    private String fileName;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 文件访问路径
     */
    private String fileUrl;

    /**
     * oss中的key
     */
    private String ossKey;

    /**
     * 上传的状态
     */
    private Integer uploadStatus;
}
