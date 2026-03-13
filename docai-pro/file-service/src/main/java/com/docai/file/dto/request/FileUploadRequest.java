package com.docai.file.dto.request;

import lombok.Builder;
import lombok.Data;

/**
 * 文件上传请求参数
 */
@Data
@Builder
public class FileUploadRequest {

    /**
     * 文件目录
     */
    private String category;
}
