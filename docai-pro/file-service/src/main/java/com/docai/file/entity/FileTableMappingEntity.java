package com.docai.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 文件sheet和mysql表的映射关系
 */
@Data
@TableName("file_table_mappings")
public class FileTableMappingEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long fileId;

    private String tableName;

    private Integer sheetIndex;

    private String sheetName;

}
