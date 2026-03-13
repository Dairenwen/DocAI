package com.docai.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("field_alias_dict")
public class FieldAliasDictEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String standardKey;
    private String aliasName;
    private String fieldType;
}
