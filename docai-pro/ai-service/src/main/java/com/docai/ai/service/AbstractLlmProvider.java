package com.docai.ai.service;

import com.docai.ai.config.LlmConfig;

/**
 * 大模型提供商的抽象基类
 */
public abstract class AbstractLlmProvider implements LlmProvider {

    protected LlmConfig config;

    public AbstractLlmProvider(LlmConfig config) {
        this.config = config;
    }

    @Override
    public String getProviderName() {
        return config.getProviderName();
    }

    @Override
    public boolean isAvailable() {
        return config != null;
    }

}
