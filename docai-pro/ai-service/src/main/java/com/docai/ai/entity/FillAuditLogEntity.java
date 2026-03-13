package com.docai.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("fill_audit_logs")
public class FillAuditLogEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String auditId;
    private Long templateId;
    private Long userId;
    private String slotId;
    private String slotLabel;
    private String finalValue;
    private BigDecimal finalConfidence;
    private String decisionMode;
    private String sourceDocName;
    private String sourceText;
    private String reason;
    private String candidatesSummary;
    private LocalDateTime createdAt;
}
