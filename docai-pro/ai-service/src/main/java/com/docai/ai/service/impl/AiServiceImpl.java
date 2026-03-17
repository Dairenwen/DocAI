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
import com.docai.ai.dto.request.SendContentEmailRequest;
import com.docai.ai.dto.request.SendEmailRequest;
import com.docai.ai.dto.response.*;
import com.docai.ai.entity.AiRequestEntity;
import com.docai.ai.entity.ExtractedFieldEntity;
import com.docai.ai.entity.SourceDocumentEntity;
import com.docai.ai.mapper.AiRequestMapper;
import com.docai.ai.mapper.ExtractedFieldMapper;
import com.docai.ai.mapper.SourceDocumentMapper;
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
import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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

    @Autowired
    private SourceDocumentMapper sourceDocumentMapper;

    @Autowired
    private ExtractedFieldMapper extractedFieldMapper;

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
    private com.docai.common.service.EmailService emailService;

    @Autowired
    @Qualifier("toolCallingChatClient")
    private ChatClient toolCallingChatClient;


    @Override
    public void streamUnifiedChat(AiChatRequest aiChatRequest, Long userId, SseEmitter sseEmitter) {
        // 1. 通知前端，可以开始处理请求了
        AiRequestEntity aiRequestEntity = null;
        try {
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

        // 先尝试在 file-service 的 files 表查找（Excel文件）
        FilesEntity filesEntity = fileMetaDataService.getFileById(userId, aiChatRequest.getFileId());

        // 如果 files 表找不到，尝试在 source_documents 表查找（源文档）
        if (filesEntity == null) {
            SourceDocumentEntity sourceDoc = sourceDocumentMapper.selectById(aiChatRequest.getFileId());
            if (sourceDoc != null && sourceDoc.getUserId().equals(userId)) {
                // 源文档走带上下文的通用对话
                AiUnifiedResponse docChatResponse = handleSourceDocChatFlow(
                        aiChatRequest, aiRequestEntity, sourceDoc, sseEmitter);
                sendCompleteEvent(sseEmitter, docChatResponse);
                sseEmitter.complete();
                return;
            }
            // 两个表都找不到
            throw new IllegalArgumentException("文档不存在或没有权限");
        }

        // 2. 校验文件的权限（files表中的Excel文件流程）
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
        } catch (Exception e) {
            log.error("streamUnifiedChat处理异常: {}", e.getMessage(), e);
            AiUnifiedResponse errorResponse = AiUnifiedResponse.builder()
                    .aiResponse("抱歉，服务处理过程中发生异常：" + (e.getMessage() != null ? e.getMessage() : "未知错误") + "。请稍后重试。")
                    .sqlQuery(null)
                    .resultData(Collections.emptyList())
                    .resultCount(0)
                    .status(AiRequestStatus.FAILED.getCode())
                    .needChart(false)
                    .isModificationRequest(false)
                    .modifiedExcelUrl(null)
                    .build();
            try {
                sendCompleteEvent(sseEmitter, errorResponse);
                sseEmitter.complete();
            } catch (Exception ignored) {
                sseEmitter.completeWithError(e);
            }
        }
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

        /**
         * 处理源文档关联的对话流程：使用文档提取字段作为上下文
         */
        private AiUnifiedResponse handleSourceDocChatFlow(
            AiChatRequest aiChatRequest,
            AiRequestEntity aiRequestEntity,
            SourceDocumentEntity sourceDoc,
            SseEmitter sseEmitter
        ) {
        sendProgressEventWithData(sseEmitter, PROGRESS, ProcessStage.PROCESS_CHAT,
            "已关联源文档：" + sourceDoc.getFileName() + "，正在读取原文内容", null, null, null, null);

        try {
            // 读取原文件内容作为上下文
            String rawContent = readSourceDocContent(sourceDoc);

            StringBuilder context = new StringBuilder();
            context.append("文档名称：").append(sourceDoc.getFileName()).append("\n\n");
            if (rawContent != null && !rawContent.isBlank()) {
                // 限制上下文长度，避免超出LLM token限制
                String trimmed = rawContent.length() > 15000 ? rawContent.substring(0, 15000) + "\n...(内容已截断)" : rawContent;
                context.append("=== 文档原文内容 ===\n").append(trimmed).append("\n=== 原文结束 ===\n");
            } else {
                // 回退到提取字段
                log.warn("无法读取源文档原文，回退到提取字段: docId={}", sourceDoc.getId());
                if (sourceDoc.getDocSummary() != null && !sourceDoc.getDocSummary().isEmpty()) {
                    context.append("文档摘要：").append(sourceDoc.getDocSummary()).append("\n");
                }
                List<ExtractedFieldEntity> fields = extractedFieldMapper.selectList(
                    new LambdaQueryWrapper<ExtractedFieldEntity>()
                        .eq(ExtractedFieldEntity::getDocId, sourceDoc.getId())
                );
                if (!fields.isEmpty()) {
                    context.append("提取字段：\n");
                    for (ExtractedFieldEntity f : fields) {
                        context.append("- ").append(f.getFieldName()).append("：").append(f.getFieldValue());
                        if (f.getSourceText() != null && !f.getSourceText().isEmpty()) {
                            context.append("（来源：").append(f.getSourceText()).append("）");
                        }
                        context.append("\n");
                    }
                }
            }

            String prompt = String.format(
                "你是DocAI智能助手。以下是用户关联的文档完整内容：\n%s\n\n" +
                "请根据上述文档的完整原文回答用户问题。如果用户要求编辑、修改、删除、增添内容，" +
                "请输出修改后的完整文档内容，只输出文档内容本身，不要添加额外说明。\n用户问题：%s",
                context, aiChatRequest.getUserInput()
            );
            String reply = llmService.generateText(prompt);

            aiRequestEntity.setAiResponse(reply);
            aiRequestEntity.setStatus(AiRequestStatus.SUCCESS.getCode());
            aiRequestMapper.updateById(aiRequestEntity);

            // 检测是否为编辑/修改操作，自动保存修改后的文档
            String userInput = aiChatRequest.getUserInput().toLowerCase();
            boolean isEditRequest = userInput.contains("修改") || userInput.contains("编辑") || userInput.contains("删除")
                    || userInput.contains("增加") || userInput.contains("添加") || userInput.contains("替换")
                    || userInput.contains("改为") || userInput.contains("改成") || userInput.contains("更新")
                    || userInput.contains("移除") || userInput.contains("插入") || userInput.contains("润色");

            String modifiedUrl = null;
            if (isEditRequest && reply != null && reply.length() > 50) {
                try {
                    Map<String, String> editResult = applyDocumentEdit(sourceDoc.getId(), aiRequestEntity.getUserId(), reply);
                    modifiedUrl = editResult.get("downloadUrl");
                    log.info("源文档编辑自动保存: docId={}, url={}", sourceDoc.getId(), modifiedUrl);
                } catch (Exception e) {
                    log.warn("源文档编辑自动保存失败: {}", e.getMessage());
                }
            }

            return AiUnifiedResponse.builder()
                .requestId(aiRequestEntity.getId())
                .aiResponse(reply)
                .sqlQuery(null)
                .resultData(Collections.emptyList())
                .resultCount(0)
                .status(AiRequestStatus.SUCCESS.getCode())
                .needChart(false)
                .isModificationRequest(isEditRequest)
                .modifiedExcelUrl(modifiedUrl)
                .build();
        } catch (Exception ex) {
            log.error("源文档对话失败: {}", ex.getMessage(), ex);
            String fallbackReply = "抱歉，基于文档\"" + sourceDoc.getFileName() + "\"的AI对话暂时不可用，请稍后重试。";
            aiRequestEntity.setAiResponse(fallbackReply);
            aiRequestEntity.setStatus(AiRequestStatus.FAILED.getCode());
            aiRequestMapper.updateById(aiRequestEntity);

            return AiUnifiedResponse.builder()
                .requestId(aiRequestEntity.getId())
                .aiResponse(fallbackReply)
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

    /**
     * 读取源文档的原始文本内容
     */
    private String readSourceDocContent(SourceDocumentEntity doc) {
        String storagePath = doc.getStoragePath();
        if (storagePath == null || storagePath.isBlank()) {
            log.warn("源文档无存储路径: docId={}", doc.getId());
            return null;
        }
        File file = new File(storagePath);
        if (!file.exists()) {
            log.warn("源文档文件不存在: path={}", storagePath);
            return null;
        }
        try {
            String fileType = doc.getFileType() != null ? doc.getFileType().toLowerCase() : "";
            return switch (fileType) {
                case "docx" -> readDocxContent(file);
                case "xlsx" -> readXlsxContent(file);
                default -> Files.readString(file.toPath(), StandardCharsets.UTF_8);
            };
        } catch (Exception e) {
            log.error("读取源文档内容失败: path={}, error={}", storagePath, e.getMessage());
            return null;
        }
    }

    private String readDocxContent(File file) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (FileInputStream fis = new FileInputStream(file);
             XWPFDocument doc = new XWPFDocument(fis)) {
            for (XWPFParagraph para : doc.getParagraphs()) {
                String text = para.getText();
                if (text != null && !text.trim().isEmpty()) {
                    sb.append(text.trim()).append("\n");
                }
            }
            for (XWPFTable table : doc.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    StringBuilder rowText = new StringBuilder();
                    row.getTableCells().forEach(cell -> {
                        String cellText = cell.getText();
                        if (cellText != null && !cellText.trim().isEmpty()) {
                            if (!rowText.isEmpty()) rowText.append(" | ");
                            rowText.append(cellText.trim());
                        }
                    });
                    if (!rowText.isEmpty()) {
                        sb.append(rowText).append("\n");
                    }
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private String readXlsxContent(File file) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (FileInputStream fis = new FileInputStream(file);
             XSSFWorkbook workbook = new XSSFWorkbook(fis)) {
            DataFormatter formatter = new DataFormatter();
            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                XSSFSheet sheet = workbook.getSheetAt(s);
                sb.append("=== Sheet: ").append(sheet.getSheetName()).append(" ===\n");
                for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) continue;
                    StringBuilder rowText = new StringBuilder();
                    for (int c = 0; c < row.getLastCellNum(); c++) {
                        Cell cell = row.getCell(c);
                        if (cell != null) {
                            String val = formatter.formatCellValue(cell).trim();
                            if (!val.isEmpty()) {
                                if (!rowText.isEmpty()) rowText.append(" | ");
                                rowText.append(val);
                            }
                        }
                    }
                    if (!rowText.isEmpty()) {
                        sb.append(rowText).append("\n");
                    }
                }
                sb.append("\n");
            }
        }
        return sb.toString();
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

    @Override
    public Boolean sendContentEmail(SendContentEmailRequest request) {
        String subject = request.getSubject();
        if (subject == null || subject.isBlank()) {
            subject = "DocAI - AI生成内容";
        }
        String htmlContent = "<html><body>" +
                "<h3>DocAI AI生成内容</h3>" +
                "<div style=\"white-space: pre-wrap; font-family: sans-serif; line-height: 1.6;\">" +
                request.getContent().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>") +
                "</div>" +
                "<hr><p style=\"color: #999; font-size: 12px;\">此邮件由 DocAI 系统自动发送</p>" +
                "</body></html>";
        return emailService.sendHtmlEmail(request.getEmail(), subject, htmlContent);
    }

    @Override
    public Map<String, String> applyDocumentEdit(Long docId, Long userId, String content) {
        SourceDocumentEntity doc = sourceDocumentMapper.selectById(docId);
        if (doc == null) throw new RuntimeException("文档不存在");
        if (!doc.getUserId().equals(userId)) throw new RuntimeException("无权操作该文档");

        String fileType = doc.getFileType() != null ? doc.getFileType().toLowerCase() : "txt";
        String baseName = doc.getFileName();
        int dotIdx = baseName.lastIndexOf('.');
        String nameWithoutExt = dotIdx > 0 ? baseName.substring(0, dotIdx) : baseName;
        String editedName = nameWithoutExt + "_edited." + (fileType.equals("docx") ? "docx" : fileType.equals("md") ? "md" : "txt");

        File editDir = new File(System.getProperty("java.io.tmpdir"), "docai_edited");
        if (!editDir.exists()) editDir.mkdirs();
        File editedFile = new File(editDir, editedName);

        try {
            if ("docx".equals(fileType)) {
                // 写入docx格式
                try (XWPFDocument document = new XWPFDocument()) {
                    String[] lines = content.split("\n");
                    for (String line : lines) {
                        XWPFParagraph para = document.createParagraph();
                        XWPFRun run = para.createRun();
                        run.setText(line);
                    }
                    try (FileOutputStream fos = new FileOutputStream(editedFile)) {
                        document.write(fos);
                    }
                }
            } else {
                // txt/md等文本格式直接写入
                Files.writeString(editedFile.toPath(), content, StandardCharsets.UTF_8);
            }

            log.info("文档在线修改完成: docId={}, editedFile={}", docId, editedFile.getAbsolutePath());

            // 返回下载URL (通过AI服务的download端点)
            String downloadUrl = "/api/v1/ai/documents/edited/" + java.net.URLEncoder.encode(editedName, "UTF-8");
            Map<String, String> result = new HashMap<>();
            result.put("downloadUrl", downloadUrl);
            result.put("fileName", editedName);
            return result;
        } catch (Exception e) {
            log.error("文档在线修改失败: {}", e.getMessage(), e);
            throw new RuntimeException("文档保存失败: " + e.getMessage());
        }
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
