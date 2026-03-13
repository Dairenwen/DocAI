package com.docai.file.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * 文件列表响应类
 */
@Data
@Builder
public class FileInfoResponse {

    /**
     * 文件ID
     */
    private Long fileId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 原始文件名称
     */
    private String fileName;

    /**
     * 文件存储路径
     */
    private String filePath;

    /**
     * 文件大小
     */
    private Long fileSize;

    /**
     * 文件访问url
     */
    private String fileUrl;

    /**
     * oss存储的key
     */
    private String ossKey;

    /**
     * 上传状态
     */
    private Integer uploadStatus;

    /**
     * 文件类型
     */
    private String fileType;

    /**
     * 文件扩展名
     */
    private String fileExtension;

}
