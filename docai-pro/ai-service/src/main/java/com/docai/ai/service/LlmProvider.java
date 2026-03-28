package com.docai.ai.service;

import java.util.List;

/**
 * 大模型提供商的接口
 */
public interface LlmProvider {

    // 获取提供商名称
    String getProviderName();

    // 检查提供商是否可用
    boolean isAvailable();

    String generateText(String prompt);

    // 使用指定模型生成文本（默认实现调用无参版本）
    default String generateText(String prompt, String modelName) {
        return generateText(prompt);
    }

    /**
     * 多模态文本生成：支持文本+图片（base64）输入
     * @param prompt 文本提示词
     * @param imageBase64List 图片base64编码列表（可为空）
     * @param modelName 模型名称
     * @return 模型生成的文本
     */
    default String generateMultiModalText(String prompt, List<String> imageBase64List, String modelName) {
        return generateText(prompt, modelName);
    }

    // 返回该提供商支持的模型列表
    default List<String> getSupportedModels() {
        return List.of();
    }

    // 返回当前默认模型名
    default String getDefaultModel() {
        return "";
    }
}
