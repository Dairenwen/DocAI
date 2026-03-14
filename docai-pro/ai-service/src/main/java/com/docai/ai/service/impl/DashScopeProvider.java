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

import java.util.List;

// 通义千问系列提供商

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

    private static final List<String> SUPPORTED_MODELS = List.of(
        "qwen-turbo",
        "qwen-plus",
        "qwen-max",
        "qwen-long",
        "qwen2.5-72b-instruct",
        "qwen2.5-32b-instruct",
        "qwen2.5-14b-instruct",
        "qwen2.5-7b-instruct"
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
        // 使用ChatModel直接调用指定模型
        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .withModel(modelName)
                .build();
        Prompt chatPrompt = new Prompt(new UserMessage(prompt), options);
        String response = chatModel.call(chatPrompt).getResult().getOutput().getText();
        log.info("调用通义千问大模型(模型:{})，结果长度：{}", modelName, response != null ? response.length() : 0);
        return response;
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
