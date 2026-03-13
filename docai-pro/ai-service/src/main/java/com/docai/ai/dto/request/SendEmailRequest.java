package com.docai.ai.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 发送邮件的请求实体类
 */
@Data
public class SendEmailRequest {

    // 收件人的邮箱
    @NotNull(message = "收件人的邮箱必填")
    @Email(message = "必须是正确的邮箱格式")
    private String email;


    // 修改后的excel下载链接地址
    @NotNull(message = "下载的excel地址必填")
    private String excelUrl;
}
