package com.docai.file.service;

import java.util.List;

/**
 * 文件映射表的服务类
 */
public interface FileTableMappingService {


    void saveMappings(Long fileId, List<String> tableNames, List<String> sheetNames);

    List<String> getTableNamesByFileId(Long fileId);

    void deleteByFileId(Long fileId);
}
