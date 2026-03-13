package com.docai.ai.service;

import java.util.List;
import java.util.Map;

/**
 * 用来生成SQL的服务接口
 */
public interface SQLGenerationService {
    List<Map<String, Object>> getTableStructure(String tableName);


    String get(String prompt);

    List<Map<String, Object>> excuteQuery(String sql, String tableName);

    int excuteUpdate(String sql);
}
