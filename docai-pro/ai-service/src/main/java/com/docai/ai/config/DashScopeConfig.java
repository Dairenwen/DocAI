package com.docai.ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云通义千问配置信息
 */
@Configuration
public class DashScopeConfig {

    @Value("${spring.ai.alibaba.dashscope.api-key:}")
    private String apiKey;

    @Value("${spring.ai.alibaba.dashscope.chat.options.model:qwen-plus}")
    private String model;

    @Value("${spring.ai.alibaba.dashscope.chat.options.temperature:0.7}")
    private Double temperature;

    @Value("${spring.ai.alibaba.dashscope.chat.options.max-tokens:2048}")
    private Integer maxTokens;

    @Value("${spring.ai.alibaba.dashscope.http.connect-timeout:60000}")
    private Integer connectTimeout;

    @Value("${spring.ai.alibaba.dashscope.http.read-timeout:60000}")
    private Integer readTimeout;

    @Value("${spring.ai.alibaba.dashscope.http.write-timeout:60000}")
    private Integer writeTimeout;

    @Bean("dashScopeLlmConfig")
    public LlmConfig dashScopeLlmConfig() {
        LlmConfig config = new LlmConfig();
        config.setProviderName("dashscope");
        config.setModel(model);
        config.setApiKey(apiKey);
        config.setBaseUrl("https://dashscope.aliyuncs.com");
        config.setTemperature(temperature);
        config.setMaxTokens(maxTokens);
        config.setConnectTimeout(connectTimeout);
        config.setReadTimeout(readTimeout);
        config.setWriteTimeout(writeTimeout);
        return config;
    }

}
