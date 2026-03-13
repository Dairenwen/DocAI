package com.docai.ai.service;

import java.util.List;
import java.util.Map;

/**
 * 大模型相关的服务接口
 */
public interface LlmService {

    // 获取所有可用的大模型列表
    List<Map<String, Object>> getProvidersList();

    // 用来切换大模型提供商
    Map<String, String> switchProvider(String providerName);

    Map<String, String> getCurrentProviderName();

    String generateText(String prompt);
}
