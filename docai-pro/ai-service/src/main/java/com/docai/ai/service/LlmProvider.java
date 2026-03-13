package com.docai.ai.service;

/**
 * 大模型提供商的接口
 */
public interface LlmProvider {

    // 获取提供商名称
    String getProviderName();

    // 检查提供商是否可用
    boolean isAvailable();

    String generateText(String prompt);
}
