package com.docai.file.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.docai.file.entity.FileTableMappingEntity;
import com.docai.file.mapper.FileTableMappingMapper;
import com.docai.file.service.FileTableMappingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 文件映射表的服务实现类
 */
@Service
public class FileTableMappingServiceImpl implements FileTableMappingService {

    @Autowired
    private FileTableMappingMapper fileTableMappingMapper;


    @Override
    public void saveMappings(Long fileId, List<String> tableNames, List<String> sheetNames) {
        for (int i = 0; i <tableNames.size(); i++) {
            FileTableMappingEntity fileTableMappingEntity = new FileTableMappingEntity();
            fileTableMappingEntity.setFileId(fileId);
            fileTableMappingEntity.setTableName(tableNames.get(i));
            if (sheetNames != null && sheetNames.get(i) != null) {
                fileTableMappingEntity.setSheetName(sheetNames.get(i));
            }
            fileTableMappingEntity.setSheetIndex(i);
            fileTableMappingMapper.insert(fileTableMappingEntity);
        }
    }

    @Override
    public List<String> getTableNamesByFileId(Long fileId) {
        QueryWrapper<FileTableMappingEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("file_id", fileId).orderByAsc("sheet_index");
        return fileTableMappingMapper.selectList(queryWrapper).stream().map(FileTableMappingEntity::getTableName).collect(Collectors.toList());
    }

    @Override
    public void deleteByFileId(Long fileId) {
        LambdaQueryWrapper<FileTableMappingEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FileTableMappingEntity::getFileId, fileId);
        fileTableMappingMapper.delete(queryWrapper);
    }
}
