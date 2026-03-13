package com.docai.file.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelReader;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.exception.ExcelAnalysisStopException;
import com.alibaba.excel.read.metadata.ReadSheet;
import com.docai.common.service.DistributeLockService;
import com.docai.common.util.PinyinUtil;
import com.docai.file.service.ExcelToTableService;
import com.docai.file.service.FieldMappingService;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Consumer;

/**
 * excel转换mysql表的实现类
 */
@Slf4j
@Service
public class ExcelToTableServiceImpl implements ExcelToTableService {

    @Autowired
    private DistributeLockService distributeLockService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FieldMappingService fieldMappingService;

    private static final Integer EXCEL_BATCH_SIZE = 1000;
    private static final int SQL_BATCH_SIZE = 1000;


    @Override
    public List<String> convertExcelToTable(MultipartFile file, Long fileId) {
        // 1. 先获取excel的数据
        int sheetCount = getSheetCount(file);

        if (sheetCount == 0) {
            throw new IllegalArgumentException("excel是个空文件");
        }

        // 2. 获取sheet的名称
        List<String> sheetNames = getSheetNames(file);


        // 3. 根据文件信息做实际table的转换

        return convertExcelToTable(file, fileId, sheetNames);
    }

    private List<String> convertExcelToTable(MultipartFile file, Long fileId, List<String> sheetNames) {

        // 1. 先生成表名
        String baseTableName = createTableName();
        List<String> tableNames = new ArrayList<>();

        // 2. 创建表
        for (int i =0; i < sheetNames.size(); i++) {
            String tableName = baseTableName + "_sheet" + i;
            createTableFromExcel(tableName, file, fileId, i);
            // 3. 获取file的数据

            // 4. 把file的数据转换成插入的sql

            // 5. 执行sql
            insertData(tableName, file, i);
            tableNames.add(tableName);
        }

        return tableNames;
    }


    public void insertData(String tableName, MultipartFile file, int sheetIndex) {

        // 1. 实现2种读取数据的方式 以文件的大小作为判断的标准
        long fileSize = 5 * 1024 * 1024;
        try {
            List<String> originalHeaders = readHeader(file, sheetIndex);
            List<Map<String, Object>> tableStructure = getTableStructure(tableName);
            List<String> columns = new ArrayList<>();

            for (Map<String, Object> column : tableStructure) {
                String columnName = (String) column.get("FieLd");
                if (!columnName.equals("id")) {
                    columns.add(columnName);
                }
            }

            Map<String, String> headerMapping = new HashMap<>();
            for (int i = 0; i< originalHeaders.size(); i++) {
                String originalHeader = originalHeaders.get(i);
                String safeColum = columns.get(i);
                headerMapping.put(originalHeader, safeColum);
            }


            // 2. 大于5MB，使用流式处理
            if (file.getSize() > fileSize) {
                // 初始化一个计数器
                final int[] streamInserted = {0};
                final Map<String, String> finalHeaderMapping = headerMapping;
                final List<String> finalSafeColumnNames = columns;
                final int finalSheetIndex = sheetIndex;

                // 流式地获取数据，逐个加入sql语句
                processExcelInBatches(file, finalSheetIndex, batchData -> {

                    List<Object[]> batchArgs = new ArrayList<>();

                    for (Map<String, Object> row : batchData) {
                        Object[] args = new Object[finalSafeColumnNames.size()];
                        for (int i = 0; i <finalSafeColumnNames.size(); i++) {
                            String safeColumnName = finalSafeColumnNames.get(i);

                            String originalHeader = null;
                            for (Map.Entry<String, String> entry : finalHeaderMapping.entrySet()) {
                                if (entry.getValue().equals(safeColumnName)) {
                                    originalHeader = entry.getKey();
                                    break;
                                }
                            }

                            // 根据原始列名从row中获取对应的数据值
                            if (originalHeader != null) {
                                args[i] = row.get(originalHeader);
                            } else {
                                args[i] = null;
                            }

                        }
                        batchArgs.add(args);
                    }
                    // 每次将当前批次的数据拼成多值 INSERT，单条 SQL 最多 500 行
                    executeBatchInsert(tableName, finalSafeColumnNames, batchArgs);
                    streamInserted[0] += batchArgs.size();
                });


            } else {
                // 3. 否则一次性读取数据
                byte[] fileBytes = getFileBytes(file);
                List<Object[]> allArgs = new ArrayList<>();
                try(Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(fileBytes)))  {
                    Sheet sheet = workbook.getSheetAt(sheetIndex);
                    int totalRows = sheet.getLastRowNum();
                    for (int i = 1; i<= totalRows; i++) {
                        Row row = sheet.getRow(i);
                        if (row == null) {
                            continue;
                        }
                        Map<String, Object> rowData = new LinkedHashMap<>();
                        for (int j = 0; j <originalHeaders.size(); j++) {
                            Cell cell = row.getCell(j);
                            rowData.put(originalHeaders.get(j), getCellValue(cell));
                        }

                        Object[] args = new Object[columns.size()];
                        for (int k = 0; k < columns.size(); k++) {
                            String safeColumName = columns.get(k);
                            String originHeader = null;

                            for (Map.Entry<String, String> entry : headerMapping.entrySet()) {
                                if (entry.getValue().equals(safeColumName)) {
                                    originHeader = entry.getKey();
                                    break;
                                }
                            }
                            if (originHeader != null) {
                                args[k] = rowData.get(originHeader);
                            } else {
                                args[k] = null;
                            }
                        }
                        allArgs.add(args);
                    }
                }
                executeBatchInsert(tableName, columns, allArgs);


            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void executeBatchInsert(String tableName, List<String> columns, List<Object[]> args) {
        if (args == null || args.isEmpty()) {
            return;
        }

        int columnCount = columns.size();

        for (int start = 0; start < args.size(); start += SQL_BATCH_SIZE) {
            int end = Math.min(start + SQL_BATCH_SIZE, args.size());

            StringBuilder sqlBuilder = new StringBuilder();
            sqlBuilder.append("insert into `").append(tableName).append("` (");
            for (String name : columns) {
                sqlBuilder.append("`").append(name).append("`,");
            }
            sqlBuilder.setLength(sqlBuilder.length() - 1);
            sqlBuilder.append(") values ");

            List<Object> paramList = new ArrayList<>();

            for (int i = start; i < end; i++) {
                Object[] rowValues = args.get(i);
                sqlBuilder.append("(");
                for (int j = 0; j < columnCount; j++) {
                    sqlBuilder.append("?,");
                    paramList.add(rowValues[j]);
                }
                sqlBuilder.setLength(sqlBuilder.length() - 1);
                sqlBuilder.append("),");
            }

            sqlBuilder.setLength(sqlBuilder.length() - 1); // 去掉最后一个逗号

            jdbcTemplate.update(sqlBuilder.toString(), paramList.toArray());
        }
    }

    // 提取excel单元格值
    private String getCellValue(Cell cell) {
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    Date date = cell.getDateCellValue();
                    SimpleDateFormat sdf = new SimpleDateFormat("YY-MM-dd HH:mm:ss");
                    yield sdf.format(date);
                } else {
                    double number = cell.getNumericCellValue();
                    yield String.valueOf(number);
                }
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> String.valueOf(cell.getCellFormula());
            default -> "";
        };
    }


    private static class EasyExcelBatchReadListener extends AnalysisEventListener<Map<Integer, String>> {
        private final Consumer<List<Map<String, Object>>> batchProcessor;
        private final int batchSize;

        private final List<String> headers = new ArrayList<>();
        private final List<Map<String, Object>> currentBatch = new ArrayList<>();

        private EasyExcelBatchReadListener(Consumer<List<Map<String, Object>>> batchProcessor, int batchSize) {
            this.batchProcessor = batchProcessor;
            this.batchSize = batchSize;
        }

        @Override
        public void invoke(Map<Integer, String> integerStringMap, AnalysisContext analysisContext) {
            if (headers.isEmpty()) {
                int maxCol = integerStringMap.keySet().stream().max(Integer::compareTo).orElse(-1);
                for (int i = 0; i <= maxCol; i++) {
                    headers.add(integerStringMap.getOrDefault(i, ""));
                }
                return;
            }

            Map<String, Object> rowData = new LinkedHashMap<>();
            for (int i =0; i <headers.size(); i++) {
                String header = headers.get(i);
                String value = integerStringMap.getOrDefault(i, "");
                rowData.put(header, value);
            }
            currentBatch.add(rowData);

            if (currentBatch.size() >= batchSize) {
                batchProcessor.accept(new ArrayList<>(currentBatch));
                currentBatch.clear();
            }
        }

        @Override
        public void doAfterAllAnalysed(AnalysisContext analysisContext) {

        }
    }


    private void processExcelInBatches(MultipartFile file,
                                       int sheetIndex, Consumer<List<Map<String, Object>>> batchProcessor) {
        // 1. 直接把文件转换成字节数组
        byte[] fileBytes = getFileBytes(file);

        // 2. 使用字节数组输入流创建EasyExcel读取器
        // 3. 保存数据
        try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(fileBytes)) {

            EasyExcel.read(byteArrayInputStream, new EasyExcelBatchReadListener(batchProcessor, EXCEL_BATCH_SIZE))
                    .sheet(sheetIndex)
                    .headRowNumber(0)
                    .doRead();
        } catch (Exception e) {
            throw new RuntimeException("处理大文件失败:"+e.getMessage());
        }

    }



    // 获取表结构
    private List<Map<String, Object>> getTableStructure(String tableName) {
        String sql = "describe `" +tableName + "`";
        return jdbcTemplate.queryForList(sql);
    }


    /**
     * 创建表
     * @param tableName 表名
     * @param file 文件
     * @param fileId 文件ID
     * @param sheetIndex sheet所在的索引位置
     */
    private void createTableFromExcel(String tableName, MultipartFile file, Long fileId, int sheetIndex) {
        // 1. 读取excel的表头
        try {
            List<String> headers = readHeader(file, sheetIndex);
            if (headers.isEmpty()) {
                throw new IllegalArgumentException("sheet没有表头");
            }
            // 2. 根据excel的表头生成table的字段
            List<String> safeHeaders = new ArrayList<>();
            for (String header : headers) {
                String safeHeader = createColumnName(header);
                safeHeaders.add(safeHeader);
            }
            System.out.println("字段长度" + safeHeaders.size());
            // 3. 生成sql语句  create table
            StringBuilder sql = new StringBuilder();
            sql.append("create table `").append(tableName).append("` (");
            sql.append(" `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',");
            for (int i =0; i < safeHeaders.size(); i++) {
                String safeHeader = safeHeaders.get(i);
                String header = headers.get(i);
                sql.append("`").append(safeHeader).append("` TEXT COMMENT '").append(header).append("',");
            }
            sql.setLength(sql.length() - 1);
            sql.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Excel导入表'");

            log.info("生成的sql{}", sql);
            jdbcTemplate.execute(sql.toString());

            fieldMappingService.saveMappings(fileId, tableName, headers, safeHeaders);



            // 4. 执行sql语句
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


    }


    private String createColumnName(String headerName) {
        // 1. 删掉首尾空格
        String cleanName = headerName.trim();

        // 2. 检查是否包含中文
        if (!cleanName.matches(".*[\\u4e00-\\u9fa5].*")) {
            cleanName = cleanName.replaceAll("[^a-zA-Z0-9]", "-");
            if (!cleanName.matches("[^a-zA-Z].*")) {
                cleanName = "col_" +cleanName;
            }
        } else {
            cleanName = PinyinUtil.chineseToPinYin(cleanName);
            cleanName = "col_" + cleanName;
        }

        if (cleanName.length() > 64) {
            cleanName = cleanName.substring(0, 64);
            cleanName = cleanName.replaceAll("_+$", "");
        }

        if (!cleanName.matches("^[a-zA-Z][a-zA-Z0-9]*$")) {
            cleanName = "filed_" + cleanName;
        }
        return cleanName.trim();
    }


    private List<String> readHeader(MultipartFile file, int sheetIndex) throws IOException {
        byte[] fileBytes = getFileBytes(file);
        List<String> headers = new ArrayList<>();

        try (ByteArrayInputStream bais = new ByteArrayInputStream(fileBytes)) {
            AnalysisEventListener<Map<Integer, String>> headerListener = new AnalysisEventListener<>() {
                @Override
                public void invoke(Map<Integer, String> data, AnalysisContext context) {
                    int maxCol = data.keySet().stream().max(Integer::compareTo).orElse(-1);
                    for (int i = 0; i <= maxCol; i++) {
                        headers.add(data.getOrDefault(i, ""));
                    }
                    // 读取到表头后立即停止解析
                    throw new ExcelAnalysisStopException("Header row read completed");
                }

                @Override
                public void doAfterAllAnalysed(AnalysisContext context) {
                    // no-op
                }
            };

            try {
                EasyExcel.read(bais, headerListener)
                        .sheet(sheetIndex)
                        .headRowNumber(0) // 第一行就是表头
                        .doRead();
            } catch (ExcelAnalysisStopException ignore) {
                // 正常用于提前结束读取
            }
        }

        if (headers.isEmpty()) {
            throw new IllegalArgumentException("指定 Sheet(索引 " + sheetIndex + ") 没有表头或无法读取表头");
        }

        return headers;
    }

    private String createTableName() {
        // 1. 设置一个重试次数
        int maxRetries = 5;
        int retryCount = 1;

        // 2. 设置lockValue
        String lockValue = UUID.randomUUID().toString();
        // 3. 获取了锁之后再去生成表名， 否则就重复
        while (retryCount <= maxRetries) {
            String baseName = "excel_table";
            String tableName = baseName + String.valueOf(System.currentTimeMillis()).substring(8);

            String lockKey = tableName;
            boolean lockAcquired = distributeLockService.tryLock(lockKey, lockValue, 60);
            if (lockAcquired) {
                // 获取分布式锁成功，对表名做个唯一判断
                if (tableExists(tableName)) {
                    distributeLockService.releaseLock(lockKey, lockValue);
                } else {
                    return tableName;
                }
            }
            retryCount++;

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
        throw new RuntimeException("生成唯一表名失败");
    }

    private boolean tableExists(String tableName) {
        String sql = "select count(1) from information_schema.tables where table_schema = DATABASE() and table_name = ?";
        return jdbcTemplate.queryForObject(sql, Integer.class, tableName) > 0;
    }


    private int getSheetCount(MultipartFile file) {
        byte[] fileBytes = getFileBytes(file);

        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(fileBytes);
        ExcelReader excelReader = EasyExcel.read(byteArrayInputStream).build();
        List<ReadSheet> sheets = excelReader.excelExecutor().sheetList();
        return sheets.size();

    }

    public List<String> getSheetNames(MultipartFile file) {
        byte[] fileBytes = getFileBytes(file);

        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(fileBytes);
        ExcelReader excelReader = EasyExcel.read(byteArrayInputStream).build();
        List<ReadSheet> sheets = excelReader.excelExecutor().sheetList();
        List<String> names = new ArrayList<>();
        for (ReadSheet readSheet :sheets) {
            names.add(readSheet.getSheetName());
        }
        return names;
    }


    private byte[] getFileBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            return null;
        }
    }
}
