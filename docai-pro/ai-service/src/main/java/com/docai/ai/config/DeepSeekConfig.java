package com.docai.ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * DeepSeek相关配置类
 */
@Configuration
public class DeepSeekConfig {

    @Value("${spring.ai.deepseek.api-key:}")
    private String apiKey;

    @Value("${spring.ai.deepseek.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${spring.ai.deepseek.chat.options.model:deepseek-chat}")
    private String model;

    @Value("${spring.ai.deepseek.chat.options.temperature:0.7}")
    private Double temperature;

    @Value("${spring.ai.deepseek.chat.options.max-tokens:2048}")
    private Integer maxTokens;

    @Value("${spring.ai.deepseek.http.connect-timeout:60000}")
    private Integer connectTimeout;

    @Value("${spring.ai.deepseek.http.read-timeout:60000}")
    private Integer readTimeout;

    @Value("${spring.ai.deepseek.http.write-timeout:60000}")
    private Integer writeTimeout;


    @Bean("deepSeekLlmConfig")
    public LlmConfig deepSeekLlmConfig() {
        LlmConfig config = new LlmConfig();
        config.setProviderName("deepseek");
        config.setModel(model);
        config.setApiKey(apiKey);
        config.setBaseUrl(baseUrl);
        config.setTemperature(temperature);
        config.setMaxTokens(maxTokens);
        config.setConnectTimeout(connectTimeout);
        config.setReadTimeout(readTimeout);
        config.setWriteTimeout(writeTimeout);
        return config;
    }
}
