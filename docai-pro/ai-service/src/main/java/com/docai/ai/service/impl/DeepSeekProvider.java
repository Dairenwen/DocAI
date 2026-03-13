package com.docai.ai.service.impl;

import com.docai.ai.config.LlmConfig;
import com.docai.ai.service.AbstractLlmProvider;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * DeepSeek提供商实现类
 */
@Service
@Slf4j
public class DeepSeekProvider extends AbstractLlmProvider {

    @Autowired
    @Qualifier("deepSeekLlmConfig")
    private LlmConfig llmConfig;

    @Autowired
    private RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();


    public DeepSeekProvider() {
        super(null);
    }

    @PostConstruct
    public void init() {
        this.config = llmConfig;
    }

    @Override
    public String generateText(String prompt) {
        // 1. 构建请求体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.getModel()); // 添加模型
        requestBody.put("messages", new Object[] {
                Map.of("role", "user", "content", prompt)
        });  // 请求内容
        requestBody.put("temperature", config.getTemperature()); // 请求温度
        requestBody.put("max_tokens", config.getMaxTokens());  // 最大token数
        requestBody.put("stream", false); // 直接返回

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + config.getApiKey());

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
        // 2. 发送请求
        String url = config.getBaseUrl() + "/v1/chat/completions";
        // 3. 得到响应
        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

        // 4. 封装结果
        if (response.getStatusCode() == HttpStatus.OK) {
            try {
                JsonNode jsonNode = objectMapper.readTree((response.getBody()));
                String content = jsonNode.path("choices").get(0).path("message").path("content").asText();
                log.info("调用DeepSeek大模型成功: 提示词: {}, 返回结果：{}", prompt, content);
                return content;
            } catch (JsonProcessingException e) {
                log.error("调用DeepSeek大模型失败: {}", prompt);
                throw new RuntimeException(e);
            }
        }
        return "";
    }
}
