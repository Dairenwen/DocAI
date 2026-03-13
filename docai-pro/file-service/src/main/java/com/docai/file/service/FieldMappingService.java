package com.docai.file.service;


import java.util.List;
import java.util.Map;

public interface FieldMappingService {
    // 保存字段映射关系
    int saveMappings(Long fileId, String tableName, List<String> originalHeader, List<String> dbFieldName);

    Map<String, String> getMappingMapByTableName(String tableName);

    void deleteByFileId(Long fileId);
}
