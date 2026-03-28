package com.docai.ai.service.impl;

import com.docai.ai.config.LlmConfig;
import com.docai.ai.service.AbstractLlmProvider;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.UserMessage;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

// 通义千问系列提供商（含VL多模态模型）

@Service
@Slf4j
public class DashScopeProvider extends AbstractLlmProvider {

    @Autowired
    @Qualifier("dashScopeLlmConfig")
    private LlmConfig llmConfig;

    public DashScopeProvider() {
        super(null);
    }

    @PostConstruct
    public void init() {
        this.config = llmConfig;
    }

    @Autowired
    @Qualifier("defaultChatClient")
    private ChatClient client;

    @Autowired
    private ChatModel chatModel;

    private static final String VL_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";

    private static final List<String> SUPPORTED_MODELS = List.of(
        "qwen-turbo",
        "qwen-plus",
        "qwen-max",
        "qwen-long",
        "qwen2.5-72b-instruct",
        "qwen2.5-32b-instruct",
        "qwen2.5-14b-instruct",
        "qwen2.5-7b-instruct",
        "qwen2.5-vl-7b-instruct"
    );

    // VL模型列表（需要走OpenAI兼容的多模态接口）
    private static final List<String> VL_MODELS = List.of(
        "qwen2.5-vl-7b-instruct"
    );

    @Override
    public String generateText(String prompt) {
        String response = client.prompt()
                .user(prompt)
                .call()
                .content();
        log.info("调用通义千问大模型(默认模型)，结果长度：{}", response != null ? response.length() : 0);
        return response;
    }

    @Override
    public String generateText(String prompt, String modelName) {
        if (modelName == null || modelName.isBlank() || modelName.equals(config.getModel())) {
            return generateText(prompt);
        }
        // VL模型走OpenAI兼容接口（纯文本模式）
        if (VL_MODELS.contains(modelName)) {
            return generateMultiModalText(prompt, null, modelName);
        }
        // 使用ChatModel直接调用指定模型
        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .withModel(modelName)
                .build();
        Prompt chatPrompt = new Prompt(new UserMessage(prompt), options);
        String response = chatModel.call(chatPrompt).getResult().getOutput().getText();
        log.info("调用通义千问大模型(模型:{})，结果长度：{}", modelName, response != null ? response.length() : 0);
        return response;
    }

    /**
     * 多模态调用：通过OpenAI兼容接口调用VL模型，支持文本+图片
     * 图片以base64编码传入，模型可识别docx中的图像内容
     */
    @Override
    public String generateMultiModalText(String prompt, List<String> imageBase64List, String modelName) {
        if (modelName == null || modelName.isBlank()) {
            modelName = "qwen2.5-vl-7b-instruct";
        }
        try {
            // 构建消息内容数组
            JSONArray contentArray = new JSONArray();

            // 添加文本部分
            JSONObject textPart = new JSONObject();
            textPart.put("type", "text");
            textPart.put("text", prompt);
            contentArray.add(textPart);

            // 添加图片部分（如果有）
            if (imageBase64List != null && !imageBase64List.isEmpty()) {
                for (String imgBase64 : imageBase64List) {
                    if (imgBase64 != null && !imgBase64.isBlank()) {
                        JSONObject imagePart = new JSONObject();
                        imagePart.put("type", "image_url");
                        JSONObject imageUrl = new JSONObject();
                        // 自动补全data URI前缀
                        if (!imgBase64.startsWith("data:")) {
                            imgBase64 = "data:image/png;base64," + imgBase64;
                        }
                        imageUrl.put("url", imgBase64);
                        imagePart.put("image_url", imageUrl);
                        contentArray.add(imagePart);
                    }
                }
            }

            // 构建请求体
            JSONObject message = new JSONObject();
            message.put("role", "user");
            message.put("content", contentArray);

            JSONArray messages = new JSONArray();
            messages.add(message);

            JSONObject requestBody = new JSONObject();
            requestBody.put("model", modelName);
            requestBody.put("messages", messages);
            requestBody.put("max_tokens", 8192);

            String apiKey = config.getApiKey();
            if (apiKey == null || apiKey.isBlank() || "local-placeholder-key".equals(apiKey)) {
                log.error("DashScope API Key未配置，无法调用VL模型");
                throw new RuntimeException("DashScope API Key未配置");
            }

            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(60))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(VL_BASE_URL))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toJSONString()))
                    .timeout(Duration.ofSeconds(300))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("VL模型调用失败，HTTP状态码: {}，响应: {}", response.statusCode(), response.body());
                // 不将原始错误暴露给前端，抛出友好异常
                throw new RuntimeException("AI模型调用失败，请稍后重试");
            }

            JSONObject responseJson = JSON.parseObject(response.body());
            JSONArray choices = responseJson.getJSONArray("choices");
            if (choices != null && !choices.isEmpty()) {
                String content = choices.getJSONObject(0).getJSONObject("message").getString("content");
                log.info("调用VL模型({})成功，结果长度：{}", modelName, content != null ? content.length() : 0);
                return content;
            }

            log.warn("VL模型返回空结果");
            return "";
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("VL模型调用异常: {}", e.getMessage(), e);
            throw new RuntimeException("AI模型调用异常，请稍后重试");
        }
    }

    @Override
    public List<String> getSupportedModels() {
        return SUPPORTED_MODELS;
    }

    @Override
    public String getDefaultModel() {
        return config.getModel();
    }
}
