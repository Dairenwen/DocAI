package com.docai.ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * DeepSeek相关配置类
 */
@Configuration
public class DeepSeekConfig {

    // api密钥
    @Value("${spring.ai.deepseek.api-key}")
    private String apiKey;

    // 访问基地址
    @Value("${spring.ai.deepseek.base-url}")
    private String baseUrl;

    // 模型的名称
    @Value("${spring.ai.deepseek.chat.options.model}")
    private String model;

    // 模型的名称
    @Value("${spring.ai.deepseek.chat.options.temperature}")
    private Double temperature;

    // 最大token数
    @Value("${spring.ai.deepseek.chat.options.max-tokens}")
    private Integer maxTokens;

    // 最大连接超时时间
    @Value("${spring.ai.deepseek.http.connect-timeout}")
    private Integer connectTimeout;

    // 最大读取超时时间
    @Value("${spring.ai.deepseek.http.read-timeout}")
    private Integer readTimeout;

    // 最大写入超时时间
    @Value("${spring.ai.deepseek.http.write-timeout}")
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
