package com.docai.ai.service;

import java.util.List;
import java.util.Map;

/**
 * AI大模型服务接口
 */
public interface AiModelService {
    String generateAiResponse(String prompt, List<Map<String, Object>> resultData);

    List<String> getFieldsFromUserInput(String userInput);

    String getSql(String userInput, String tableName, List<Map<String, Object>> tableStructure);

    String getUpdateSql(String userInput, String tableName, List<Map<String, Object>> tableStructure);
}
