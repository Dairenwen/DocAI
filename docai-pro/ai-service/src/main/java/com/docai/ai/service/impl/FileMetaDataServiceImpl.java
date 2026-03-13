package com.docai.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.docai.ai.mapper.FieldMappingsReadOnlyMapper;
import com.docai.ai.mapper.FileTableMappingsReadOnlyMapper;
import com.docai.ai.mapper.FilesReadOnlyMapper;
import com.docai.ai.service.FileMetaDataService;
import com.docai.file.entity.FieldMappingEntity;
import com.docai.file.entity.FileTableMappingEntity;
import com.docai.file.entity.FilesEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI服务下用来用去文件相关数据的实现类
 */
@Service
@Slf4j
public class FileMetaDataServiceImpl  implements FileMetaDataService {

    @Autowired
    private FilesReadOnlyMapper filesReadOnlyMapper;

    @Autowired
    private FileTableMappingsReadOnlyMapper fileTableMappingsReadOnlyMapper;

    @Autowired
    private FieldMappingsReadOnlyMapper fieldMappingsReadOnlyMapper;


    @Override
    public FilesEntity getFileById(Long userId, Long fileId) {
        FilesEntity filesEntity = filesReadOnlyMapper.selectById(fileId);
        if (filesEntity != null && filesEntity.getUserId().equals(userId)) {
            return filesEntity;
        }
        return null;
    }

    @Override
    public List<String> getTableNamesByFileId(Long fileId) {
        LambdaQueryWrapper<FileTableMappingEntity> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.select(FileTableMappingEntity::getTableName)
                .eq(FileTableMappingEntity::getFileId, fileId)
                .orderByAsc(FileTableMappingEntity::getSheetIndex)
                .orderByAsc(FileTableMappingEntity::getId);
        return fileTableMappingsReadOnlyMapper.selectList(lambdaQueryWrapper)
                .stream()
                .map(FileTableMappingEntity::getTableName)
                .collect(Collectors.toList())
                ;
    }

    @Override
    public String getTableNameByFileIdAndHeader(String field, Long fileId) {
        LambdaQueryWrapper<FieldMappingEntity> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.select(FieldMappingEntity::getTableName)
                .eq(FieldMappingEntity::getFileId, fileId)
                .eq(FieldMappingEntity::getOriginalHeader, field)
                .last("limit 1");
        FieldMappingEntity fieldMappingEntity = fieldMappingsReadOnlyMapper.selectOne(lambdaQueryWrapper);
        if (fieldMappingEntity == null) return null;
        return fieldMappingEntity.getTableName();
    }

    @Override
    public List<Map<String, Object>> mapQuery(List<Map<String, Object>> result, String tableName) {
        // 1. 获取字段与表头的映射关系
        List<FieldMappingEntity> fieldMappingEntityList = listOrderMappings(tableName);

        // 2. 结合result，构建新的map
        Map<String, String> map = new HashMap<>();
        for (FieldMappingEntity fieldMappingEntity : fieldMappingEntityList) {
            String dbFieldName = fieldMappingEntity.getDbFieldName();
            String header = fieldMappingEntity.getOriginalHeader();
            if (StringUtils.hasText(dbFieldName)) {
                String originalHeader = StringUtils.hasText(header) ? header : dbFieldName;
                map.put(dbFieldName, header);
            }
        }

        return result.stream()
                .map(row -> {
                    Map<String, Object> mappedRow = new LinkedHashMap<>();
                    for (Map.Entry<String, Object> entry : row.entrySet()) {
                        String dbFieldName = entry.getKey();
                        Object value = entry.getValue();

                        String header = map.get(dbFieldName);
                        if (header != null) {
                            mappedRow.put(header, value);
                        } else {
                            mappedRow.put(dbFieldName, value);
                        }
                    }
                    return mappedRow;
                })
                .collect(Collectors.toList())
                ;
    }

    @Override
    public FilesEntity getFileById(Long fileId) {
        return filesReadOnlyMapper.selectById(fileId);
    }

    private List<FieldMappingEntity> listOrderMappings(String tableName) {
        LambdaQueryWrapper<FieldMappingEntity> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(FieldMappingEntity::getTableName, tableName)
                .orderByAsc(FieldMappingEntity::getFieldOrder)
                .orderByAsc(FieldMappingEntity::getId);
        return fieldMappingsReadOnlyMapper.selectList(lambdaQueryWrapper);
    }
}
