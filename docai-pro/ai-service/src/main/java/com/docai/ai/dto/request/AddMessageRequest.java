package com.docai.ai.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AddMessageRequest {
    @NotNull(message = "角色不能为空")
    @NotBlank(message = "角色不能为空")
    private String role;

    @NotNull(message = "内容不能为空")
    private String content;
}
