package com.docai.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("extracted_fields")
public class ExtractedFieldEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long docId;
    private String fieldKey;
    private String fieldName;
    private String fieldValue;
    private String fieldType;
    private String aliases;
    private String sourceText;
    private String sourceLocation;
    private BigDecimal confidence;
    private LocalDateTime createdAt;
}
