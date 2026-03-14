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
import java.util.List;
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
        return callDeepSeek(prompt, config.getModel());
    }

    @Override
    public String generateText(String prompt, String modelName) {
        return callDeepSeek(prompt, (modelName != null && !modelName.isBlank()) ? modelName : config.getModel());
    }

    private static final List<String> SUPPORTED_MODELS = List.of(
        "deepseek-chat",
        "deepseek-reasoner"
    );

    @Override
    public List<String> getSupportedModels() {
        return SUPPORTED_MODELS;
    }

    @Override
    public String getDefaultModel() {
        return config.getModel();
    }

    private String callDeepSeek(String prompt, String model) {
        // 1. 构建请求体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", new Object[] {
                Map.of("role", "user", "content", prompt)
        });
        requestBody.put("temperature", config.getTemperature());
        requestBody.put("max_tokens", config.getMaxTokens());
        requestBody.put("stream", false);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + config.getApiKey());

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
        String url = config.getBaseUrl() + "/v1/chat/completions";
        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

        if (response.getStatusCode() == HttpStatus.OK) {
            try {
                JsonNode jsonNode = objectMapper.readTree((response.getBody()));
                String content = jsonNode.path("choices").get(0).path("message").path("content").asText();
                log.info("调用DeepSeek大模型(模型:{})成功，结果长度：{}", model, content != null ? content.length() : 0);
                return content;
            } catch (JsonProcessingException e) {
                log.error("调用DeepSeek大模型失败: {}", e.getMessage());
                throw new RuntimeException(e);
            }
        }
        return "";
    }
}
