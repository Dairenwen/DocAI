package com.docai.file.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.docai.file.entity.FieldMappingEntity;
import com.docai.file.mapper.FieldMappingMapper;
import com.docai.file.service.FieldMappingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 保存字段关系的实现类
 */
@Service
@Slf4j
public class FieldMappingServiceImpl implements FieldMappingService {

    @Autowired
    private FieldMappingMapper fieldMappingMapper;


    @Override
    @Transactional(rollbackFor = Exception.class)
    public int saveMappings(Long fileId, String tableName, List<String> originalHeader, List<String> dbFieldName) {

        List<FieldMappingEntity> mappingEntities = new ArrayList<>();
        for (int i = 0; i < originalHeader.size(); i++) {
            String excelHeader = originalHeader.get(i);
            String  dbName = dbFieldName.get(i);

            FieldMappingEntity fieldMappingEntity = new FieldMappingEntity();
            fieldMappingEntity.setFileId(fileId);
            fieldMappingEntity.setTableName(tableName);
            fieldMappingEntity.setDbFieldName(dbName);
            fieldMappingEntity.setOriginalHeader(excelHeader);
            fieldMappingEntity.setFieldOrder(i);
            mappingEntities.add(fieldMappingEntity);
        }
        return fieldMappingMapper.batchInsert(mappingEntities);
    }

    @Override
    public Map<String, String> getMappingMapByTableName(String tableName) {
        QueryWrapper<FieldMappingEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("table_name", tableName);
        List<FieldMappingEntity> fieldMappingEntities = fieldMappingMapper.selectList(queryWrapper);
        Map<String, String> map = new LinkedHashMap<>();
        for (FieldMappingEntity fieldMappingEntity :fieldMappingEntities) {
            map.put(fieldMappingEntity.getDbFieldName(), fieldMappingEntity.getOriginalHeader());
        }
        return map;
    }

    @Override
    public void deleteByFileId(Long fileId) {
        LambdaQueryWrapper<FieldMappingEntity> queryWrapper  = new LambdaQueryWrapper<>();
        queryWrapper.eq(FieldMappingEntity::getFileId, fileId);
        fieldMappingMapper.delete(queryWrapper);
    }
}
