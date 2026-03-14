package com.docai.ai.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.docai.ai.entity.ExtractedFieldEntity;
import com.docai.ai.entity.FieldAliasDictEntity;
import com.docai.ai.entity.SourceDocumentEntity;
import com.docai.ai.mapper.ExtractedFieldMapper;
import com.docai.ai.mapper.FieldAliasDictMapper;
import com.docai.ai.mapper.SourceDocumentMapper;
import com.docai.ai.service.ExtractionService;
import com.docai.ai.service.LlmService;
import com.docai.common.service.OssService;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class ExtractionServiceImpl implements ExtractionService {

    // 限制同时进行的LLM提取并发数为2，避免API限流
    private static final Semaphore LLM_SEMAPHORE = new Semaphore(2);
    // 异步提取线程池
    private static final ExecutorService EXTRACT_EXECUTOR = Executors.newFixedThreadPool(3);

    @Autowired
    private SourceDocumentMapper sourceDocumentMapper;
    @Autowired
    private ExtractedFieldMapper extractedFieldMapper;
    @Autowired
    private FieldAliasDictMapper fieldAliasDictMapper;

    @Autowired
    private LlmService llmService;

    @Autowired
    private OssService ossService;

    // 标准化字段映射
    private static final Map<String, List<String>> ALIAS_MAP = new LinkedHashMap<>();
    static {
        ALIAS_MAP.put("project_name", Arrays.asList("项目名称", "课题名称", "申报项目名称", "项目名", "课题名"));
        ALIAS_MAP.put("owner", Arrays.asList("负责人", "项目负责人", "课题负责人", "主持人", "项目主持人"));
        ALIAS_MAP.put("org_name", Arrays.asList("单位名称", "申报单位", "承担单位", "所在单位", "依托单位", "工作单位"));
        ALIAS_MAP.put("phone", Arrays.asList("联系电话", "手机号码", "电话", "手机号", "手机", "联系方式"));
        ALIAS_MAP.put("email", Arrays.asList("电子邮箱", "邮箱", "E-mail", "Email", "email"));
        ALIAS_MAP.put("id_number", Arrays.asList("身份证号", "身份证号码", "证件号码", "证件号"));
        ALIAS_MAP.put("address", Arrays.asList("地址", "通讯地址", "联系地址", "通信地址"));
        ALIAS_MAP.put("start_date", Arrays.asList("开始日期", "起始日期", "开始时间", "起始时间", "项目起始"));
        ALIAS_MAP.put("end_date", Arrays.asList("结束日期", "截止日期", "结束时间", "截止时间", "项目终止"));
        ALIAS_MAP.put("budget", Arrays.asList("经费", "预算", "资助金额", "项目经费", "总经费", "资助额度"));
        ALIAS_MAP.put("dept_name", Arrays.asList("部门", "院系", "学院", "所属部门", "所属院系"));
        ALIAS_MAP.put("title", Arrays.asList("职称", "职务", "专业技术职称"));
        ALIAS_MAP.put("degree", Arrays.asList("学历", "学位", "最高学历", "最高学位"));
        ALIAS_MAP.put("research_field", Arrays.asList("研究方向", "研究领域", "专业领域", "学科方向"));
        ALIAS_MAP.put("postal_code", Arrays.asList("邮编", "邮政编码"));
    }

    @Override
    public SourceDocumentEntity uploadAndExtract(MultipartFile file, Long userId) {
        String originalFilename = file.getOriginalFilename();
        String fileType = getFileType(originalFilename);

        String ossKey = "";
        try {
            String fileUrl = ossService.uploadFile(file, "source_documents/");
            ossKey = getOssKey(fileUrl);
        } catch (Exception ex) {
            log.warn("源文档上传OSS失败，继续使用本地路径: {}", ex.getMessage());
        }

        // 保存文件到本地临时目录
        Path tempDir;
        Path savedPath;
        try {
            tempDir = Files.createTempDirectory("docai_source_");
            savedPath = tempDir.resolve(originalFilename);
            file.transferTo(savedPath.toFile());
        } catch (IOException e) {
            throw new RuntimeException("文件保存失败: " + e.getMessage());
        }

        // 创建源文档记录
        SourceDocumentEntity doc = new SourceDocumentEntity();
        doc.setUserId(userId);
        doc.setFileName(originalFilename);
        doc.setFileType(fileType);
        doc.setStoragePath(savedPath.toString());
        doc.setOssKey(ossKey);
        doc.setFileSize(file.getSize());
        doc.setUploadStatus("parsing");

        sourceDocumentMapper.insert(doc);

        // 异步执行LLM提取，立即返回文档记录
        final long docId = doc.getId();
        final String filePath = savedPath.toString();
        EXTRACT_EXECUTOR.submit(() -> {
            try {
                LLM_SEMAPHORE.acquire();
                try {
                    doExtraction(docId, filePath, fileType, originalFilename);
                } finally {
                    LLM_SEMAPHORE.release();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("文档提取任务被中断: docId={}", docId);
                updateDocStatus(docId, "failed", "提取任务被中断");
            }
        });

        return doc;
    }

    /**
     * 实际执行LLM提取的方法（在线程池中运行）
     */
    private void doExtraction(long docId, String filePath, String fileType, String originalFilename) {
        try {
            String textContent = extractTextContent(filePath, fileType);
            List<ExtractedFieldEntity> fields = callLlmExtract(docId, textContent, originalFilename);

            for (ExtractedFieldEntity field : fields) {
                standardizeField(field);
                extractedFieldMapper.insert(field);
                updateAliasDict(field);
            }

            updateDocStatus(docId, "parsed", fields.size() + "个字段已抽取");
        } catch (Exception e) {
            log.error("文档抽取失败: docId={}, error={}", docId, e.getMessage(), e);
            updateDocStatus(docId, "failed", "文档内容提取失败，请检查文件格式是否正确");
        }
    }

    private void updateDocStatus(long docId, String status, String summary) {
        SourceDocumentEntity doc = sourceDocumentMapper.selectById(docId);
        if (doc != null) {
            doc.setUploadStatus(status);
            doc.setDocSummary(summary);
            sourceDocumentMapper.updateById(doc);
        }
    }

    private String getOssKey(String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            return "";
        }
        String[] parts = fileUrl.split("/");
        if (parts.length >= 4) {
            StringBuilder key = new StringBuilder();
            for (int i = 3; i < parts.length; i++) {
                if (i > 3) {
                    key.append('/');
                }
                key.append(parts[i]);
            }
            return key.toString();
        }
        return "";
    }

    @Override
    public List<ExtractedFieldEntity> getFieldsByDocId(Long docId, Long userId) {
        // 先验证文档归属
        SourceDocumentEntity doc = sourceDocumentMapper.selectById(docId);
        if (doc == null || !doc.getUserId().equals(userId)) {
            return Collections.emptyList();
        }
        return extractedFieldMapper.selectList(
                new LambdaQueryWrapper<ExtractedFieldEntity>().eq(ExtractedFieldEntity::getDocId, docId)
        );
    }

    // 保留无userId的旧签名供内部调用
    public List<ExtractedFieldEntity> getFieldsByDocId(Long docId) {
        return extractedFieldMapper.selectList(
                new LambdaQueryWrapper<ExtractedFieldEntity>().eq(ExtractedFieldEntity::getDocId, docId)
        );
    }

    @Override
    public List<SourceDocumentEntity> getUserDocuments(Long userId) {
        return sourceDocumentMapper.selectList(
                new LambdaQueryWrapper<SourceDocumentEntity>().eq(SourceDocumentEntity::getUserId, userId)
                        .orderByDesc(SourceDocumentEntity::getCreatedAt)
        );
    }

    @Override
    public SourceDocumentEntity getDocument(Long docId, Long userId) {
        SourceDocumentEntity doc = sourceDocumentMapper.selectById(docId);
        if (doc == null || !doc.getUserId().equals(userId)) {
            return null;
        }
        return doc;
    }

    @Override
    public boolean deleteDocument(Long docId, Long userId) {
        SourceDocumentEntity doc = sourceDocumentMapper.selectById(docId);
        if (doc == null || !doc.getUserId().equals(userId)) {
            throw new RuntimeException("文档不存在或无权限");
        }
        // 删除提取字段
        extractedFieldMapper.delete(
                new LambdaQueryWrapper<ExtractedFieldEntity>().eq(ExtractedFieldEntity::getDocId, docId)
        );
        // 删除文档记录
        sourceDocumentMapper.deleteById(docId);
        // 删除OSS文件
        if (doc.getOssKey() != null && !doc.getOssKey().isEmpty()) {
            try {
                ossService.deleteFile(doc.getOssKey());
            } catch (Exception e) {
                log.warn("删除OSS文件失败: {}", e.getMessage());
            }
        }
        return true;
    }

    @Override
    public boolean batchDeleteDocuments(List<Long> docIds, Long userId) {
        for (Long docId : docIds) {
            deleteDocument(docId, userId);
        }
        return true;
    }

    /**
     * 从文件中提取文本内容
     */
    private String extractTextContent(String filePath, String fileType) throws Exception {
        return switch (fileType) {
            case "docx" -> extractFromDocx(filePath);
            case "xlsx" -> extractFromXlsx(filePath);
            case "txt", "md" -> extractFromText(filePath);
            default -> extractFromText(filePath);
        };
    }

    private String extractFromDocx(String filePath) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (FileInputStream fis = new FileInputStream(filePath);
             XWPFDocument doc = new XWPFDocument(fis)) {
            // 段落
            for (XWPFParagraph para : doc.getParagraphs()) {
                String text = para.getText();
                if (text != null && !text.trim().isEmpty()) {
                    sb.append(text.trim()).append("\n");
                }
            }
            // 表格
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

    private String extractFromXlsx(String filePath) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (FileInputStream fis = new FileInputStream(filePath);
             XSSFWorkbook workbook = new XSSFWorkbook(fis)) {
            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                XSSFSheet sheet = workbook.getSheetAt(s);
                sb.append("=== Sheet: ").append(sheet.getSheetName()).append(" ===\n");
                for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                    XSSFRow row = sheet.getRow(r);
                    if (row == null) continue;
                    StringBuilder rowText = new StringBuilder();
                    for (int c = 0; c < row.getLastCellNum(); c++) {
                        XSSFCell cell = row.getCell(c);
                        if (cell != null) {
                            String val = getCellStringValue(cell);
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

    private String extractFromText(String filePath) throws Exception {
        return Files.readString(Path.of(filePath), StandardCharsets.UTF_8);
    }

    private String getCellStringValue(XSSFCell cell) {
        try {
            return switch (cell.getCellType()) {
                case STRING -> cell.getStringCellValue().trim();
                case NUMERIC -> {
                    double val = cell.getNumericCellValue();
                    if (val == Math.floor(val)) yield String.valueOf((long) val);
                    else yield String.valueOf(val);
                }
                case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
                case FORMULA -> {
                    try { yield cell.getStringCellValue(); }
                    catch (Exception e) { yield String.valueOf(cell.getNumericCellValue()); }
                }
                default -> "";
            };
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 调用LLM进行结构化抽取（阶段1）
     * 使用DashScope的通义千问模型
     */
    private List<ExtractedFieldEntity> callLlmExtract(Long docId, String textContent, String fileName) {
        // 截断过长文本
        if (textContent.length() > 15000) {
            textContent = textContent.substring(0, 15000) + "\n...(文本已截断)";
        }

        String prompt = buildExtractionPrompt(textContent, fileName);

        String response;
        try {
            // 使用qwen-turbo进行提取，速度快且成本低，同时保证提取准确率
            response = llmService.generateText(prompt, "dashscope", "qwen-turbo");
        } catch (Exception e) {
            log.error("LLM抽取调用失败: {}", e.getMessage());
            return ruleBasedExtract(docId, textContent);
        }
        List<ExtractedFieldEntity> parsed = parseExtractionResponse(docId, response);
        if (parsed == null || parsed.isEmpty()) {
            return ruleBasedExtract(docId, textContent);
        }
        return parsed;
    }

    /**
     * 当外部LLM不可用时，基于规则提取常见“键:值”字段，保证流程可用性。
     */
    private List<ExtractedFieldEntity> ruleBasedExtract(Long docId, String textContent) {
        List<ExtractedFieldEntity> fields = new ArrayList<>();
        if (textContent == null || textContent.isBlank()) {
            return fields;
        }

        String[] lines = textContent.split("\\r?\\n");
        for (String raw : lines) {
            String line = raw == null ? "" : raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            Matcher m = Pattern.compile("^([^:：]{1,40})[:：]\\s*(.+)$").matcher(line);
            if (!m.find()) {
                continue;
            }
            String fieldName = m.group(1).trim();
            String value = m.group(2).trim();
            if (value.isEmpty()) {
                continue;
            }

            ExtractedFieldEntity field = new ExtractedFieldEntity();
            field.setDocId(docId);
            field.setFieldName(fieldName);
            field.setFieldValue(value);
            field.setFieldKey(normalizeKey(fieldName));
            field.setFieldType(detectFieldType(fieldName, value));
            field.setAliases(JSON.toJSONString(new String[]{fieldName}));
            field.setSourceText(line);
            field.setSourceLocation("line");
            field.setConfidence(new BigDecimal("0.7800"));
            fields.add(field);
        }
        return fields;
    }

    private String normalizeKey(String fieldName) {
        String normalized = fieldName == null ? "" : fieldName.toLowerCase(Locale.ROOT).replaceAll("\\s+", "_");
        return switch (normalized) {
            case "项目名称", "课题名称", "申报项目名称" -> "project_name";
            case "负责人", "项目负责人", "课题负责人" -> "owner";
            case "单位名称", "申报单位", "承担单位" -> "org_name";
            case "联系电话", "手机号码", "电话", "手机号" -> "phone";
            case "电子邮箱", "邮箱" -> "email";
            default -> normalized;
        };
    }

    private String detectFieldType(String fieldName, String value) {
        String text = (fieldName + " " + value).toLowerCase(Locale.ROOT);
        if (text.matches(".*(电话|手机|联系方式).*") || value.matches("^1\\d{10}$")) {
            return "phone";
        }
        if (text.matches(".*(日期|时间).*")) {
            return "date";
        }
        if (text.matches(".*(单位|大学|公司|机构).*")) {
            return "org";
        }
        if (value.matches("^-?\\d+(\\.\\d+)?$")) {
            return "number";
        }
        return "text";
    }

    private String buildExtractionPrompt(String textContent, String fileName) {
        return """
                你是一个专业的文档信息抽取助手。请从以下文档内容中抽取所有关键字段信息。
                
                文件名：%s
                
                文档内容：
                %s
                
                请严格按照以下JSON格式输出抽取结果，不要输出任何其他内容：
                {
                  "doc_title": "文档标题",
                  "doc_type": "文档类型",
                  "fields": [
                    {
                      "field_key": "标准化字段键（英文，如project_name）",
                      "field_name": "原始字段名（如：项目名称）",
                      "field_value": "字段值",
                      "field_type": "text|date|number|phone|org|person|enum",
                      "aliases": ["可能的别名"],
                      "source_text": "原文中包含该字段的语句",
                      "source_location": "字段在文档中的大致位置描述",
                      "confidence": 0.95
                    }
                  ],
                  "summary": "文档内容摘要"
                }
                
                抽取要求：
                1. 尽可能抽取所有有意义的字段，包括人名、机构名、日期、电话、地址、金额等
                2. field_type必须准确标注
                3. 每个字段必须带source_text原文证据
                4. confidence表示你对该抽取结果的置信度(0-1)
                5. 不要编造不存在的信息
                6. 对于表格数据，按行列关系抽取字段
                """.formatted(fileName, textContent);
    }

    private List<ExtractedFieldEntity> parseExtractionResponse(Long docId, String response) {
        List<ExtractedFieldEntity> fields = new ArrayList<>();

        try {
            // 尝试从响应中提取JSON
            String jsonStr = extractJsonFromResponse(response);
            JSONObject result = JSON.parseObject(jsonStr);
            JSONArray fieldsArray = result.getJSONArray("fields");

            if (fieldsArray != null) {
                for (int i = 0; i < fieldsArray.size(); i++) {
                    JSONObject fieldObj = fieldsArray.getJSONObject(i);
                    ExtractedFieldEntity entity = new ExtractedFieldEntity();
                    entity.setDocId(docId);
                    entity.setFieldKey(fieldObj.getString("field_key"));
                    entity.setFieldName(fieldObj.getString("field_name"));
                    entity.setFieldValue(fieldObj.getString("field_value"));
                    entity.setFieldType(fieldObj.getString("field_type"));

                    JSONArray aliasArray = fieldObj.getJSONArray("aliases");
                    if (aliasArray != null) {
                        entity.setAliases(aliasArray.toJSONString());
                    }

                    entity.setSourceText(fieldObj.getString("source_text"));
                    entity.setSourceLocation(fieldObj.getString("source_location"));

                    BigDecimal conf = fieldObj.getBigDecimal("confidence");
                    entity.setConfidence(conf != null ? conf : new BigDecimal("0.80"));

                    fields.add(entity);
                }
            }
        } catch (Exception e) {
            log.error("解析LLM抽取结果失败: {}", e.getMessage());
            // 尝试简单的键值对抽取作为fallback
            fields = fallbackExtraction(docId, response);
        }

        return fields;
    }

    private String extractJsonFromResponse(String response) {
        // 去掉可能的markdown代码块标记
        response = response.trim();
        if (response.startsWith("```json")) {
            response = response.substring(7);
        } else if (response.startsWith("```")) {
            response = response.substring(3);
        }
        if (response.endsWith("```")) {
            response = response.substring(0, response.length() - 3);
        }
        return response.trim();
    }

    private List<ExtractedFieldEntity> fallbackExtraction(Long docId, String text) {
        List<ExtractedFieldEntity> fields = new ArrayList<>();
        // 简单正则抽取 "键：值" 或 "键: 值" 模式
        Pattern pattern = Pattern.compile("([\\u4e00-\\u9fa5a-zA-Z]{2,15})[：:](\\s*)([^\\n]{1,200})");
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            ExtractedFieldEntity field = new ExtractedFieldEntity();
            field.setDocId(docId);
            field.setFieldName(matcher.group(1).trim());
            field.setFieldValue(matcher.group(3).trim());
            field.setFieldKey(normalizeFieldKey(matcher.group(1).trim()));
            field.setFieldType("text");
            field.setSourceText(matcher.group(0));
            field.setConfidence(new BigDecimal("0.60"));
            fields.add(field);
        }
        return fields;
    }

    /**
     * 标准化字段（阶段2）
     */
    private void standardizeField(ExtractedFieldEntity field) {
        // 文本清洗
        if (field.getFieldName() != null) {
            field.setFieldName(cleanFieldName(field.getFieldName()));
        }
        if (field.getFieldValue() != null) {
            field.setFieldValue(field.getFieldValue().trim());
        }

        // 字段名标准化
        String stdKey = normalizeFieldKey(field.getFieldName());
        if (stdKey != null) {
            field.setFieldKey(stdKey);
        }

        // 值格式化
        if ("date".equals(field.getFieldType()) && field.getFieldValue() != null) {
            field.setFieldValue(normalizeDateValue(field.getFieldValue()));
        }
        if ("phone".equals(field.getFieldType()) && field.getFieldValue() != null) {
            field.setFieldValue(field.getFieldValue().replaceAll("[^0-9+]", ""));
        }
    }

    private String cleanFieldName(String name) {
        return name.replaceAll("[：:()（）\\n\\r\\t]", "").trim();
    }

    private String normalizeFieldKey(String fieldName) {
        if (fieldName == null) return "unknown";
        for (Map.Entry<String, List<String>> entry : ALIAS_MAP.entrySet()) {
            for (String alias : entry.getValue()) {
                if (fieldName.contains(alias) || alias.contains(fieldName)) {
                    return entry.getKey();
                }
            }
        }
        // 如果没找到标准映射，保留原key
        return fieldName.toLowerCase()
                .replaceAll("[\\s\\u4e00-\\u9fa5]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
    }

    private String normalizeDateValue(String value) {
        // 尝试将各种日期格式统一为YYYY-MM-DD
        value = value.replaceAll("[年/]", "-").replaceAll("月", "-").replaceAll("日", "");
        return value.trim();
    }

    private void updateAliasDict(ExtractedFieldEntity field) {
        if (field.getFieldKey() == null || field.getFieldName() == null) return;

        // 检查是否已存在
        Long count = fieldAliasDictMapper.selectCount(
                new LambdaQueryWrapper<FieldAliasDictEntity>()
                        .eq(FieldAliasDictEntity::getStandardKey, field.getFieldKey())
                        .eq(FieldAliasDictEntity::getAliasName, field.getFieldName())
        );
        if (count == 0) {
            FieldAliasDictEntity alias = new FieldAliasDictEntity();
            alias.setStandardKey(field.getFieldKey());
            alias.setAliasName(field.getFieldName());
            alias.setFieldType(field.getFieldType());
            fieldAliasDictMapper.insert(alias);
        }
    }

    private String getFileType(String fileName) {
        if (fileName == null) return "txt";
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) return "txt";
        return fileName.substring(dot + 1).toLowerCase();
    }
}
