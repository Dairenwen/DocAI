package com.docai.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("source_documents")
public class SourceDocumentEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String fileName;
    private String fileType;
    private String storagePath;
    private String ossKey;
    private Long fileSize;
    private String uploadStatus;
    private String docSummary;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
