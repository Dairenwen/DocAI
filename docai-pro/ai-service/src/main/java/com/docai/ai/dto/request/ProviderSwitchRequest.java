package com.docai.ai.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 切换大模型的请求实体类
 */
@Data
public class ProviderSwitchRequest {

    @NotBlank(message = "提供商名称不能为空")
    private String providerName;
}
