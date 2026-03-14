package com.docai.ai.service.impl;

import com.docai.ai.service.LlmProvider;
import com.docai.ai.service.LlmService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 大模型实现类
 */
@Service
@Slf4j
public class LlmServiceImpl implements LlmService {

    @Autowired
    private LlmProviderFactory providerFactory;

    @Value("${spring.ai.default-provider}")
    private String currentProvider;

    private String currentModel = null; // null = 使用提供商默认模型

    @Override
    public List<Map<String, Object>> getProvidersList() {
        List<Map<String, Object>> providersInfo = new ArrayList<>();

        for (LlmProvider provider : providerFactory.getAllProviders()) {
            Map<String, Object> map = new HashMap<>();
            map.put("name", provider.getProviderName());
            map.put("available", provider.isAvailable());
            map.put("models", provider.getSupportedModels());
            map.put("defaultModel", provider.getDefaultModel());
            providersInfo.add(map);
        }
        return providersInfo;
    }

    @Override
    public Map<String, String> switchProvider(String providerName) {
        // 支持 "provider:model" 格式
        String model = null;
        if (providerName.contains(":")) {
            String[] parts = providerName.split(":", 2);
            providerName = parts[0];
            model = parts[1];
        }

        if (!providerFactory.isProviderAvailable(providerName)) {
            throw new IllegalArgumentException("提供商不可用: " + providerName);
        }

        this.currentProvider = providerName;
        this.currentModel = model;
        log.info("已切换到大模型提供商: {}, 模型: {}", providerName, model != null ? model : "默认");
        Map<String, String> map = new HashMap<>();
        map.put("currentProvider", currentProvider);
        map.put("currentModel", currentModel != null ? currentModel : providerFactory.getProvider(currentProvider).getDefaultModel());
        return map;
    }

    @Override
    public Map<String, String> getCurrentProviderName() {
        Map<String, String> map = new HashMap<>();
        map.put("currentProvider", currentProvider);
        LlmProvider provider = providerFactory.getProvider(currentProvider);
        map.put("currentModel", currentModel != null ? currentModel : (provider != null ? provider.getDefaultModel() : ""));
        return map;
    }

    @Override
    public String generateText(String prompt) {
        LlmProvider llmProvider = providerFactory.getProvider(currentProvider);
        if (currentModel != null && !currentModel.isBlank()) {
            return llmProvider.generateText(prompt, currentModel);
        }
        return llmProvider.generateText(prompt);
    }

    @Override
    public String generateText(String prompt, String provider, String model) {
        LlmProvider llmProvider = providerFactory.getProvider(provider != null ? provider : currentProvider);
        if (llmProvider == null) {
            llmProvider = providerFactory.getProvider(currentProvider);
        }
        if (model != null && !model.isBlank()) {
            return llmProvider.generateText(prompt, model);
        }
        return llmProvider.generateText(prompt);
    }
}
