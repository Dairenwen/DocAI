package com.docai.ai.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI流式对话请求类
 */
@Data
public class AiChatRequest {

    // 文件ID
    @NotNull(message = "文件ID不能为空")
    private Long fileId;

    // 用户输入的自然语言
    @NotNull(message = "用户输入不能为空")
    @Size(max = 2000, message = "用户输入长度不能超过2000字符")
    private String userInput;

}
