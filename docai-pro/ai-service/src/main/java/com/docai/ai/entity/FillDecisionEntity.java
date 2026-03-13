package com.docai.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;

@Data
@TableName("fill_decisions")
public class FillDecisionEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String auditId;
    private Long templateId;
    private String slotId;
    private String slotLabel;
    private String finalValue;
    private String finalFieldId;
    private BigDecimal finalConfidence;
    private String decisionMode;
    private String reason;
}
