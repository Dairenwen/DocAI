package com.docai.ai.config;

import lombok.Data;

/**
 * 大模型配置信息
 */
@Data
public class LlmConfig {

    // 提供商的名称
    private String providerName;

    // 模型的名称
    private String model;

    // 密钥
    private String apiKey;

    // 服务基础地址
    private String baseUrl;

    // 模型温度
    private Double temperature;

    // 最大token
    private Integer maxTokens;

    // 最大连接超时时间
    private Integer connectTimeout;

    // 最大读取超时时间
    private Integer readTimeout;

    // 最大写入超时时间
    private Integer writeTimeout;

}
