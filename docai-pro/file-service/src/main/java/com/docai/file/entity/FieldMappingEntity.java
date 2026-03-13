package com.docai.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 字段映射关系表的实体类
 */
@Data
@TableName("field_mappings")
public class FieldMappingEntity {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 文件ID
     */
    private Long fileId;

    /**
     * 表名
     */
    private String tableName;

    /**
     * 动态字段名
     */
    private String dbFieldName;

    /**
     * excel中的表头
     */
    private String originalHeader;

    /**
     * excel表头的顺序
     */
    private Integer fieldOrder;

}
