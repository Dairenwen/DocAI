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

    @Override
    public List<Map<String, Object>> getProvidersList() {
        List<Map<String, Object>> providersInfo = new ArrayList<>();

        for (LlmProvider provider : providerFactory.getAllProviders()) {
            Map<String, Object> map = new HashMap<>();
            map.put("name", provider.getProviderName());
            map.put("available", provider.isAvailable());
            providersInfo.add(map);
        }
        return providersInfo;
    }

    @Override
    public Map<String, String> switchProvider(String providerName) {
        // 1. providerName校验
        if (!providerFactory.isProviderAvailable(providerName)) {
            throw new IllegalArgumentException("提供商不可用: " + providerName);
        }

        // 2. 切换当前的模型提供商
        this.currentProvider = providerName;
        log.info("已经切换到大模型提供商: {}", providerName);
        Map<String, String> map = new HashMap<>();
        map.put("currentProvider", currentProvider);
        return map;
    }

    @Override
    public Map<String, String> getCurrentProviderName() {
        Map<String, String> map = new HashMap<>();
        map.put("currentProvider", currentProvider);
        return map;
    }

    @Override
    public String generateText(String prompt) {
        LlmProvider llmProvider = providerFactory.getProvider(currentProvider);
        return llmProvider.generateText(prompt);
    }
}
