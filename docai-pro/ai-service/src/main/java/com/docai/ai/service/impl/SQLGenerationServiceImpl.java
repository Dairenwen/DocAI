package com.docai.ai.service.impl;

import com.docai.ai.service.FileMetaDataService;
import com.docai.ai.service.LlmService;
import com.docai.ai.service.SQLGenerationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 生成SQL服务的实现类
 */
@Service
@Slf4j
public class SQLGenerationServiceImpl implements SQLGenerationService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private LlmService llmService;

    @Autowired
    private FileMetaDataService fileMetaDataService;


    @Override
    public List<Map<String, Object>> getTableStructure(String tableName) {
        String sql = "DESCRIBE `" + tableName + "`";
        return jdbcTemplate.queryForList(sql);
    }

    @Override
    public String get(String prompt) {
        return llmService.generateText(prompt);
    }

    @Override
    public List<Map<String, Object>> excuteQuery(String sql, String tableName) {
        List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);
        return fileMetaDataService.mapQuery(result, tableName);
    }

    @Override
    public int excuteUpdate(String sql) {
        return jdbcTemplate.update(sql);
    }
}
