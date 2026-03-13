package com.docai.ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云通义千问配置信息
 */
@Configuration
public class DashScopeConfig {

    // dashscope密钥
    @Value("${spring.ai.alibaba.dashscope.api-key}")
    private String apiKey;


    // dashscope下面的模型
    @Value("${spring.ai.alibaba.dashscope.chat.options.model}")
    private String model;


    // dashscope温度参数
    @Value("${spring.ai.alibaba.dashscope.chat.options.temperature}")
    private Double temperature;

    // 生成的最大token数
    @Value("${spring.ai.alibaba.dashscope.chat.options.max-tokens}")
    private Integer maxTokens;

    // 大模型连接超时时间
    @Value("${spring.ai.alibaba.dashscope.http.connect-timeout}")
    private Integer connectTimeout;

    // 大模型读取超时时间
    @Value("${spring.ai.alibaba.dashscope.http.read-timeout}")
    private Integer readTimeout;

    // 大模型写入超时时间
    @Value("${spring.ai.alibaba.dashscope.http.write-timeout}")
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
