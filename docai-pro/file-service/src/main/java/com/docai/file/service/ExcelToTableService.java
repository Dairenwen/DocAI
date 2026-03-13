package com.docai.file.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 用于做excel到mysql表转换的服务接口
 */
public interface ExcelToTableService {

    /**
     * 把已有的excel文件转换成mysql表
     */
    List<String> convertExcelToTable(MultipartFile file, Long fileId);

    /**
     * 一键复原数据
     */
    void insertData(String tableName, MultipartFile file, int sheetIndex);
}
