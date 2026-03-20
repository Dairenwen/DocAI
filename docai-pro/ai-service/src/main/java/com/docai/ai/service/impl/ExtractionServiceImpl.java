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

    // 限制同时进行的LLM提取并发数为1，避免API限流（DashScope QPS较低时安全值）
    private static final Semaphore LLM_SEMAPHORE = new Semaphore(1);
    // 异步提取线程池（虚拟线程调度，减少线程阻塞开销）
    private static final ExecutorService EXTRACT_EXECUTOR = Executors.newFixedThreadPool(4);
    // LLM调用间隔（毫秒），避免API限流
    private static final long LLM_CALL_INTERVAL_MS = 1500;
    private static volatile long lastLlmCallTime = 0;

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
        ALIAS_MAP.put("address", Arrays.asList("地址", "通讯地址", "联系地址", "通信地址", "详细地址"));
        ALIAS_MAP.put("start_date", Arrays.asList("开始日期", "起始日期", "开始时间", "起始时间", "项目起始"));
        ALIAS_MAP.put("end_date", Arrays.asList("结束日期", "截止日期", "结束时间", "截止时间", "项目终止"));
        ALIAS_MAP.put("budget", Arrays.asList("经费", "预算", "资助金额", "项目经费", "总经费", "资助额度"));
        ALIAS_MAP.put("dept_name", Arrays.asList("部门", "院系", "学院", "所属部门", "所属院系"));
        ALIAS_MAP.put("title", Arrays.asList("职称", "职务", "专业技术职称"));
        ALIAS_MAP.put("degree", Arrays.asList("学历", "学位", "最高学历", "最高学位"));
        ALIAS_MAP.put("research_field", Arrays.asList("研究方向", "研究领域", "专业领域", "学科方向"));
        ALIAS_MAP.put("postal_code", Arrays.asList("邮编", "邮政编码"));
        ALIAS_MAP.put("project_type", Arrays.asList("项目类型", "课题类型", "项目类别"));
        ALIAS_MAP.put("project_number", Arrays.asList("项目编号", "课题编号", "合同编号", "编号"));
        ALIAS_MAP.put("project_level", Arrays.asList("项目级别", "课题级别", "级别"));
        ALIAS_MAP.put("gender", Arrays.asList("性别"));
        ALIAS_MAP.put("age", Arrays.asList("年龄"));
        ALIAS_MAP.put("birth_date", Arrays.asList("出生日期", "出生年月", "生日"));
        ALIAS_MAP.put("apply_date", Arrays.asList("申报日期", "申请日期", "填报日期"));
        ALIAS_MAP.put("fax", Arrays.asList("传真", "传真号"));
        ALIAS_MAP.put("keywords", Arrays.asList("关键词", "关键字", "主题词"));
        ALIAS_MAP.put("discipline", Arrays.asList("学科分类", "学科方向", "所属学科"));
        ALIAS_MAP.put("funding_amount", Arrays.asList("资助金额", "拨款金额", "合同金额"));
        ALIAS_MAP.put("contact_person", Arrays.asList("联系人", "联络人"));
        ALIAS_MAP.put("id_card", Arrays.asList("身份证", "身份证号码", "证件号"));
        // 通用地理/数量类字段
        ALIAS_MAP.put("population", Arrays.asList("人口", "总人口", "人口数", "人口数量", "常住人口"));
        ALIAS_MAP.put("country", Arrays.asList("国家", "国", "国别", "国家名称"));
        ALIAS_MAP.put("state_province", Arrays.asList("州", "省", "省份", "自治区", "自治州", "行政区"));
        ALIAS_MAP.put("area", Arrays.asList("面积", "占地面积", "国土面积", "总面积"));
        ALIAS_MAP.put("capital", Arrays.asList("首都", "省会", "首府", "行政中心"));
        ALIAS_MAP.put("gdp", Arrays.asList("GDP", "国内生产总值", "生产总值", "经济总量"));
        ALIAS_MAP.put("language", Arrays.asList("语言", "官方语言", "通用语言", "主要语言"));
        ALIAS_MAP.put("currency", Arrays.asList("货币", "货币单位", "流通货币"));
        ALIAS_MAP.put("region", Arrays.asList("地区", "区域", "所在地区", "行政区划"));
        ALIAS_MAP.put("city", Arrays.asList("城市", "市", "所在城市"));
        ALIAS_MAP.put("description", Arrays.asList("描述", "简介", "概况", "说明", "介绍", "备注"));
        ALIAS_MAP.put("name", Arrays.asList("名称", "姓名", "名字"));
        ALIAS_MAP.put("quantity", Arrays.asList("数量", "个数", "总数", "数目"));
    }

    @Override
    public SourceDocumentEntity uploadAndExtract(MultipartFile file, Long userId) {
        String originalFilename = file.getOriginalFilename();
        String fileType = getFileType(originalFilename);

        // 将文件持久化保存到 data/local-oss/source_documents/ 目录，避免临时文件被系统清理
        Path savedPath;
        String ossKey = "";
        try {
            Path persistDir = java.nio.file.Paths.get("data", "local-oss", "source_documents").toAbsolutePath().normalize();
            Files.createDirectories(persistDir);
            // 确保文件名安全
            String safeName = java.nio.file.Paths.get(originalFilename).getFileName().toString()
                    .replace("\\", "_").replace("/", "_").replace(":", "_");
            savedPath = persistDir.resolve(safeName);
            // 若文件重名，加时间戳后缀
            if (Files.exists(savedPath)) {
                int lastDot = safeName.lastIndexOf('.');
                String nameOnly = lastDot > 0 ? safeName.substring(0, lastDot) : safeName;
                String ext = lastDot > 0 ? safeName.substring(lastDot) : "";
                safeName = nameOnly + "_" + System.currentTimeMillis() + ext;
                savedPath = persistDir.resolve(safeName);
            }
            try (var is = file.getInputStream()) {
                Files.copy(is, savedPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            ossKey = "source_documents/" + safeName;
            log.info("源文件已持久保存: {}", savedPath);
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
                // 等待LLM调用间隔，避免并发调用时触发API限流
                waitForLlmSlot();
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
            } catch (Exception e) {
                log.error("文档提取任务异常: docId={}, error={}", docId, e.getMessage(), e);
                updateDocStatus(docId, "failed", "提取异常: " + e.getMessage());
            }
        });

        return doc;
    }

    /**
     * 限流等待：确保两次LLM调用之间有足够间隔
     */
    private static synchronized void waitForLlmSlot() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastLlmCallTime;
        if (elapsed < LLM_CALL_INTERVAL_MS) {
            try {
                Thread.sleep(LLM_CALL_INTERVAL_MS - elapsed);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastLlmCallTime = System.currentTimeMillis();
    }

    /**
     * 实际执行LLM提取的方法（在线程池中运行）
     * 带重试机制，避免单次API失败导致文档永久停留在parsing状态
     */
    private void doExtraction(long docId, String filePath, String fileType, String originalFilename) {
        int maxRetries = 2;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                String textContent = extractTextContent(filePath, fileType);
                if (textContent == null || textContent.isBlank()) {
                    updateDocStatus(docId, "failed", "文档内容为空，无法提取");
                    return;
                }
                List<ExtractedFieldEntity> fields = callLlmExtract(docId, textContent, originalFilename);

                for (ExtractedFieldEntity field : fields) {
                    standardizeField(field);
                    extractedFieldMapper.insert(field);
                    updateAliasDict(field);
                }

                updateDocStatus(docId, "parsed", fields.size() + "个字段已抽取");
                return; // 抽取成功，退出重试循环
            } catch (Exception e) {
                log.error("文档抽取失败(attempt {}/{}): docId={}, error={}", attempt + 1, maxRetries + 1, docId, e.getMessage(), e);
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(2000L * (attempt + 1)); // 指数退避
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        updateDocStatus(docId, "failed", "提取任务被中断");
                        return;
                    }
                } else {
                    updateDocStatus(docId, "failed", "文档内容提取失败，请检查文件格式是否正确");
                }
            }
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
        int successCount = 0;
        for (Long docId : docIds) {
            try {
                deleteDocument(docId, userId);
                successCount++;
            } catch (Exception e) {
                log.warn("批量删除文档时跳过 docId={}: {}", docId, e.getMessage());
            }
        }
        return successCount > 0;
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
            // 表格: 智能检测key-value行，输出为"键：值"格式以便后续精准提取
            for (XWPFTable table : doc.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    var cells = row.getTableCells();
                    if (cells.isEmpty()) continue;

                    // 检测当前行是否为key-value结构（偶数索引的单元格为标签）
                    boolean isKvRow = cells.size() >= 2 && cells.size() <= 8;
                    if (isKvRow) {
                        int labelCount = 0;
                        int pairCount = (cells.size() + 1) / 2;
                        for (int c = 0; c < cells.size(); c += 2) {
                            String text = cells.get(c).getText().trim();
                            if (!text.isEmpty() && text.length() <= 40 && !text.matches("^[\\d.,%]+$")) {
                                labelCount++;
                            }
                        }
                        isKvRow = labelCount > 0 && labelCount >= pairCount * 0.5;
                    }

                    if (isKvRow) {
                        // key-value行：逐对输出"键：值"格式
                        for (int c = 0; c < cells.size() - 1; c += 2) {
                            String key = cells.get(c).getText().trim();
                            String val = cells.get(c + 1).getText().trim();
                            if (!key.isEmpty() && key.length() <= 40 && !key.matches("^[\\d.,%]+$")) {
                                sb.append(key).append("：").append(val).append("\n");
                            } else {
                                if (!key.isEmpty()) sb.append(key);
                                if (!val.isEmpty()) {
                                    if (!key.isEmpty()) sb.append(" | ");
                                    sb.append(val);
                                }
                                if (!key.isEmpty() || !val.isEmpty()) sb.append("\n");
                            }
                        }
                        if (cells.size() % 2 != 0) {
                            String last = cells.get(cells.size() - 1).getText().trim();
                            if (!last.isEmpty()) sb.append(last).append("\n");
                        }
                    } else {
                        // 普通表格行：按管道符分隔输出
                        StringBuilder rowText = new StringBuilder();
                        cells.forEach(cell -> {
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
        // 截断过长文本，保留更多内容以确保关键字段不丢失
        if (textContent.length() > 30000) {
            textContent = textContent.substring(0, 30000) + "\n...(文本已截断)";
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

        Set<String> seen = new HashSet<>();
        String[] lines = textContent.split("\\r?\\n");
        String activeTableHeader = null; // 跟踪当前Markdown表格的表头行
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i] == null ? "" : lines[i].trim();
            if (line.isEmpty()) {
                activeTableHeader = null; // 空行重置表头
                continue;
            }
            // 移除Markdown标题符号(#)以提取其中的key:value信息
            if (line.startsWith("#")) {
                line = line.replaceAll("^#+\\s*", "");
                if (line.isEmpty()) continue;
            }

            // 规则1: 标准"键:值"或"键：值"格式
            Matcher m = Pattern.compile("^([^:：]{1,40})[:：]\\s*(.+)$").matcher(line);
            if (m.find()) {
                String fieldName = m.group(1).trim();
                String value = m.group(2).trim();
                if (!value.isEmpty() && !seen.contains(fieldName + ":" + value)) {
                    seen.add(fieldName + ":" + value);
                    ExtractedFieldEntity field = new ExtractedFieldEntity();
                    field.setDocId(docId);
                    field.setFieldName(fieldName);
                    field.setFieldValue(value);
                    field.setFieldKey(normalizeKey(fieldName));
                    field.setFieldType(detectFieldType(fieldName, value));
                    field.setAliases(JSON.toJSONString(new String[]{fieldName}));
                    field.setSourceText(line);
                    field.setSourceLocation("line_" + (i + 1));
                    field.setConfidence(new BigDecimal("0.7800"));
                    fields.add(field);
                }
                continue;
            }

            // 规则2: 表格行 "列值 | 列值 | ..." 格式（支持Markdown表格）
            if (line.contains("|")) {
                String[] cells = line.split("\\|");
                // 跳过分隔行（如 | --- | --- |）
                boolean isSeparator = true;
                for (String c : cells) {
                    c = c.trim();
                    if (!c.isEmpty() && !c.matches("^[-:= ]+$")) { isSeparator = false; break; }
                }
                if (isSeparator) {
                    // 分隔行：标记前一行为表头
                    if (i > 0) {
                        String prevL = lines[i - 1] == null ? "" : lines[i - 1].trim();
                        if (prevL.contains("|")) {
                            activeTableHeader = prevL;
                        }
                    }
                    continue;
                }
                
                // 判断当前行是否有有效的表头可用
                String headerLine = activeTableHeader;
                if (headerLine == null && cells.length >= 2 && i > 0) {
                    // 没有分隔行的简单表格：前一行就是表头
                    String prevLine = lines[i - 1] == null ? "" : lines[i - 1].trim();
                    if (prevLine.contains("|")) {
                        boolean prevIsSep = true;
                        for (String c : prevLine.split("\\|")) {
                            c = c.trim();
                            if (!c.isEmpty() && !c.matches("^[-:= ]+$")) { prevIsSep = false; break; }
                        }
                        if (!prevIsSep) {
                            headerLine = prevLine;
                        }
                    }
                }

                if (headerLine != null && cells.length >= 2) {
                    String[] headers = headerLine.split("\\|");
                    for (int j = 0; j < Math.min(headers.length, cells.length); j++) {
                        String header = headers[j].trim();
                        String cellVal = cells[j].trim();
                        if (!header.isEmpty() && !cellVal.isEmpty() && !cellVal.matches("^[-=]+$")
                                && !seen.contains(header + ":" + cellVal)) {
                            seen.add(header + ":" + cellVal);
                            ExtractedFieldEntity field = new ExtractedFieldEntity();
                            field.setDocId(docId);
                            field.setFieldName(header);
                            field.setFieldValue(cellVal);
                            field.setFieldKey(normalizeKey(header));
                            field.setFieldType(detectFieldType(header, cellVal));
                            field.setAliases(JSON.toJSONString(new String[]{header}));
                            field.setSourceText(headerLine + " -> " + line);
                            field.setSourceLocation("table_row_" + (i + 1));
                            field.setConfidence(new BigDecimal("0.7500"));
                            fields.add(field);
                        }
                    }
                } else {
                    // 没有表头的 | 行：跳过或当作非表格数据
                    activeTableHeader = null;
                }
            } else {
                // 非表格行：重置活跃表头
                activeTableHeader = null;
            }

            // 规则3: 提取行内实体（手机号、邮箱、身份证）
            Matcher phoneMatcher = Pattern.compile("(1[3-9]\\d{9})").matcher(line);
            while (phoneMatcher.find()) {
                String phone = phoneMatcher.group(1);
                if (!seen.contains("phone:" + phone)) {
                    seen.add("phone:" + phone);
                    ExtractedFieldEntity field = new ExtractedFieldEntity();
                    field.setDocId(docId);
                    field.setFieldName("联系电话");
                    field.setFieldValue(phone);
                    field.setFieldKey("phone");
                    field.setFieldType("phone");
                    field.setAliases(JSON.toJSONString(new String[]{"联系电话", "手机号"}));
                    field.setSourceText(line);
                    field.setSourceLocation("line_" + (i + 1));
                    field.setConfidence(new BigDecimal("0.8500"));
                    fields.add(field);
                }
            }

            Matcher emailMatcher = Pattern.compile("([\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,})").matcher(line);
            while (emailMatcher.find()) {
                String email = emailMatcher.group(1);
                if (!seen.contains("email:" + email)) {
                    seen.add("email:" + email);
                    ExtractedFieldEntity field = new ExtractedFieldEntity();
                    field.setDocId(docId);
                    field.setFieldName("电子邮箱");
                    field.setFieldValue(email);
                    field.setFieldKey("email");
                    field.setFieldType("email");
                    field.setAliases(JSON.toJSONString(new String[]{"电子邮箱", "邮箱", "Email"}));
                    field.setSourceText(line);
                    field.setSourceLocation("line_" + (i + 1));
                    field.setConfidence(new BigDecimal("0.9000"));
                    fields.add(field);
                }
            }

            Matcher idMatcher = Pattern.compile("(\\d{17}[\\dXx])").matcher(line);
            while (idMatcher.find()) {
                String idNum = idMatcher.group(1);
                if (!seen.contains("id:" + idNum)) {
                    seen.add("id:" + idNum);
                    ExtractedFieldEntity field = new ExtractedFieldEntity();
                    field.setDocId(docId);
                    field.setFieldName("身份证号");
                    field.setFieldValue(idNum);
                    field.setFieldKey("id_number");
                    field.setFieldType("id_number");
                    field.setAliases(JSON.toJSONString(new String[]{"身份证号", "身份证号码", "证件号码"}));
                    field.setSourceText(line);
                    field.setSourceLocation("line_" + (i + 1));
                    field.setConfidence(new BigDecimal("0.8500"));
                    fields.add(field);
                }
            }
        }
        return fields;
    }

    private String normalizeKey(String fieldName) {
        if (fieldName == null) return "";
        String cleaned = fieldName.trim();
        // Use ALIAS_MAP for comprehensive normalization
        for (Map.Entry<String, List<String>> entry : ALIAS_MAP.entrySet()) {
            for (String alias : entry.getValue()) {
                if (cleaned.equals(alias) || cleaned.contains(alias) || alias.contains(cleaned)) {
                    return entry.getKey();
                }
            }
        }
        return cleaned.toLowerCase(Locale.ROOT).replaceAll("\\s+", "_");
    }

    private String detectFieldType(String fieldName, String value) {
        String text = (fieldName + " " + value).toLowerCase(Locale.ROOT);
        // Phone detection
        if (text.matches(".*(电话|手机|联系方式|phone|tel|mobile).*") || value.matches("^1[3-9]\\d{9}$") || value.matches("^0\\d{2,3}-?\\d{7,8}$")) {
            return "phone";
        }
        // Email detection
        if (text.matches(".*(邮箱|email|e-mail).*") || value.matches("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$")) {
            return "email";
        }
        // Date detection
        if (text.matches(".*(日期|时间|date|年月|起始|截止|开始|结束).*") || value.matches(".*\\d{4}[年/-]\\d{1,2}[月/-].*")) {
            return "date";
        }
        // Person detection
        if (text.matches(".*(负责人|主持人|联系人|姓名|person|name|作者).*")) {
            return "person";
        }
        // Organization detection
        if (text.matches(".*(单位|大学|公司|机构|学院|学校|org|院系|部门|研究所|实验室).*")) {
            return "org";
        }
        // ID number detection
        if (text.matches(".*(身份证|证件号|id).*") || value.matches("^\\d{17}[\\dXx]$")) {
            return "id_number";
        }
        // Number/budget/quantity detection
        if (text.matches(".*(经费|预算|金额|资助|万元|budget|amount|人口|面积|数量|总数|gdp|生产总值).*") || value.matches("^-?\\d+(\\.\\d+)?$") || value.matches("^\\d{1,3}(,\\d{3})*(\\.\\d+)?$")) {
            return "number";
        }
        return "text";
    }

    private String buildExtractionPrompt(String textContent, String fileName) {
        return """
                你是一个专业的文档信息抽取助手。请从以下文档内容中完整抽取所有关键字段信息。
                
                文件名：%s
                
                文档内容：
                %s
                
                请严格按照以下JSON格式输出抽取结果，不要输出任何其他内容：
                {
                  "doc_title": "文档标题",
                  "doc_type": "文档类型",
                  "fields": [
                    {
                      "field_key": "标准化字段键（英文下划线格式）",
                      "field_name": "原始字段名",
                      "field_value": "字段值（完整，不截断）",
                      "field_type": "text|date|number|phone|org|person|enum",
                      "confidence": 0.95
                    }
                  ]
                }
                
                抽取要求：
                1. 完整抽取文档中所有信息字段，不遗漏
                2. 重点抽取以下字段类别：
                   - 项目/文档信息、人员信息、机构信息、联系方式
                   - 时间日期、经费金额、地理信息（国家/省/城市）
                   - 数量信息（人口、面积、GDP等）
                3. 对于重复出现的同类字段（如多个省份、多个国家），每个值都应独立抽取为单独的field对象
                4. 表格/列表数据：每行每个字段独立提取，不要合并多个值
                5. field_key使用英文下划线格式（如country、population、gdp_per_capita）
                6. 不要编造信息，字段值必须来自原文
                7. 为减少输出量，省略source_text和aliases字段
                """.formatted(fileName, textContent);
    }

    private List<ExtractedFieldEntity> parseExtractionResponse(Long docId, String response) {
        List<ExtractedFieldEntity> fields = new ArrayList<>();

        try {
            // 尝试从响应中提取JSON
            String jsonStr = extractJsonFromResponse(response);
            JSONObject result = null;
            
            // 策略1: 直接解析
            try {
                result = JSON.parseObject(jsonStr);
            } catch (Exception e1) {
                log.warn("JSON直接解析失败({}), 尝试修复...", e1.getMessage().substring(0, Math.min(100, e1.getMessage().length())));
                // 策略2: 替换所有控制字符
                String cleaned = jsonStr.replaceAll("[\\x00-\\x1f&&[^\\n\\r\\t]]", "");
                try {
                    result = JSON.parseObject(cleaned);
                } catch (Exception e2) {
                    // 策略3: 只提取第一个完整的JSON对象 {"fields":[...]}
                    int fieldsStart = cleaned.indexOf("\"fields\"");
                    if (fieldsStart > 0) {
                        int arrStart = cleaned.indexOf('[', fieldsStart);
                        if (arrStart > 0) {
                            // 找到对应的]
                            int depth = 0;
                            int arrEnd = -1;
                            for (int i = arrStart; i < cleaned.length(); i++) {
                                char c = cleaned.charAt(i);
                                if (c == '[') depth++;
                                else if (c == ']') { depth--; if (depth == 0) { arrEnd = i; break; } }
                            }
                            if (arrEnd > 0) {
                                String arrStr = cleaned.substring(arrStart, arrEnd + 1);
                                result = new JSONObject();
                                result.put("fields", JSON.parseArray(arrStr));
                            }
                        }
                    }
                }
            }
            
            if (result != null) {
                JSONArray fieldsArray = result.getJSONArray("fields");

                if (fieldsArray != null) {
                    for (int i = 0; i < fieldsArray.size(); i++) {
                        try {
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
                        } catch (Exception itemEx) {
                            log.warn("解析第{}个字段失败: {}", i, itemEx.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("解析LLM抽取结果失败: {}", e.getMessage());
        }
        
        if (fields.isEmpty()) {
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
        response = response.trim();
        
        // 修复LLM常见的JSON转义问题
        // 1. 修复未转义的反斜杠（不在合法转义序列中的\）
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < response.length(); i++) {
            char c = response.charAt(i);
            if (c == '\\' && i + 1 < response.length()) {
                char next = response.charAt(i + 1);
                if (next == '"' || next == '\\' || next == '/' || next == 'b'
                        || next == 'f' || next == 'n' || next == 'r' || next == 't' || next == 'u') {
                    sb.append(c); // 合法转义，保留
                } else {
                    sb.append("\\\\"); // 非法转义，双重转义
                }
            } else {
                sb.append(c);
            }
        }
        response = sb.toString();
        
        // 2. 修复末尾截断的JSON：如果响应被截断，尝试补齐
        if (!response.endsWith("}") && !response.endsWith("]")) {
            // 尝试找到最后一个完整的JSON对象
            int lastBrace = response.lastIndexOf('}');
            if (lastBrace > 0) {
                response = response.substring(0, lastBrace + 1);
                // 检查是否需要补齐外层大括号
                long openBraces = response.chars().filter(ch -> ch == '{').count();
                long closeBraces = response.chars().filter(ch -> ch == '}').count();
                while (closeBraces < openBraces) {
                    response += "}";
                    closeBraces++;
                }
                // 检查数组括号
                long openBrackets = response.chars().filter(ch -> ch == '[').count();
                long closeBrackets = response.chars().filter(ch -> ch == ']').count();
                while (closeBrackets < openBrackets) {
                    response += "]";
                    closeBrackets++;
                }
            }
        }
        
        return response;
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
