package com.docai.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * 文件元信息
 */
@Data
@Slf4j
@Builder
@TableName("files")
public class FilesEntity {

    /**
     * 文件ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 文件名
     */
    @TableField("file_name")
    private String fileName;


    /**
     * 文件上传路径
     */
    @TableField("file_path")
    private String filePath;

    /**
     * 文件大小
     */
    @TableField("file_size")
    private Long fileSize;


    /**
     * 保存在oss中的标识
     */
    @TableField("oss_key")
    private String ossKey;

    /**
     * 文件上传状态
     */
    @TableField("upload_status")
    private Integer uploadStatus;
}
