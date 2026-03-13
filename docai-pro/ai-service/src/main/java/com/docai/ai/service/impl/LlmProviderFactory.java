package com.docai.ai.service.impl;

import com.docai.ai.service.LlmProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 大模型提供商的工厂类
 */
@Component
public class LlmProviderFactory {

    private final Map<String, LlmProvider> providerMap;

    @Autowired
    public LlmProviderFactory(List<LlmProvider> providers) {
        this.providerMap = providers.stream()
                .collect(Collectors.toMap(
                        LlmProvider::getProviderName,
                        Function.identity(),
                        (existing, replacement) -> existing
                ));
    }

    // 获取所有可用的提供商
    public List<LlmProvider> getAllProviders() {
        return providerMap.values().stream()
                .filter(LlmProvider::isAvailable)
                .collect(Collectors.toList());
    }

    // 用来判断大模型提供商是否可用
    public boolean isProviderAvailable(String providerName) {
        LlmProvider llmProvider = providerMap.get(providerName);
        return llmProvider != null && llmProvider.isAvailable();
    }

    // 根据提供商名称去获取大模型提供商
    public LlmProvider getProvider(String providerName) {
        return  providerMap.get(providerName);
    }
}
