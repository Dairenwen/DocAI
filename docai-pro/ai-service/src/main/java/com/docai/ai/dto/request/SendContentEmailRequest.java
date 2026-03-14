package com.docai.ai.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SendContentEmailRequest {

    @NotBlank(message = "收件人邮箱必填")
    @Email(message = "必须是正确的邮箱格式")
    private String email;

    @NotBlank(message = "邮件内容必填")
    private String content;

    private String subject;
}
