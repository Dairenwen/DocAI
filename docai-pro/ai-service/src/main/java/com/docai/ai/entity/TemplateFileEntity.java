package com.docai.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("template_files")
public class TemplateFileEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String fileName;
    private String templateType;
    private String storagePath;
    private String ossKey;
    private Long fileSize;
    private String parseStatus;
    private Integer slotCount;
    private String outputPath;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
