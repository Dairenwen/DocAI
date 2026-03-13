package com.docai.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 用户表映射的实体类，users表
 */
@Data
@TableName("users")
public class UserEntity {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户名
     */
    @TableField(value = "username")
    private String userName;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 密码哈希
     */
    private String passwordHash;

}
