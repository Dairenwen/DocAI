package com.docai.ai.service.impl;

import com.docai.ai.config.LlmConfig;
import com.docai.ai.service.AbstractLlmProvider;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

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

    @Override
    public String generateText(String prompt) {

        String response = client.prompt()
                .user(prompt)
                .call()
                .content();

        log.info("调用通义千问大模型，提示词：{}, 结果是：{}", prompt, response);
        return response;
    }
}
