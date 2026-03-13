package com.docai.file.service.impl;

import com.alibaba.cloud.commons.lang.StringUtils;
import com.aliyun.oss.model.OSSObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.docai.common.service.OssService;
import com.docai.common.util.FileValidationUtil;
import com.docai.file.dto.request.FileDeleteRequest;
import com.docai.file.dto.request.FileListRequest;
import com.docai.file.dto.request.FileUploadRequest;
import com.docai.file.dto.response.ExcelPreviewResponse;
import com.docai.file.dto.response.FileInfoResponse;
import com.docai.file.dto.response.FileUploadResponse;
import com.docai.file.entity.FilesEntity;
import com.docai.file.mapper.FilesMapper;
import com.docai.file.service.ExcelToTableService;
import com.docai.file.service.FieldMappingService;
import com.docai.file.service.FileTableMappingService;
import com.docai.file.service.FilesService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 操作文件接口的实现类
 */
@Service
@Slf4j
public class FileServiceImpl implements FilesService {

    @Autowired
    private OssService ossService;

    @Autowired
    private ExcelToTableService excelToTableService;

    @Autowired
    private FilesMapper filesMapper;

    @Autowired
    private FileTableMappingService fileTableMappingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FieldMappingService fieldMappingService;


    @Override
    public FileUploadResponse upload(MultipartFile file, FileUploadRequest request, Long userId) {
        // 1. 校验文件（文件类型、文件大小）
        if (!FileValidationUtil.validateFileFormat(file)) {
            throw new IllegalArgumentException("只能处理excel文件");
        }

        if (!FileValidationUtil.ifOutOfLarge(file)) {
            throw new IllegalArgumentException("文件带下不能超过50MB");
        }

        // 2. 上传文件到阿里云OSS
        String fileUrl = ossService.uploadFile(file, request.getCategory());

        // 3. 创建文件记录
        FilesEntity filesEntity = FilesEntity.builder()
                .userId(userId)
                .fileName(file.getOriginalFilename())
                .filePath(request.getCategory() == null ? "upload" : request.getCategory())
                .fileSize(file.getSize())
                .ossKey(getOssKey(fileUrl))
                .build();
        filesMapper.insert(filesEntity);


        // 4. 把excel转化成mysql的表
        List<String> tableNames = excelToTableService.convertExcelToTable(file, filesEntity.getId());

        fileTableMappingService.saveMappings(filesEntity.getId(), tableNames, null);

        // 5. 构建响应
        FileUploadResponse response = FileUploadResponse.builder()
                .fileId(filesEntity.getId())
                .fileName(filesEntity.getFileName())
                .fileSize(filesEntity.getFileSize())
                .fileUrl(fileUrl)
                .uploadStatus(1)
                .ossKey(filesEntity.getOssKey())
                .build();


        return response;
    }

    @Override
    public IPage<FileInfoResponse> list(FileListRequest request) {
        // 1. 构建查询对象
        QueryWrapper<FilesEntity> queryWrapper = new QueryWrapper<>();
        // 2. 在查询对象中间去构建请求参数
        queryWrapper.eq("user_id", request.getUserId());
        if (StringUtils.isNotBlank(request.getFileName())) {
            queryWrapper.like("file_name", request.getFileName());
        }

        if (request.getUploadStatus() != null) {
            queryWrapper.eq("upload_status", request.getUploadStatus());
        }

        queryWrapper.orderByDesc("id");

        // 3. 查询出结构，创建分页对象
        long current = request.getPageNum() != null ? request.getPageNum() : 1;
        long size = request.getPageSize() != null ? request.getPageSize() : 10;
        Page<FilesEntity> page = new Page<>(current, size);
        IPage<FilesEntity> entityIPage = filesMapper.selectPage(page, queryWrapper);

        // 4. 构造响应
        List<FileInfoResponse> responseList = entityIPage.getRecords().stream()
                .map(this::convert)
                .collect(Collectors.toList());
        Page<FileInfoResponse> responsePage = new Page<>(current, size);
        responsePage.setRecords(responseList);
        responsePage.setTotal(entityIPage.getTotal());
        responsePage.setPages(entityIPage.getPages());
        return responsePage;
    }

    @Override
    public void downloadFile(Long fileId, HttpServletResponse response, Long userId) {
        // 1 查询文件信息
        FilesEntity filesEntity = filesMapper.selectById(fileId);
        if (fileId == null) {
            log.error("文件不存在 {}", fileId);
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            try {
                response.getWriter().write("{\"error\":\"文件不存在\"}");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return;
        }

        // 2 检查用户是否拥有文件的权限
        if (!filesEntity.getUserId().equals(userId)){
            log.error("用户没权限下载当前文件{}", fileId);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            try {
                response.getWriter().write("{\"error\":\"用户没权限\"}");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return;
        }

        // 3 从oss上去拉文件
        response.reset();
        response.setContentType(FileValidationUtil.getContentType(filesEntity.getFileName()));
        response.setCharacterEncoding("UTF-8");

        OSSObject ossObject = ossService.getObject(filesEntity.getOssKey());
        // 设置文件大小
        long contentLength = ossObject.getObjectMetadata().getContentLength();
        response.setContentLengthLong(contentLength);
        response.setBufferSize(65536);

        try {
            InputStream inputStream = ossObject.getObjectContent();
            OutputStream outputStream = response.getOutputStream();
            byte[] buffer = new byte[65536];
            int byteRead = 0;
            long totalByteRead = 0;

            while ((byteRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, byteRead);
                totalByteRead += byteRead;

                if (totalByteRead % (1024 * 1024) == 0) {
                    outputStream.flush();
                }
            }

            outputStream.flush();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public ExcelPreviewResponse previewExcel(Long fileId, Long userId, Integer page, Integer pageSize, Integer sheetIndex) {
        // 1. 校验文件权限
        FilesEntity filesEntity = filesMapper.selectByUserIdAndFileId(userId, fileId);
        if (filesEntity == null) {
            throw new IllegalArgumentException("文件不存在或者用户无权限");
        }
        // 2. 根据fileId获取所有的表
        List<String> tableNames = fileTableMappingService.getTableNamesByFileId(fileId);
        String currentTableName = tableNames.get(sheetIndex);
        Long totalRecords = getTotalRecords(currentTableName);

        // 3. 从表中获取数据，然后构建响应
        return ExcelPreviewResponse.builder()
                .excelInfo(getExcelInfo(fileId))
                .sheets(tableNames.size() > 1 ? buildSheetInfoList(tableNames) : null)
                .currentSheetIndex(sheetIndex)
                .headers(getColumnHeaders(currentTableName))
                .dataRows(getPageData(currentTableName, page, pageSize))
                .paginationInfo(getPaginationInfo(page, pageSize, totalRecords))
                .build();
    }


    private ExcelPreviewResponse.PaginationInfo getPaginationInfo(Integer page, Integer pageSize, Long totalRecords) {
        long totalPages = (long) Math.ceil((double) totalRecords / pageSize);

        return ExcelPreviewResponse.PaginationInfo.builder()
                .currentPage(page)
                .pageSize(pageSize)
                .totalPages(totalPages)
                .totalRecords(totalRecords)
                .hasNext(page <totalPages)
                .hasPrevious(page >1)
                .build();
    }


    private List<Map<String, Object>> getPageData(String tableName, Integer page, Integer pageSize) {
        int offset = (page - 1) * pageSize;
        String sql = "SELECT * FROM " + tableName + " ORDER BY id LIMIT ? OFFSET ?";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, pageSize, offset);
        return rows.stream()
                .map(row -> {
                    Map<String, Object> newRow = new HashMap<>(row);
                    newRow.remove("id");
                    return newRow;
                })
                .collect(Collectors.toList());
    }


    private List<ExcelPreviewResponse.ColumnHeader> getColumnHeaders(String tableName) {
        Map<String, String> fieldMappings = fieldMappingService.getMappingMapByTableName(tableName);
        List<ExcelPreviewResponse.ColumnHeader> headers = new ArrayList<>();
        for (String key :fieldMappings.keySet()) {
            String dbFieldName = key;
            String originalHeader = fieldMappings.getOrDefault(dbFieldName, dbFieldName);
            ExcelPreviewResponse.ColumnHeader header = ExcelPreviewResponse.ColumnHeader.builder()
                    .dbFieldName(dbFieldName)
                    .originalHeader(originalHeader)
                    .build();
            headers.add(header);
        }
        return headers;
    }


    private List<ExcelPreviewResponse.SheetInfo> buildSheetInfoList(List<String> tableNames) {
        List<ExcelPreviewResponse.SheetInfo> sheetInfos = new ArrayList<>();
        for (int i =0; i <tableNames.size(); i++) {
            String tableName = tableNames.get(i);
            Long totalRows = getTotalRecords(tableName);
            Long totalColumns = getTotalColumns(tableName);
            String sheetName = "sheet_"+i;

            ExcelPreviewResponse.SheetInfo sheetInfo = ExcelPreviewResponse.SheetInfo.builder()
                    .sheetIndex(i)
                    .sheetName(sheetName)
                    .tableName(tableName)
                    .totalColumns(totalColumns)
                    .totalRows(totalRows)
                    .build();
            sheetInfos.add(sheetInfo);
        }
        return sheetInfos;
    }


    public ExcelPreviewResponse.ExcelInfo getExcelInfo(Long fileId) {
        FilesEntity filesEntity = filesMapper.selectById(fileId);

        List<String> tableNames = fileTableMappingService.getTableNamesByFileId(fileId);
        String fistTableName = tableNames.get(0);
        Long totalRows = getTotalRecords(fistTableName);
        Long totalColumns = getTotalColumns(fistTableName);

        return ExcelPreviewResponse.ExcelInfo
                .builder()
                .fileId(fileId)
                .fileName(filesEntity.getFileName())
                .fileSize(filesEntity.getFileSize())
                .totalRows(totalRows)
                .totalColumns(totalColumns)
                .build();
    }

    @Override
    public boolean restoreFileData(Long fileId, Long userId) {
        // 1. 查询文件信息
        FilesEntity filesEntity = filesMapper.selectByUserIdAndFileId(userId, fileId);
        if (filesEntity == null) {
            throw new IllegalArgumentException("文件不存在或者用户无权限");
        }
        // 2. 选择需要复原的mysql表
        List<String> tableNameList = fileTableMappingService.getTableNamesByFileId(fileId);

        // 3. 获取原始excel文件
        MultipartFile file = downloadFileFromOss(filesEntity.getOssKey());
        if (file == null) {
            throw new RuntimeException("无法获取有效的文件");
        }

        for (int i = 0; i <tableNameList.size(); i++) {
            String tableName = tableNameList.get(i);
            // 4. 清空mysql表
            String sql = "TRUNCATE TABLE `" + tableName + "`";
            jdbcTemplate.update(sql);

            // 5. 插入数据
            excelToTableService.insertData(tableName, file, i);
        }



        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteFiles(FileDeleteRequest fileDeleteRequest, Long userId) {
        // 1. 遍历处理文件ID
        for (Long fileId : fileDeleteRequest.getFileIds()) {
            // 2. 判断权限
            FilesEntity filesEntity = filesMapper.selectByUserIdAndFileId(userId, fileId);
            if (filesEntity == null) {
                log.error("文件不存在或者无权限删除 {} {}", fileId, userId);
                continue;  // 继续循环
            }
            filesMapper.deleteById(fileId);

            // 3. 删除衍生出来的表
            List<String> tableNames = fileTableMappingService.getTableNamesByFileId(fileId);
            for (String tableName :tableNames) {
                String sql = "DROP TABLE IF EXISTS `" + tableName + "`";
                jdbcTemplate.execute(sql);
                log.info("mysql表删除成功 {}", tableName);
            }

            // 4. 删除file_table_mappings的记录
            fileTableMappingService.deleteByFileId(fileId);

            // 5. 删除field_mappings的记录
            fieldMappingService.deleteByFileId(fileId);

            // 6 删除oss记录
            ossService.deleteFile(filesEntity.getOssKey());
        }

        return true;
    }


    private MultipartFile downloadFileFromOss(String ossKey) {
        try {

            // 从OSS获取文件流
            InputStream inputStream = ossService.getObject(ossKey).getObjectContent();
            if (inputStream == null) {
                log.error("无法从OSS获取文件流，OSS Key：{}", ossKey);
                return null;
            }

            // 读取文件内容到字节数组
            byte[] fileBytes = inputStream.readAllBytes();
            inputStream.close();

            // 从OSS Key中提取文件名
            String fileName = ossKey.substring(ossKey.lastIndexOf('/') + 1);

            // 创建MultipartFile实现
            return new MultipartFile() {
                @Override
                public @org.springframework.lang.NonNull String getName() {
                    return "file";
                }

                @Override
                public String getOriginalFilename() {
                    return fileName;
                }

                @Override
                public String getContentType() {
                    return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                }

                @Override
                public boolean isEmpty() {
                    return fileBytes.length == 0;
                }

                @Override
                public long getSize() {
                    return fileBytes.length;
                }

                @Override
                public @org.springframework.lang.NonNull byte[] getBytes() {
                    return fileBytes;
                }

                @Override
                public @org.springframework.lang.NonNull java.io.InputStream getInputStream() {
                    return new ByteArrayInputStream(fileBytes);
                }

                @Override
                public void transferTo(@org.springframework.lang.NonNull java.io.File dest) throws java.io.IOException {
                    Files.write(dest.toPath(), fileBytes);
                }
            };

        } catch (Exception e) {
            log.error("从OSS下载文件失败，OSS Key：{}，错误：{}", ossKey, e.getMessage(), e);
            return null;
        }
    }


    private Long getTotalColumns(String tableName) {
        String sql = "describe " + tableName;
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(sql);
        return (long) (columns.size() - 1);
    }

    private Long getTotalRecords(String tableName) {
        String sql = "select count(1) from " + tableName;
        return jdbcTemplate.queryForObject(sql, Long.class);
    }

    // 转换对象的函数
    private FileInfoResponse convert(FilesEntity filesEntity) {
        String fileName = filesEntity.getFileName();

        return FileInfoResponse.builder()
                .fileId(filesEntity.getId())
                .userId(filesEntity.getUserId())
                .fileName(filesEntity.getFileName())
                .filePath(filesEntity.getFilePath())
                .fileSize(filesEntity.getFileSize())
                .fileUrl(filesEntity.getOssKey())
                .ossKey(filesEntity.getOssKey())
                .uploadStatus(filesEntity.getUploadStatus())
                .fileExtension(fileName.substring(fileName.lastIndexOf(".")))
                .fileType(FileValidationUtil.getContentType(fileName))
                .build();
    }



    // 根据文件地址获取key
    private String getOssKey(String fileUrl) {
        String[] parts = fileUrl.split("/");
        if (parts.length >= 4) {
            StringBuilder key = new StringBuilder();
            for (int i = 3; i <parts.length; i++) {
                if (i >3) {
                    key.append("/");
                }
                key.append(parts[i]);
            }
            return key.toString();
        }
        return "";
    }

}
