package com.docai.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * AI请求记录表的实体类
 */
@Data
@TableName("ai_requests")
public class AiRequestEntity {

    // 主键ID
    @TableId(type = IdType.AUTO)
    private Long id;

    // 用户ID
    private Long userId;

    // 文件ID
    private Long fileId;

    // 用户输入内容
    private String userInput;

    // AI返回的响应内容
    private String aiResponse;

    // 请求状态 0-处理中  1-成功  2-失败   3-部分成功
    private Integer status;

}
