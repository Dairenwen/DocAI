package com.docai.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("template_slots")
public class TemplateSlotEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long templateId;
    private String slotId;
    private String label;
    private String context;
    private String position;
    private String expectedType;
    private Integer requiredFlag;
    private String slotType;

    public boolean isRequired() {
        return requiredFlag != null && requiredFlag == 1;
    }

    public void setRequired(boolean required) {
        this.requiredFlag = required ? 1 : 0;
    }
}
