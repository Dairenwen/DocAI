package com.docai.ai.service;

import com.docai.file.entity.FilesEntity;

import java.util.List;
import java.util.Map;

/**
 * AI服务下用来用去文件相关数据的接口
 */
public interface FileMetaDataService {
    FilesEntity getFileById(Long userId, Long fileId);

    List<String> getTableNamesByFileId(Long fileId);

    String getTableNameByFileIdAndHeader(String field, Long fileId);

    List<Map<String, Object>> mapQuery(List<Map<String, Object>> result, String tableName);

    FilesEntity getFileById(Long fileId);
}
