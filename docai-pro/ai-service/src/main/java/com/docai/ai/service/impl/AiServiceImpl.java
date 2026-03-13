package com.docai.ai.service.impl;

import com.alibaba.cloud.commons.lang.StringUtils;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.docai.ai.config.AiRequestStatus;
import com.docai.ai.config.ProcessStage;
import com.docai.ai.dto.request.AiChatRequest;
import com.docai.ai.dto.request.AiRequestHistoryRequest;
import com.docai.ai.dto.request.SendEmailRequest;
import com.docai.ai.dto.response.*;
import com.docai.ai.entity.AiRequestEntity;
import com.docai.ai.mapper.AiRequestMapper;
import com.docai.ai.service.AiModelService;
import com.docai.ai.service.AiService;
import com.docai.ai.service.FileMetaDataService;
import com.docai.ai.service.LlmService;
import com.docai.ai.service.SQLGenerationService;
import com.docai.common.service.OssService;
import com.docai.file.entity.FilesEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI服务实现类
 */
@Service
@Slf4j
public class AiServiceImpl implements AiService {

    @Autowired
    private AiRequestMapper aiRequestMapper;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static  final String PROGRESS = "progress";

    @Autowired
    private FileMetaDataService fileMetaDataService;

    @Autowired
    private SQLGenerationService sqlGenerationService;

    @Autowired
    private AiModelService aiModelService;

    @Autowired
    private LlmService llmService;

    @Autowired
    private OssService ossService;

    @Autowired
    @Qualifier("toolCallingChatClient")
    private ChatClient toolCallingChatClient;


    @Override
    public void streamUnifiedChat(AiChatRequest aiChatRequest, Long userId, SseEmitter sseEmitter) {
        // 1. 通知前端，可以开始处理请求了
        AiRequestEntity aiRequestEntity = null;
        sendProgressEventWithData(sseEmitter, ProcessStage.INIT.getCode(), ProcessStage.INIT,
                "开始处理AI请求...", null, null, null, null);
        aiRequestEntity = initStreamRequest(aiChatRequest, userId);

        // 无文档时走在线大模型日常对话，避免强制绑定文档。
        if (aiChatRequest.getFileId() == null || aiChatRequest.getFileId() <= 0) {
            AiUnifiedResponse generalResponse = handleGeneralChatFlow(aiChatRequest, aiRequestEntity, sseEmitter);
            sendCompleteEvent(sseEmitter, generalResponse);
            sseEmitter.complete();
            return;
        }

        // 2. 校验文件的权限
        List<String> tableNames = validateFileAndSendProgress(aiChatRequest, userId, sseEmitter);

        // 3. 查询表信息（表结构）
        loadTableStructureAndSendProgress(tableNames, sseEmitter);

        // 4. 判断用户的输入 （查、改、图表）
        TypeResult result = judgeUserInputAndSendProgress(aiChatRequest, sseEmitter);

        // 5. 分叉处理
        AiUnifiedResponse response = null;
        // 5.1 先处理生成图表的逻辑
        if (result.isNeedChart()) {
            response = handleChartFlow(aiChatRequest, aiRequestEntity, sseEmitter);
        } else if (result.isModificationRequest()) {
            // 5.2 执行修改excel的操作
            response = handleModificationChatFlow(aiChatRequest, aiRequestEntity, sseEmitter);
        } else if (!result.continueNext) {
            // 5.3 输入内容与数据处理无关
            response = AiUnifiedResponse.builder()
                    .requestId(aiRequestEntity.getId())
                    .aiResponse("输入内容与数据处理无瓜，请重新输入")
                    .sqlQuery(null)
                    .resultData(Collections.emptyList())
                    .resultCount(0)
                    .status(AiRequestStatus.FAILED.getCode())
                    .needChart(false)
                    .isModificationRequest(false)
                    .modifiedExcelUrl(null)
                    .build();
        } else {
            // 5.4 普通对话，查询
            response = handleChatFlow(aiChatRequest, aiRequestEntity, sseEmitter);
        }

        // 6. 事件处理，汇总
        sendCompleteEvent(sseEmitter, response);
        sseEmitter.complete();
    }

        private AiUnifiedResponse handleGeneralChatFlow(
            AiChatRequest aiChatRequest,
            AiRequestEntity aiRequestEntity,
            SseEmitter sseEmitter
        ) {
        sendProgressEventWithData(sseEmitter, PROGRESS, ProcessStage.PROCESS_CHAT,
            "未关联文档，执行通用在线对话", null, null, null, null);

        try {
            String prompt = String.format(
                "你是DocAI智能助手。请根据用户输入给出专业、简洁、可执行的中文回答。用户输入：%s",
                aiChatRequest.getUserInput()
            );
            String reply = llmService.generateText(prompt);

            aiRequestEntity.setAiResponse(reply);
            aiRequestEntity.setStatus(AiRequestStatus.SUCCESS.getCode());
            aiRequestMapper.updateById(aiRequestEntity);

            return AiUnifiedResponse.builder()
                .requestId(aiRequestEntity.getId())
                .aiResponse(reply)
                .sqlQuery(null)
                .resultData(Collections.emptyList())
                .resultCount(0)
                .status(AiRequestStatus.SUCCESS.getCode())
                .needChart(false)
                .isModificationRequest(false)
                .modifiedExcelUrl(null)
                .build();
        } catch (Exception ex) {
            log.error("通用对话失败: {}", ex.getMessage(), ex);
            aiRequestEntity.setAiResponse("抱歉，当前在线模型服务暂时不可用，请稍后重试。");
            aiRequestEntity.setStatus(AiRequestStatus.FAILED.getCode());
            aiRequestMapper.updateById(aiRequestEntity);

            return AiUnifiedResponse.builder()
                .requestId(aiRequestEntity.getId())
                .aiResponse("抱歉，当前在线模型服务暂时不可用，请稍后重试。")
                .sqlQuery(null)
                .resultData(Collections.emptyList())
                .resultCount(0)
                .status(AiRequestStatus.FAILED.getCode())
                .needChart(false)
                .isModificationRequest(false)
                .modifiedExcelUrl(null)
                .errorMessage(ex.getMessage())
                .build();
        }
        }

    @Override
    public IPage<AiRequestHistoryResponse> getRequestHistory(AiRequestHistoryRequest aiRequestHistoryRequest, Long userId) {
        // 1. 构建查询条件
        LambdaQueryWrapper<AiRequestEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(AiRequestEntity::getUserId, userId);

        if (aiRequestHistoryRequest.getFileId() != null) {
            queryWrapper.eq(AiRequestEntity::getFileId, aiRequestHistoryRequest.getFileId());
        }

        queryWrapper.orderByDesc(AiRequestEntity::getId);

        // 2. 分页查询结果
        long current = aiRequestHistoryRequest.getPageNum();
        long size = aiRequestHistoryRequest.getPageSize();

        Page<AiRequestEntity> page = new Page<>(current, size);

        IPage<AiRequestEntity> entityIPage = aiRequestMapper.selectPage(page, queryWrapper);

        // 3. 对象转换
        List<AiRequestHistoryResponse> responseList = entityIPage.getRecords().stream()
                .map(this::convert)
                .collect(Collectors.toList());

        Page<AiRequestHistoryResponse> responsePage = new Page<>(current, size);
        responsePage.setRecords(responseList);
        responsePage.setTotal(entityIPage.getTotal());
        responsePage.setPages(entityIPage.getPages());

        return responsePage;
    }

    @Override
    public Boolean sendEmailWithExcel(SendEmailRequest sendEmailRequest) {
        // 1. 构建邮件内容  （主题+附件+副标题）
        String[] data = sendEmailRequest.getExcelUrl().split("/");
        String fileName = data[data.length - 1];
        String emailContent = "<html><body>" +
                "<h3>您好！</h3>" +
                "<p>您请求的修改后的excel文件已经生成，请查收</p>" +
                "<p><strong>文件名：</strong>" + fileName + "</p>"+
                "</body></html>";
        String subject = "修改后的Excel文件" + fileName;
        // 2. 封装发送邮件的工具

        // 3. 封装提示词
        StringBuilder prompt = new StringBuilder();
        prompt.append("请使用sendEmail工具来发送一封邮件\n");
        prompt.append("参数信息如下：\n");
        prompt.append("-email（收件人邮箱）:").append(sendEmailRequest.getEmail()).append("\n");
        prompt.append("-subject（邮件主题）:").append(subject).append("\n");
        prompt.append("-content（邮件正文）:").append(emailContent).append("\n");
        prompt.append("-attachmentUrl（附件URL）:").append(sendEmailRequest.getExcelUrl()).append("\n");
        prompt.append("\n请立即调用sendEmail工具");
        // 4. 调用LLM，触发tool calling来发送邮件
        String response = toolCallingChatClient.prompt()
                .user(prompt.toString())
                .call()
                .content();

        // 5. 构造响应
        return response.contains("成功");
    }


    // 实体类转换为历史响应的DTO
    private AiRequestHistoryResponse convert(AiRequestEntity aiRequestEntity) {
        return AiRequestHistoryResponse.builder()
                .id(aiRequestEntity.getId())
                .fileId(aiRequestEntity.getFileId())
                .userInput(aiRequestEntity.getUserInput())
                .aiResponse(aiRequestEntity.getAiResponse())
                .status(aiRequestEntity.getStatus())
                .build();
    }

    // 用来执行查询excel的操作
    private AiUnifiedResponse handleChatFlow(
            AiChatRequest aiChatRequest,
            AiRequestEntity aiRequestEntity,
            SseEmitter sseEmitter
    ) {
        // 1. 发送开始处理事件
        sendProgressEventWithData(sseEmitter, PROGRESS, ProcessStage.PROCESS_CHAT,
                null, null, null, null, null);
        // 2. 执行修改流程
        AiChatResponse aiChatResponse = executeChatFlow(aiChatRequest, aiRequestEntity, sseEmitter);

        // 3. 封装响应
        return processChatResponse(
                aiChatRequest,
                aiRequestEntity,
                new StringBuilder(),
                sseEmitter,
                aiChatResponse,
                aiChatResponse.getResultData(),
                aiChatResponse.getSqlQuery()
        );
    }


    // 用来执行修改excel的操作
    private AiUnifiedResponse handleModificationChatFlow(
            AiChatRequest aiChatRequest,
            AiRequestEntity aiRequestEntity,
            SseEmitter sseEmitter
    ) {
        // 1. 发送开始处理事件
        sendProgressEventWithData(sseEmitter, PROGRESS, ProcessStage.PROCESS_CHAT,
                null, null, null, null, null);
        // 2. 执行修改流程
        AiChatResponse aiChatResponse = executeModificationFlow(aiChatRequest, aiRequestEntity, sseEmitter);

        // 3. 封装响应
        return processChatResponse(
                aiChatRequest,
                aiRequestEntity,
                new StringBuilder(),
                sseEmitter,
                aiChatResponse,
                aiChatResponse.getResultData(),
                aiChatResponse.getSqlQuery()
        );
    }


    // 对话场景的封装响应
    private AiUnifiedResponse processChatResponse(
            AiChatRequest aiChatRequest,
            AiRequestEntity aiRequestEntity,
            StringBuilder aiResponse,
            SseEmitter sseEmitter,
            AiChatResponse aiChatResponse,
            List<Map<String, Object>> resultData,
            String sqlQuery
    ) {

        // 1. 生成AI响应
        String aiResponseResult;
        try {
            aiResponseResult = aiModelService.generateAiResponse(aiChatRequest.getUserInput(), resultData);
        } catch (Exception ex) {
            log.warn("生成对话说明失败，回退结果摘要: {}", ex.getMessage());
            aiResponseResult = resultData == null || resultData.isEmpty()
                    ? "已完成处理，但当前没有可返回的数据。"
                    : "处理完成，以下是结果数据摘要。";
        }

        aiResponse.append(aiResponseResult);
        // 2. 发送AI响应事件
        sendProgressEventWithData(
                sseEmitter,
                PROGRESS,
                ProcessStage.AI_RESPONSE,
                null,
                sqlQuery,
                resultData,
                resultData.size(),
                aiResponse.toString()
        );

        // 3. 更新AI请求记录
        aiRequestEntity.setAiResponse(aiChatResponse.toString());
        aiRequestEntity.setStatus(AiRequestStatus.SUCCESS.getCode());
        aiRequestMapper.updateById(aiRequestEntity);

        // 4. 返回响应对象
        return AiUnifiedResponse.builder()
                .requestId(aiRequestEntity.getId())
                .aiResponse(aiResponse.toString())
                .sqlQuery(sqlQuery)
                .resultData(resultData)
                .resultCount(resultData.size())
                .status(AiRequestStatus.SUCCESS.getCode())
                .needChart(false)
                .isModificationRequest(aiChatResponse.getIsModificationRequest())
                .modifiedExcelUrl(aiChatResponse.getModifiedExcelUrl())
                .build();
    }

    // 处理修改语句的逻辑
    private AiChatResponse executeChatFlow(
            AiChatRequest aiChatRequest,
            AiRequestEntity aiRequestEntity,
            SseEmitter sseEmitter
    ) {
        // 1. 获取当前关联文件的所有表名
        List<String> tableNames = getTableNamesByFileId(aiChatRequest.getFileId());

        // 2. 根据用户输入，依靠大模型判断，应该操作哪一张表
        String tableName = determineTargetTable(aiChatRequest.getUserInput(), tableNames, aiChatRequest.getFileId());

        if (tableName == null || StringUtils.isBlank(tableName)) {
            throw new IllegalArgumentException("用户输入无效，无法获取到表名");
        }

        // 3. 结合表结构和用户输入的命令生成最终的修改sql
        String sql = generateSql(tableName, aiChatRequest.getUserInput());

        sendProgressEventWithData(sseEmitter, PROGRESS, ProcessStage.QUERY_SQL,
                "生成查询sql", sql, null, null, null);
        // 4. 执行查询
        List<Map<String, Object>> result = sqlGenerationService.excuteQuery(sql, tableName);
        sendProgressEventWithData(sseEmitter, PROGRESS, ProcessStage.EXECUTE_QUERY_SQL,
                "执行查询sql", sql, result, result.size(), null);
        // 5. 构建统一格式的响应
        return AiChatResponse.builder()
                .requestId(aiRequestEntity.getId())
                .aiResponse("")
                .sqlQuery(sql)
                .resultData(result)
                .resultCount(result.size())
                .status(AiRequestStatus.SUCCESS.getCode())
                .isModificationRequest(true)
                .modifiedExcelUrl(null)
                .build();
    }

    // 处理修改语句的逻辑
    private AiChatResponse executeModificationFlow(
            AiChatRequest aiChatRequest,
            AiRequestEntity aiRequestEntity,
            SseEmitter sseEmitter
    ) {
        // 1. 获取当前关联文件的所有表名
        List<String> tableNames = getTableNamesByFileId(aiChatRequest.getFileId());

        // 2. 根据用户输入，依靠大模型判断，应该操作哪一张表
        String tableName = determineTargetTable(aiChatRequest.getUserInput(), tableNames, aiChatRequest.getFileId());

        if (tableName == null || StringUtils.isBlank(tableName)) {
            throw new IllegalArgumentException("用户输入无效，无法获取到表名");
        }

        // 3. 结合表结构和用户输入的命令生成最终的修改sql
        String sql = generateUpdateSql(tableName, aiChatRequest.getUserInput());

        sendProgressEventWithData(sseEmitter, PROGRESS, ProcessStage.UPDATE_SQL,
                "生成修改sql", sql, null, null, null);
        // 4. 执行修改语句
        int count = sqlGenerationService.excuteUpdate(sql);
        sendProgressEventWithData(sseEmitter, PROGRESS, ProcessStage.EXECUTE_UPDATE_SQL,
                "执行修改sql", sql, null, count, null);
        // 5. 把已经修改后的数据再查一遍，上传到oss
        List<Map<String, Object>> result = sqlGenerationService.excuteQuery("select * from " + tableName, tableName);
        sendProgressEventWithData(sseEmitter, PROGRESS, ProcessStage.QUERY_UPDATE_DATA,
                "修改完成，再查数据", sql, null, count, null);
        String excelDownLoadUrl = generateModifiedExcel(result, aiChatRequest.getFileId());
        sendProgressEventWithData(sseEmitter, PROGRESS, ProcessStage.CREATE_EXCEL,
                "上传修改后的数据到oss", null, result, result.size(), null);
        // 6. 构建统一格式的响应
        return AiChatResponse.builder()
                .requestId(aiRequestEntity.getId())
                .aiResponse("")
                .sqlQuery(sql)
                .resultData(result)
                .resultCount(result.size())
                .status(AiRequestStatus.SUCCESS.getCode())
                .isModificationRequest(true)
                .modifiedExcelUrl(excelDownLoadUrl)
                .build();
    }

    // 根据查询出来的结果，生成新的excel文件，上传文件到oss。返回上传的地址
    private String generateModifiedExcel(List<Map<String, Object>> result, Long filedId) {
        // 1. 根据fileId获取原始文件信息
        String fileName = getFileById(filedId).getFileName();
        // 2. 创建excel表格
        try {
            Workbook workbook = WorkbookFactory.create(true);
            Sheet sheet = workbook.createSheet("修改后的数据");
            // 设置excel的样式
            CellStyle cellStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 12);
            cellStyle.setFont(headerFont);
            cellStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);

            // 获取第一行
            Map<String, Object> firstRow = result.get(0);
            List<String> columOrder = new ArrayList<>(firstRow.keySet());
            List<String> validColumns = new ArrayList<>();
            for (String column : columOrder) {
                if (!"id".equalsIgnoreCase(column)) {
                    validColumns.add(column);
                }
            }

            // 写入表头
            Row headerRow = sheet.createRow(0);
            int colIndex = 0;
            for (String column : validColumns) {
                Cell cell = headerRow.createCell(colIndex++);
                cell.setCellValue(column);
                cell.setCellStyle(cellStyle);
            }

            // 写入数据行
            int rowIndex = 1;
            for (Map<String, Object> row : result) {
                Row excelRow = sheet.createRow(rowIndex++);
                colIndex = 0;
                for (String column : validColumns) {
                    Cell cell = excelRow.createCell(colIndex++);
                    cell.setCellStyle(dataStyle);
                    Object value = row.get(column);
                    if (value != null) {
                        if (value instanceof  Number) {
                            cell.setCellValue(((Number) value).doubleValue());
                        } else {
                            cell.setCellValue(value.toString());
                        }
                    }
                }
            }

            // 自动调整列宽
            for (int i =0; i <validColumns.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            workbook.close();

            byte[] excelBytes = outputStream.toByteArray();
            MultipartFile multipartFile = new MultipartFile() {
                @Override
                public String getName() {
                    return fileName;
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
                    return excelBytes.length == 0;
                }

                @Override
                public long getSize() {
                    return excelBytes.length;
                }

                @Override
                public byte[] getBytes() throws IOException {
                    return excelBytes;
                }

                @Override
                public InputStream getInputStream() throws IOException {
                    return new ByteArrayInputStream(excelBytes);
                }

                @Override
                public void transferTo(File dest) throws IOException, IllegalStateException {
                        throw new UnsupportedEncodingException("当前数据文件格式不支持excel");
                }
            };
            // 3. 把表格传到oss，返回下载地址
            String url = ossService.uploadFile(multipartFile, "modified_excel/");
            log.info("下载文件的地址{}", url);
            return url;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // 用户生成修改语句
    private String generateUpdateSql(String tableName, String userInput) {
        // 1. 先获取表结构
        List<Map<String, Object>> tableStructure = sqlGenerationService.getTableStructure(tableName);

        // 2. 结合表结构和用户输入去封装提示词
        String sql =  aiModelService.getUpdateSql(userInput, tableName, tableStructure);


        // 3. 获取AI响应中的SQL
        return sql;

    }


    // 发送处理完成事件
    private void sendCompleteEvent(SseEmitter sseEmitter, AiUnifiedResponse aiUnifiedResponse) {
        // 1. 判断当前是查询、生成图表还是修改
        List<Map<String, Object>> resultPreview = new ArrayList<>();
        if (aiUnifiedResponse.getNeedChart() != null && aiUnifiedResponse.getNeedChart()) {
            if (aiUnifiedResponse.getChartDataList() != null) {
                resultPreview = new ArrayList<>(aiUnifiedResponse.getChartDataList());
            }
        } else {
            List<Map<String, Object>> rows = aiUnifiedResponse.getResultData() == null
                    ? Collections.emptyList()
                    : aiUnifiedResponse.getResultData();
            resultPreview = new ArrayList<>(
                    rows.subList(0, Math.min(5, rows.size()))
            );
        }

        // 2. 再去发送完成事件
        StreamProcessEvent event = StreamProcessEvent.builder()
                .eventType("complete")
                .stage(ProcessStage.COMPLETE.getCode())
                .progress(ProcessStage.COMPLETE.getProgress())
                .message("处理完成")
                .detail("处理完成")
                .completed(true)
                .result(aiUnifiedResponse)
                .error(null)
                .sqlQuery(aiUnifiedResponse.getSqlQuery())
                .resultPreview(resultPreview)
                .resultCount(aiUnifiedResponse.getResultCount())
                .aiResponseContent(aiUnifiedResponse.getAiResponse())
                .build();

        try {
            String json = objectMapper.writeValueAsString(event);
            String payload = json == null ? "" :json;
            sseEmitter.send(SseEmitter.event().name("complete").data(payload));
        } catch (Exception e) {
            log.error("发送完成事件失败{},", e.getMessage(), e);
        }
    }



    // 处理生成图表的逻辑
    private AiUnifiedResponse handleChartFlow(AiChatRequest request, AiRequestEntity aiRequestEntity, SseEmitter sseEmitter) {
        // 1. 发送处理对话的事件
        sendProgressEventWithData(sseEmitter, PROGRESS, ProcessStage.PROCESS_CHAT,
                "开始处理对话...", null, null, null, null);
        // 2. 生成图表所需要的数据
        AiChartResponse aiChartResponse = generateChart(request, sseEmitter);
        // 3. 发送后续处理的事件
        sendProgressEventWithData(sseEmitter, PROGRESS, ProcessStage.CREATE_CHART,
                null, null, null, null, null);
        // 4. 把生成的数据保存到数据库
        aiRequestEntity.setAiResponse(JSON.toJSONString(aiChartResponse));
        aiRequestMapper.updateById(aiRequestEntity);

        // 5. 封装响应数据
        return AiUnifiedResponse.builder()
                .requestId(aiRequestEntity.getId())
                .aiResponse(aiChartResponse.getChartDescription())
                .sqlQuery(aiChartResponse.getGeneratedSql())
                .resultData(aiChartResponse.getChartData())
                .resultCount(aiChartResponse.getChartData().size())
                .status(AiRequestStatus.SUCCESS.getCode())
                .needChart(true)
                .chartId(aiChartResponse.getChartId())
                .chartType(aiChartResponse.getChartType())
                .chartData(JSON.toJSONString(aiChartResponse.getChartData()))
                .chartDataList(aiChartResponse.getChartData())
                .xlabel(aiChartResponse.getXlabel())
                .ylabel(aiChartResponse.getYlabel())
                .fileName(aiChartResponse.getFileName())
                .build();
    }

    // 用来生成图表所需要的数据
    private AiChartResponse generateChart(AiChatRequest request, SseEmitter sseEmitter) {
        // 1. 获取当前文件关联的所有的表名
        List<String> tableNames = getTableNamesByFileId(request.getFileId());

        // 2. 需要根据用户输入的内容，结合AI大模型筛选出来要处理的mysql表
        String tableName = determineTargetTable(request.getUserInput(), tableNames, request.getFileId());

        // 3. 结合表结构与用户输入命令->生成SQL
        String sql = generateSql(tableName, request.getUserInput());

        sendProgressEventWithData(sseEmitter, PROGRESS, ProcessStage.QUERY_SQL,
                "生成查询sql", sql, null, null, null);

        // 4. 执行sql，查询结果
        List<Map<String, Object>> chartData = sqlGenerationService.excuteQuery(sql, tableName);

        sendProgressEventWithData(sseEmitter, PROGRESS, ProcessStage.EXECUTE_QUERY_SQL,
                "执行sql", sql, chartData, chartData.size(), null);
        // 5. 结构化封装结合AI去生成二维平面图的x轴和y轴的命名
        ChartLabels chartLabels = generateChartLabelsWithAI(request.getUserInput(), chartData);

        // 6. 判断图形的类型
        String chartType = determineChartType(request.getUserInput());

        // 7. 构建返回给前端的响应
        return AiChartResponse.builder()
                .chartId(String.valueOf(System.currentTimeMillis()))
                .chartType(chartType)
                .generatedSql(sql)
                .chartData(chartData)
                .xlabel(chartLabels.getXlabel())
                .ylabel(chartLabels.getYlabel())
                .fileId(request.getFileId())
                .fileName(getFileById(request.getFileId()).getFileName())
                .build();

    }

    private FilesEntity getFileById(Long fileId) {
        return fileMetaDataService.getFileById(fileId);
    }

    private String determineChartType(String userInput) {
        if (userInput.contains("扇形") || userInput.contains("饼") || userInput.contains("占比")) {
            return "pie";  // 扇形图
        } else if (userInput.contains("折线") || userInput.contains("line")) {
            return "line";  // 折线图
        }
        return "bar"; // 柱状图
    }


    // 结构化封装结合AI去生成二维平面图的x轴和y轴的命名
    private ChartLabels generateChartLabelsWithAI(String prompt, List<Map<String, Object>> chartData) {
        // 1. 构造AI提示词
        StringBuilder dataSummary = new StringBuilder();
        dataSummary.append("数据字段：");
        dataSummary.append(String.join(", ", chartData.get(0).keySet()));
        dataSummary.append("\n数据行数：").append(chartData.size());
        if (chartData.size() > 1) {
            dataSummary.append("\n数据样本：");
            Map<String, Object> sampleRow = chartData.get(1);
            sampleRow.forEach((key, value) ->
                    dataSummary.append(key).append("=").append(value).append(", ")
            );
        }

        String result = String.format("" +
                "你是一个专业的数据分析是，请根据用户需求和数据摘要，生成图表的X轴和Y轴标签。\n"+
                "用户需求: %s\n"+
                "数据摘要: %s\n"+
                "请分析数据特点，生成合适的轴标签:\n"+
                "1. xlabel应该是分类（如：姓名、成绩、地区）\n"+
                "2. ylabel应该是数值（如：成绩分数，年龄大小，百分比）\n"+
                "3. 标签不要太长，不超过10个字符\n"+
                "示例： \n" +
                "如果数据是[姓名：张三， 月薪：5000]\n"+
                "将来的结果: {\"xlabel\":\"姓名\", \"ylabel\":\"月薪(元)\"}\n"+
                "结果以json的格式返回"
                , prompt, dataSummary
        );
        
        String aiResponse = aiModelService.generateAiResponse(result, chartData);
        
        ChartLabels chartLabels = parseChatLabelsFromAI(aiResponse);
        return chartLabels;
    }

    private ChartLabels parseChatLabelsFromAI(String aiResponse) {
        try {
            return objectMapper.readValue(aiResponse, ChartLabels.class);
        } catch (JsonProcessingException e) {
            return new ChartLabels("类别", "数值");
        }
    }


    // 结合表结构与用户输入命令->生成SQL
    private String generateSql(String tableName, String userInput) {
        // 1. 先获取表结构
        List<Map<String, Object>> tableStructure = sqlGenerationService.getTableStructure(tableName);

        // 2. 结合表结构和用户输入去封装提示词
        String sql =  aiModelService.getSql(userInput, tableName, tableStructure);


        // 3. 获取AI响应中的SQL
        return sql;
    }

    // 根据用户输入的命令，来判断应该处理excel下面的哪一张表
    private String determineTargetTable(String userInput, List<String> tableNames, Long fileId) {
        if (tableNames.size() == 1) {
            log.info("excel是单sheet的，只有一张表");
            return tableNames.get(0);
        }

        // 1. 提取关键词
        List<String> keyFields = aiModelService.getFieldsFromUserInput(userInput);

        // 2. 遍历关键词来获取mysql表
        for (String field : keyFields) {
           String tableName = fileMetaDataService.getTableNameByFileIdAndHeader(field, fileId);
           if (tableName != null) {
               return tableName;
           }
        }
        return null;

    }


    // 根据用户的输入来判断要执行什么样的操作（查、改、图表、拒绝）
    private TypeResult judgeUserInputAndSendProgress(AiChatRequest aiChatRequest, SseEmitter sseEmitter) {

        String description = "";
        // 1. 判断是否需要生成图表
        boolean needChart = requiresChartGeneration(aiChatRequest.getUserInput());

        if (needChart) {
            description = "需要生成图表";
            TypeResult typeResult = new TypeResult(needChart, false, true, description);

            sendProgressEventWithData(sseEmitter, PROGRESS, ProcessStage.ANALYZE_INPUT, description,
                    null, null, null, null);
            return typeResult;
        }

        // 2. 判断是否需要执行修改操作
        boolean isModificationRequest = isModificationGeneration(aiChatRequest.getUserInput());

        if (isModificationRequest) {
            description = "需要修改数据";
            TypeResult typeResult = new TypeResult(needChart, isModificationRequest, true, description);
            sendProgressEventWithData(sseEmitter, PROGRESS, ProcessStage.ANALYZE_INPUT, description,
                    null, null, null, null);
            return typeResult;
        }

        // 3. 判断是否需要拒绝
        boolean isContinue = isContinueGeneration(aiChatRequest.getUserInput());

        if (!isContinue) {
            description = "是否继续";
            TypeResult typeResult = new TypeResult(needChart, isModificationRequest, false, description);
            sendProgressEventWithData(sseEmitter, PROGRESS, ProcessStage.ANALYZE_INPUT, description,
                    null, null, null, null);
            return typeResult;
        }

        // 4. 普通查询
        description = "普通查询";
        TypeResult typeResult = new TypeResult(needChart, isModificationRequest, true, description);
        sendProgressEventWithData(sseEmitter, PROGRESS, ProcessStage.ANALYZE_INPUT, description,
                null, null, null, null);
        return typeResult;
    }

    // 用来判断用户输入的内容是否需要执行拒绝操作
    private boolean isContinueGeneration(String userInput) {
        // 1. 构建提示词：系统提示词+用户提示词
        String prompt = String.format(
                "请判断一下用户输入是否需要拒绝执行，标准如下：\n" +
                        "- 只允许处理跟数据相关的内容：查询数据、修改数据、绘制图表\n" +
                        "- 假如用户的问题是非正向的，直接返回否 \n" +
                        "- 请回答 `是` 或者 `否`即可， 不要去解释， 也不要输出多余的内容\n" +
                        "\n 用户的输入是: %s", userInput
        );
        // 2. 去调用大模型
        String response = aiModelService.generateAiResponse(prompt, null);
        return response.equals("是");
    }

    // 用来判断用户输入的内容是否需要执行修改操作
    private boolean isModificationGeneration(String userInput) {
        // 1. 构建提示词：系统提示词+用户提示词
        String prompt = String.format(
                "请判断一下用户输入是否需要执行数据的修改操作，标准如下：\n" +
                        "- 只允许执行修改操作，修改操作包含：加、减、乘、除 \n" +    // 这里只执行update, insert和delete操作，同学课下完成
                        "- 假如用户有新增和删除数据的意图，直接返回否 \n" +
                        "- 相近的词可以替换，例如：改正也可以认为是修改 \n" +
                        "- 请回答 `是` 或者 `否`即可， 不要去解释， 也不要输出多余的内容\n" +
                        "\n 用户的输入是: %s", userInput
        );
        // 2. 去调用大模型
        String response = aiModelService.generateAiResponse(prompt, null);
        return response.equals("是");
    }


    // 用来判断用户输入的内容是否需要生成图表
    private boolean requiresChartGeneration(String userInput) {
        // 1. 构建提示词：系统提示词+用户提示词
        String prompt = String.format(
                "请判断一下用户输入是否需要生成图表，标准如下：\n" +
                        "- 只有当用户明确表达出需要绘制图表的时候，再给他绘制图表 \n" +
                        "- 绘制的图表只能选择三种：柱状图、折线图、扇形图 \n" +
                        "- 相近的词可以替换，例如：扇形图也可以称作饼状图 \n" +
                        "- 请回答 `是` 或者 `否`即可， 不要去解释， 也不要输出多余的内容\n" +
                        "\n 用户的输入是: %s", userInput
        );
        // 2. 去调用大模型
        String response = aiModelService.generateAiResponse(prompt, null);
        return response.equals("是");
    }


    // 用来获取表结构
    private void loadTableStructureAndSendProgress(List<String> tableNames, SseEmitter sseEmitter) {
        // 1. 遍历表名列表
        for (String tableName : tableNames) {
            List<Map<String, Object>> tableStructure = sqlGenerationService.getTableStructure(tableName);
            sendProgressEventWithData(sseEmitter, PROGRESS, ProcessStage.GET_TABLE_STRUCTURE,
                    "当前的表: " + tableName + "共有" + tableStructure.size() + "个字段",
                        null,
                    null,
                    null,
                    null
                    );
        }
    }

    // 校验文件权限，并获取文件下面的表名
    private List<String> validateFileAndSendProgress(AiChatRequest aiChatRequest, Long userId, SseEmitter sseEmitter) {
        // 1. 先来发送验证文件的事件
        sendProgressEventWithData(sseEmitter, PROGRESS, ProcessStage.VALIDATE_FILE
                , null, null, null, null, null);
        // 2. 判断文件的权限
        FilesEntity filesEntity = getFileById(userId, aiChatRequest.getFileId());

        if (filesEntity == null) {
            throw new IllegalArgumentException("文件不存在或没有权限");
        }
        // 3. 获取表名列表
        List<String> tableNames = getTableNamesByFileId(aiChatRequest.getFileId());
        String tableNameInfo = tableNames.size() == 1 ? tableNames.get(0) :
                tableNames.size() + "个表" + String.join(", ", tableNames);
        // 4. 发送事件
        sendProgressEventWithData(sseEmitter, PROGRESS, ProcessStage.GET_TABLE_NAMES, "表名: "+ tableNameInfo,
                null, null, null, null);
        return tableNames;
    }


    // 根据文件ID获取所有的表名
    private List<String> getTableNamesByFileId(Long fileId) {
        return fileMetaDataService.getTableNamesByFileId(fileId);
    }


    // 判断用userId是否拥有fileId的权限
    private FilesEntity getFileById(Long userId, Long fileId) {
        return fileMetaDataService.getFileById(userId, fileId);
    }

    // 初始化AI请求记录
    private AiRequestEntity initStreamRequest(AiChatRequest aiChatRequest, Long userId) {
        AiRequestEntity aiRequestEntity = new AiRequestEntity();
        aiRequestEntity.setUserId(userId);
        aiRequestEntity.setFileId(aiChatRequest.getFileId() == null ? 0L : aiChatRequest.getFileId());
        aiRequestEntity.setUserInput(aiChatRequest.getUserInput());
        aiRequestEntity.setStatus(AiRequestStatus.PROCESSING.getCode());
        aiRequestEntity.setAiResponse("");
        aiRequestMapper.insert(aiRequestEntity);
        return aiRequestEntity;
    }

    // 发送处理进度事件
    private void sendProgressEventWithData(
            SseEmitter sseEmitter, // 事件发送器
            String eventType, // 事件类型
            ProcessStage processStage, // 处理状态枚举
            String detail, // 详细的消息
            String sqlQuery, // SQL语句
            List<Map<String, Object>> resultPreview, // 查询结果
            Integer resultCount, // 查询结果总数
            String aiResponseContent // AI响应
    ) {
        StreamProcessEvent event = StreamProcessEvent.builder()
                .eventType(eventType)
                .stage(processStage.getCode())
                .progress(processStage.getProgress())
                .message(processStage.getDescription())
                .detail(detail)
                .completed("complete".equals(eventType))
                .result(null)
                .error("error".equals(eventType) ? detail : null)
                .sqlQuery(sqlQuery)
                .resultPreview(resultPreview)
                .resultCount(resultCount)
                .aiResponseContent(aiResponseContent)
                .build();
        try {
            sseEmitter.send(
                    SseEmitter.event()
                            .name(eventType)
                            .data(objectMapper.writeValueAsString(event))
            );
        } catch (IOException e) {
            log.error("发送事件失败:{}", e.getMessage());
            throw new RuntimeException(e);
        }
    }


    // 分析输入阶段的结果对象
    @Data
    @AllArgsConstructor
    private static  class TypeResult {
        private boolean needChart;  // 是否需要生成图表
        private boolean modificationRequest; // 是否需要执行修改操作
        private boolean continueNext;   // 用户命令出圈了，直接拒绝即可
        private String description; // 描述信息
    }
}
