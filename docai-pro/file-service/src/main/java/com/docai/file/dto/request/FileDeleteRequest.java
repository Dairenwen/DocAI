package com.docai.file.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 文件删除请求类
 */
@Data
public class FileDeleteRequest {

    /**
     * 文件ID列表
     */
    @NotEmpty(message = "需要删除的文件不能为空")
    private List<Long> fileIds;
}
