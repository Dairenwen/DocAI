package com.docai.ai.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.docai.ai.entity.*;
import com.docai.ai.mapper.*;
import com.docai.ai.service.LlmService;
import com.docai.ai.service.TemplateFillService;
import com.docai.common.service.OssService;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TemplateFillServiceImpl implements TemplateFillService {

    @Autowired
    private TemplateFileMapper templateFileMapper;
    @Autowired
    private TemplateSlotMapper templateSlotMapper;
    @Autowired
    private FillDecisionMapper fillDecisionMapper;
    @Autowired
    private FillAuditLogMapper fillAuditLogMapper;
    @Autowired
    private ExtractedFieldMapper extractedFieldMapper;
    @Autowired
    private FieldAliasDictMapper fieldAliasDictMapper;
    @Autowired
    private SourceDocumentMapper sourceDocumentMapper;

    @Autowired
    private LlmService llmService;

    @Autowired
    private OssService ossService;

    @Override
    public TemplateFileEntity uploadTemplate(MultipartFile file, Long userId) {
        String originalFilename = file.getOriginalFilename();
        String fileType = getFileType(originalFilename);

        if (!"xlsx".equals(fileType) && !"docx".equals(fileType)) {
            throw new RuntimeException("仅支持xlsx和docx格式的模板文件");
        }

        String ossKey = "";
        try {
            String fileUrl = ossService.uploadFile(file, "template_files/");
            ossKey = getOssKey(fileUrl);
        } catch (Exception ex) {
            log.warn("模板上传OSS失败，继续使用本地路径: {}", ex.getMessage());
        }

        Path tempDir;
        Path savedPath;
        try {
            tempDir = Files.createTempDirectory("docai_template_");
            savedPath = tempDir.resolve(originalFilename);
            file.transferTo(savedPath.toFile());
        } catch (IOException e) {
            throw new RuntimeException("文件保存失败: " + e.getMessage());
        }

        TemplateFileEntity tpl = new TemplateFileEntity();
        tpl.setUserId(userId);
        tpl.setFileName(originalFilename);
        tpl.setTemplateType(fileType);
        tpl.setStoragePath(savedPath.toString());
        tpl.setOssKey(ossKey);
        tpl.setFileSize(file.getSize());
        tpl.setParseStatus("uploaded");

        templateFileMapper.insert(tpl);

        return tpl;
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
    public List<TemplateSlotEntity> parseSlots(Long templateId) {
        TemplateFileEntity tpl = templateFileMapper.selectById(templateId);
        if (tpl == null) throw new RuntimeException("模板不存在");

        // 先删除旧的槽位
        templateSlotMapper.delete(
                new LambdaQueryWrapper<TemplateSlotEntity>().eq(TemplateSlotEntity::getTemplateId, templateId)
        );

        List<TemplateSlotEntity> slots;
        try {
            if ("xlsx".equals(tpl.getTemplateType())) {
                slots = parseExcelSlots(templateId, tpl.getStoragePath());
            } else {
                slots = parseWordSlots(templateId, tpl.getStoragePath());
            }

            for (TemplateSlotEntity slot : slots) {
                templateSlotMapper.insert(slot);
            }

            tpl.setParseStatus("parsed");
            tpl.setSlotCount(slots.size());
            templateFileMapper.updateById(tpl);
        } catch (Exception e) {
            log.error("模板解析失败: {}", e.getMessage(), e);
            tpl.setParseStatus("failed");
            templateFileMapper.updateById(tpl);
            throw new RuntimeException("模板解析失败: " + e.getMessage());
        }

        return slots;
    }

    @Override
    public Map<String, Object> autoFill(Long templateId, List<Long> docIds, Long userId) {
        TemplateFileEntity tpl = templateFileMapper.selectById(templateId);
        if (tpl == null) throw new RuntimeException("模板不存在");

        // 获取槽位
        List<TemplateSlotEntity> slots = templateSlotMapper.selectList(
                new LambdaQueryWrapper<TemplateSlotEntity>().eq(TemplateSlotEntity::getTemplateId, templateId)
        );
        if (slots.isEmpty()) throw new RuntimeException("模板未解析槽位，请先解析");

        // 获取所有候选字段
        List<ExtractedFieldEntity> allFields;
        if (docIds != null && !docIds.isEmpty()) {
            allFields = extractedFieldMapper.selectList(
                    new LambdaQueryWrapper<ExtractedFieldEntity>().in(ExtractedFieldEntity::getDocId, docIds)
            );
        } else {
            allFields = extractedFieldMapper.selectList(null);
        }

        // 获取别名词典
        List<FieldAliasDictEntity> aliasDictList = fieldAliasDictMapper.selectList(null);
        Map<String, List<String>> aliasDict = new HashMap<>();
        for (FieldAliasDictEntity a : aliasDictList) {
            aliasDict.computeIfAbsent(a.getStandardKey(), k -> new ArrayList<>()).add(a.getAliasName());
        }

        // 对每个槽位进行候选召回和决策
        List<FillDecisionEntity> decisions = new ArrayList<>();
        List<FillAuditLogEntity> auditLogs = new ArrayList<>();
        int filledCount = 0;
        int blankCount = 0;

        for (TemplateSlotEntity slot : slots) {
            // 阶段4：候选召回
            List<CandidateResult> candidates = recallCandidates(slot, allFields, aliasDict);

            // 阶段5：难例判定
            FillDecisionEntity decision = makeDecision(slot, candidates);

            // 阶段6：置信度融合
            applyConfidenceThreshold(decision);

            decision.setTemplateId(templateId);
            fillDecisionMapper.insert(decision);
            decisions.add(decision);

            // 计数
            if (decision.getFinalValue() != null && !decision.getFinalValue().isEmpty()) {
                filledCount++;
            } else {
                blankCount++;
            }

            // 阶段8：审计日志
            FillAuditLogEntity auditLog = createAuditLog(templateId, slot, decision, candidates);
            fillAuditLogMapper.insert(auditLog);
            auditLogs.add(auditLog);
        }

        // 阶段7：模板写回
        String outputPath = null;
        try {
            outputPath = writeBack(tpl, slots, decisions);
        } catch (Exception e) {
            log.error("模板写回失败: {}", e.getMessage(), e);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("templateId", templateId);
        result.put("outputFile", outputPath);
        result.put("filledCount", filledCount);
        result.put("blankCount", blankCount);
        result.put("totalSlots", slots.size());
        result.put("auditLogs", auditLogs);

        return result;
    }

    @Override
    public List<FillAuditLogEntity> getAuditLog(Long templateId) {
        return fillAuditLogMapper.selectList(
                new LambdaQueryWrapper<FillAuditLogEntity>().eq(FillAuditLogEntity::getTemplateId, templateId)
                        .orderByAsc(FillAuditLogEntity::getId)
        );
    }

    @Override
    public List<FillDecisionEntity> getDecisions(Long templateId) {
        return fillDecisionMapper.selectList(
                new LambdaQueryWrapper<FillDecisionEntity>().eq(FillDecisionEntity::getTemplateId, templateId)
        );
    }

    @Override
    public List<TemplateFileEntity> getUserTemplates(Long userId) {
        return templateFileMapper.selectList(
                new LambdaQueryWrapper<TemplateFileEntity>().eq(TemplateFileEntity::getUserId, userId)
                        .orderByDesc(TemplateFileEntity::getCreatedAt)
        );
    }

    @Override
    public String downloadResult(Long templateId) {
        TemplateFileEntity tpl = templateFileMapper.selectById(templateId);
        if (tpl == null || tpl.getOutputPath() == null) {
            throw new RuntimeException("模板结果文件不存在");
        }
        return tpl.getOutputPath();
    }

    // ===== 阶段3：模板槽位解析 =====

    private List<TemplateSlotEntity> parseExcelSlots(Long templateId, String filePath) throws Exception {
        List<TemplateSlotEntity> slots = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(filePath);
             XSSFWorkbook workbook = new XSSFWorkbook(fis)) {

            Set<String> mergedMainCells = new HashSet<>();
            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                XSSFSheet sheet = workbook.getSheetAt(s);
                // 记录合并单元格
                for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
                    CellRangeAddress range = sheet.getMergedRegion(i);
                    mergedMainCells.add(sheet.getSheetName() + "!" + getCellRef(range.getFirstRow(), range.getFirstColumn()));
                }
            }

            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                XSSFSheet sheet = workbook.getSheetAt(s);
                String sheetName = sheet.getSheetName();

                for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                    XSSFRow row = sheet.getRow(r);
                    if (row == null) continue;

                    for (int c = 0; c < row.getLastCellNum(); c++) {
                        XSSFCell cell = row.getCell(c);
                        if (cell == null) continue;

                        String cellValue = getCellStringValue(cell);
                        if (cellValue.isEmpty()) continue;

                        // 判断是否为标签（非空、长度适中、不是大段文字）
                        if (isLabelCell(cellValue)) {
                            // 规则1: 右边为空 → 槽位在右边
                            XSSFCell rightCell = row.getCell(c + 1);
                            if (rightCell != null && getCellStringValue(rightCell).isEmpty()) {
                                TemplateSlotEntity slot = buildSlot(templateId, sheetName, r, c + 1, cellValue, guessType(cellValue));
                                slots.add(slot);
                                continue;
                            }
                            if (rightCell == null && c + 1 < row.getLastCellNum()) {
                                TemplateSlotEntity slot = buildSlot(templateId, sheetName, r, c + 1, cellValue, guessType(cellValue));
                                slots.add(slot);
                                continue;
                            }

                            // 规则2: 下面为空 → 槽位在下面
                            XSSFRow nextRow = sheet.getRow(r + 1);
                            if (nextRow != null) {
                                XSSFCell belowCell = nextRow.getCell(c);
                                if (belowCell != null && getCellStringValue(belowCell).isEmpty()) {
                                    TemplateSlotEntity slot = buildSlot(templateId, sheetName, r + 1, c, cellValue, guessType(cellValue));
                                    slots.add(slot);
                                }
                            }

                            // 规则3: 包含"：____"或": ____"
                            if (cellValue.matches(".*[：:]\\s*[_\\s]*$")) {
                                TemplateSlotEntity slot = buildSlot(templateId, sheetName, r, c, cellValue.replaceAll("[：:].*", "").trim(), guessType(cellValue));
                                slot.setSlotType("inline");
                                slots.add(slot);
                            }
                        }
                    }
                }
            }
        }
        return slots;
    }

    private List<TemplateSlotEntity> parseWordSlots(Long templateId, String filePath) throws Exception {
        List<TemplateSlotEntity> slots = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(filePath);
             XWPFDocument doc = new XWPFDocument(fis)) {

            List<XWPFTable> tables = doc.getTables();
            for (int t = 0; t < tables.size(); t++) {
                XWPFTable table = tables.get(t);
                List<XWPFTableRow> rows = table.getRows();
                for (int r = 0; r < rows.size(); r++) {
                    XWPFTableRow row = rows.get(r);
                    var cells = row.getTableCells();
                    for (int c = 0; c < cells.size(); c++) {
                        String cellText = cells.get(c).getText().trim();
                        if (cellText.isEmpty()) continue;

                        if (isLabelCell(cellText)) {
                            // 右边空白
                            if (c + 1 < cells.size() && cells.get(c + 1).getText().trim().isEmpty()) {
                                TemplateSlotEntity slot = new TemplateSlotEntity();
                                slot.setTemplateId(templateId);
                                slot.setLabel(cellText);
                                slot.setContext("Table" + t);
                                slot.setPosition("{\"table\":" + t + ",\"row\":" + r + ",\"col\":" + (c + 1) + "}");
                                slot.setExpectedType(guessType(cellText));
                                slot.setRequired(true);
                                slot.setSlotType("right_blank");
                                slots.add(slot);
                                continue;
                            }

                            // 下方空白
                            if (r + 1 < rows.size()) {
                                var nextRow = rows.get(r + 1);
                                if (c < nextRow.getTableCells().size() && nextRow.getTableCells().get(c).getText().trim().isEmpty()) {
                                    TemplateSlotEntity slot = new TemplateSlotEntity();
                                    slot.setTemplateId(templateId);
                                    slot.setLabel(cellText);
                                    slot.setContext("Table" + t);
                                    slot.setPosition("{\"table\":" + t + ",\"row\":" + (r + 1) + ",\"col\":" + c + "}");
                                    slot.setExpectedType(guessType(cellText));
                                    slot.setRequired(true);
                                    slot.setSlotType("below_blank");
                                    slots.add(slot);
                                }
                            }

                            // 内联"字段名：____"
                            if (cellText.matches(".*[：:]\\s*[_\\s]*$")) {
                                TemplateSlotEntity slot = new TemplateSlotEntity();
                                slot.setTemplateId(templateId);
                                slot.setLabel(cellText.replaceAll("[：:].*", "").trim());
                                slot.setContext("Table" + t);
                                slot.setPosition("{\"table\":" + t + ",\"row\":" + r + ",\"col\":" + c + "}");
                                slot.setExpectedType(guessType(cellText));
                                slot.setRequired(true);
                                slot.setSlotType("inline");
                                slots.add(slot);
                            }
                        }
                    }
                }
            }
        }
        return slots;
    }

    // ===== 阶段4：候选召回 =====

    private List<CandidateResult> recallCandidates(TemplateSlotEntity slot, List<ExtractedFieldEntity> allFields,
                                                    Map<String, List<String>> aliasDict) {
        List<CandidateResult> candidates = new ArrayList<>();
        String slotLabel = slot.getLabel().trim();
        String slotLabelStd = normalizeLabel(slotLabel);

        // 统计多文档投票
        Map<String, Integer> valueVotes = new HashMap<>();
        for (ExtractedFieldEntity f : allFields) {
            if (f.getFieldValue() != null) {
                valueVotes.merge(f.getFieldValue().trim(), 1, Integer::sum);
            }
        }
        int maxVote = valueVotes.values().stream().mapToInt(Integer::intValue).max().orElse(1);

        for (ExtractedFieldEntity field : allFields) {
            if (field.getFieldValue() == null || field.getFieldValue().trim().isEmpty()) continue;

            double aliasScore = 0.0;
            double typeScore = 0.0;
            double voteScore = 0.0;
            double contextScore = 0.0;
            double confScore = field.getConfidence() != null ? field.getConfidence().doubleValue() : 0.5;

            // 1. 别名匹配
            if (slotLabel.equals(field.getFieldName()) || slotLabelStd.equals(field.getFieldKey())) {
                aliasScore = 1.0;
            } else {
                // 检查别名词典
                for (Map.Entry<String, List<String>> entry : aliasDict.entrySet()) {
                    if (entry.getValue().contains(slotLabel) && entry.getKey().equals(field.getFieldKey())) {
                        aliasScore = 0.9;
                        break;
                    }
                    if (entry.getValue().stream().anyMatch(a -> slotLabel.contains(a) || a.contains(slotLabel))
                            && entry.getKey().equals(field.getFieldKey())) {
                        aliasScore = 0.7;
                        break;
                    }
                }
                // 模糊匹配
                if (aliasScore == 0.0 && field.getFieldName() != null) {
                    if (slotLabel.contains(field.getFieldName()) || field.getFieldName().contains(slotLabel)) {
                        aliasScore = 0.5;
                    }
                }
            }

            // 2. 类型匹配
            if (slot.getExpectedType() != null && field.getFieldType() != null) {
                if (slot.getExpectedType().equals(field.getFieldType())) {
                    typeScore = 1.0;
                } else if ("text".equals(slot.getExpectedType())) {
                    typeScore = 0.3; // text类型兼容性较高
                }
            } else {
                typeScore = 0.3; // 类型未知时给低分
            }

            // 3. 多文档投票
            int votes = valueVotes.getOrDefault(field.getFieldValue().trim(), 0);
            voteScore = (double) votes / maxVote;

            // 4. 上下文关键字匹配
            if (slot.getContext() != null && field.getSourceText() != null) {
                String ctx = slot.getContext().toLowerCase();
                String src = field.getSourceText().toLowerCase();
                if (ctx.contains(field.getFieldKey() != null ? field.getFieldKey() : "") || src.contains(slotLabel.toLowerCase())) {
                    contextScore = 0.8;
                }
            }

            // 计算综合得分
            double score = 0.40 * aliasScore + 0.20 * typeScore + 0.20 * voteScore + 0.10 * contextScore + 0.10 * confScore;

            if (score > 0.1) { // 过低分数不纳入候选
                CandidateResult cr = new CandidateResult();
                cr.field = field;
                cr.score = score;
                cr.aliasScore = aliasScore;
                cr.typeScore = typeScore;
                cr.voteScore = voteScore;
                cr.contextScore = contextScore;
                cr.confScore = confScore;
                candidates.add(cr);
            }
        }

        // 排序取Top-5
        candidates.sort((a, b) -> Double.compare(b.score, a.score));
        if (candidates.size() > 5) {
            candidates = candidates.subList(0, 5);
        }

        return candidates;
    }

    // ===== 阶段5：难例判定 =====

    private FillDecisionEntity makeDecision(TemplateSlotEntity slot, List<CandidateResult> candidates) {
        FillDecisionEntity decision = new FillDecisionEntity();
        decision.setSlotId(slot.getId().toString());
        decision.setSlotLabel(slot.getLabel());

        if (candidates.isEmpty()) {
            decision.setFinalValue("");
            decision.setFinalConfidence(BigDecimal.ZERO);
            decision.setDecisionMode("fallback_blank");
            decision.setReason("无候选字段");
            return decision;
        }

        CandidateResult top1 = candidates.get(0);
        boolean needLlm = false;

        // 判定是否需要调用LLM
        if (candidates.size() >= 2) {
            CandidateResult top2 = candidates.get(1);
            // 条件1: Top1和Top2分差 < 0.15
            if (top1.score - top2.score < 0.15) needLlm = true;
            // 条件3: 候选值类型冲突
            if (top1.field.getFieldType() != null && top2.field.getFieldType() != null
                    && !top1.field.getFieldType().equals(top2.field.getFieldType())) needLlm = true;
            // 条件4: 多文档不同值
            if (!Objects.equals(top1.field.getFieldValue(), top2.field.getFieldValue())
                    && top1.score - top2.score < 0.20) needLlm = true;
        }
        // 条件2: Top1分数 < 0.75
        if (top1.score < 0.75) needLlm = true;
        // 条件5: 别名词典没命中
        if (top1.aliasScore < 0.5) needLlm = true;

        if (needLlm) {
            try {
                LlmJudgment judgment = callLlmJudge(slot, candidates);
                if (judgment != null && judgment.selectedValue != null) {
                    // 融合：0.70 * candidate_score + 0.30 * model_confidence
                    double finalConf = 0.70 * top1.score + 0.30 * judgment.modelConfidence;
                    decision.setFinalValue(judgment.selectedValue);
                    decision.setFinalFieldId(judgment.selectedFieldId);
                    decision.setFinalConfidence(BigDecimal.valueOf(finalConf).setScale(4, RoundingMode.HALF_UP));
                    decision.setDecisionMode("rule_plus_llm");
                    decision.setReason(judgment.reason);
                    return decision;
                }
            } catch (Exception e) {
                log.warn("LLM判定失败，回退规则决策: {}", e.getMessage());
            }
        }

        // 纯规则决策
        decision.setFinalValue(top1.field.getFieldValue());
        decision.setFinalFieldId(top1.field.getId().toString());
        decision.setFinalConfidence(BigDecimal.valueOf(top1.score).setScale(4, RoundingMode.HALF_UP));
        decision.setDecisionMode("rule_only");
        decision.setReason("规则Top1候选: " + top1.field.getFieldName() + " (score=" + String.format("%.2f", top1.score) + ")");

        return decision;
    }

    // ===== 阶段6：置信度阈值 =====

    private void applyConfidenceThreshold(FillDecisionEntity decision) {
        double conf = decision.getFinalConfidence() != null ? decision.getFinalConfidence().doubleValue() : 0;
        if (conf < 0.70) {
            decision.setFinalValue(""); // 拒填
            decision.setDecisionMode("fallback_blank");
            decision.setReason(decision.getReason() + " [置信度=" + String.format("%.2f", conf) + " < 0.70, 拒填]");
        } else if (conf < 0.85) {
            decision.setReason(decision.getReason() + " [置信度=" + String.format("%.2f", conf) + ", 建议人工复核]");
        }
    }

    // ===== LLM难例判定 =====

    private LlmJudgment callLlmJudge(TemplateSlotEntity slot, List<CandidateResult> candidates) {
        StringBuilder candidatesJson = new StringBuilder("[");
        for (int i = 0; i < candidates.size(); i++) {
            CandidateResult cr = candidates.get(i);
            if (i > 0) candidatesJson.append(",");
            candidatesJson.append("{")
                    .append("\"fieldId\":\"").append(cr.field.getId()).append("\",")
                    .append("\"fieldKey\":\"").append(cr.field.getFieldKey()).append("\",")
                    .append("\"fieldValue\":\"").append(escapeJson(cr.field.getFieldValue())).append("\",")
                    .append("\"sourceText\":\"").append(escapeJson(cr.field.getSourceText() != null ? cr.field.getSourceText() : "")).append("\",")
                    .append("\"score\":").append(String.format("%.4f", cr.score))
                    .append("}");
        }
        candidatesJson.append("]");

        String prompt = """
                你是一个智能文档填表判定助手。请根据模板槽位信息和候选字段，选出最合适的填写值。
                
                模板槽位：
                - 标签：%s
                - 上下文：%s
                
                候选字段：
                %s
                
                请从候选字段中选择最合适的一个，严格按照以下JSON格式输出，不要输出其他内容：
                {"selectedFieldId":"字段ID","selectedValue":"选中的值","modelConfidence":0.95,"reason":"选择原因"}
                
                规则：
                1. 只能从候选字段中选择，不能自己编造值
                2. 如果所有候选都不合适，modelConfidence设为0
                3. reason必须说明选择理由
                """.formatted(slot.getLabel(), slot.getContext() != null ? slot.getContext() : "", candidatesJson.toString());

        String response;
        try {
            response = llmService.generateText(prompt);
        } catch (Exception e) {
            log.error("LLM判定调用失败: {}", e.getMessage());
            return null;
        }

        try {
            String jsonStr = extractJsonFromResponse(response);
            JSONObject obj = JSON.parseObject(jsonStr);
            LlmJudgment judgment = new LlmJudgment();
            judgment.selectedFieldId = obj.getString("selectedFieldId");
            judgment.selectedValue = obj.getString("selectedValue");
            judgment.modelConfidence = obj.getDoubleValue("modelConfidence");
            judgment.reason = obj.getString("reason");
            return judgment;
        } catch (Exception e) {
            log.warn("解析LLM判定结果失败: {}", e.getMessage());
            return null;
        }
    }

    // ===== 阶段7：模板写回 =====

    private String writeBack(TemplateFileEntity tpl, List<TemplateSlotEntity> slots, List<FillDecisionEntity> decisions) throws Exception {
        // 构建slotLabel -> decision的映射
        Map<String, FillDecisionEntity> decisionMap = new HashMap<>();
        for (FillDecisionEntity d : decisions) {
            decisionMap.put(d.getSlotLabel(), d);
        }

        String outputPath;
        if ("xlsx".equals(tpl.getTemplateType())) {
            outputPath = writeBackExcel(tpl.getStoragePath(), slots, decisionMap);
        } else {
            outputPath = writeBackWord(tpl.getStoragePath(), slots, decisionMap);
        }

        tpl.setOutputPath(outputPath);
        templateFileMapper.updateById(tpl);

        return outputPath;
    }

    private String writeBackExcel(String templatePath, List<TemplateSlotEntity> slots, Map<String, FillDecisionEntity> decisionMap) throws Exception {
        String outputPath = templatePath.replace(".", "_filled.");
        try (FileInputStream fis = new FileInputStream(templatePath);
             XSSFWorkbook workbook = new XSSFWorkbook(fis)) {

            for (TemplateSlotEntity slot : slots) {
                FillDecisionEntity decision = decisionMap.get(slot.getLabel());
                if (decision == null || decision.getFinalValue() == null || decision.getFinalValue().isEmpty()) continue;

                JSONObject pos = JSON.parseObject(slot.getPosition());
                String sheetName = pos.getString("sheet");
                int row = pos.getIntValue("row");
                int col = pos.getIntValue("col");

                XSSFSheet sheet = workbook.getSheet(sheetName);
                if (sheet == null) continue;

                XSSFRow xrow = sheet.getRow(row);
                if (xrow == null) xrow = sheet.createRow(row);

                XSSFCell cell = xrow.getCell(col);
                if (cell == null) cell = xrow.createCell(col);

                // 不覆盖公式格
                if (cell.getCellType() == CellType.FORMULA) continue;

                if ("inline".equals(slot.getSlotType())) {
                    // 内联模式：在现有文本后追加值
                    String existing = getCellStringValue(cell);
                    cell.setCellValue(existing + decision.getFinalValue());
                } else {
                    cell.setCellValue(decision.getFinalValue());
                }
            }

            try (FileOutputStream fos = new FileOutputStream(outputPath)) {
                workbook.write(fos);
            }
        }
        return outputPath;
    }

    private String writeBackWord(String templatePath, List<TemplateSlotEntity> slots, Map<String, FillDecisionEntity> decisionMap) throws Exception {
        String outputPath = templatePath.replace(".", "_filled.");
        try (FileInputStream fis = new FileInputStream(templatePath);
             XWPFDocument doc = new XWPFDocument(fis)) {

            for (TemplateSlotEntity slot : slots) {
                FillDecisionEntity decision = decisionMap.get(slot.getLabel());
                if (decision == null || decision.getFinalValue() == null || decision.getFinalValue().isEmpty()) continue;

                JSONObject pos = JSON.parseObject(slot.getPosition());
                int tableIdx = pos.getIntValue("table");
                int rowIdx = pos.getIntValue("row");
                int colIdx = pos.getIntValue("col");

                List<XWPFTable> tables = doc.getTables();
                if (tableIdx >= tables.size()) continue;

                XWPFTable table = tables.get(tableIdx);
                List<XWPFTableRow> rows = table.getRows();
                if (rowIdx >= rows.size()) continue;

                var cells = rows.get(rowIdx).getTableCells();
                if (colIdx >= cells.size()) continue;

                var targetCell = cells.get(colIdx);

                if ("inline".equals(slot.getSlotType())) {
                    // 内联替换：在冒号后添加值
                    String text = targetCell.getText();
                    String newText = text.replaceAll("([：:])\\s*[_\\s]*$", "$1" + decision.getFinalValue());
                    // 替换段落文本而不是整个cell，保留格式
                    if (!targetCell.getParagraphs().isEmpty()) {
                        XWPFParagraph para = targetCell.getParagraphs().get(0);
                        List<XWPFRun> runs = para.getRuns();
                        if (!runs.isEmpty()) {
                            // 清除所有run然后重写
                            for (int i = runs.size() - 1; i >= 0; i--) {
                                para.removeRun(i);
                            }
                            XWPFRun newRun = para.createRun();
                            newRun.setText(newText);
                        }
                    }
                } else {
                    // 空白单元格直接写值
                    if (!targetCell.getParagraphs().isEmpty()) {
                        XWPFParagraph para = targetCell.getParagraphs().get(0);
                        List<XWPFRun> runs = para.getRuns();
                        if (runs.isEmpty()) {
                            XWPFRun run = para.createRun();
                            run.setText(decision.getFinalValue());
                        } else {
                            runs.get(0).setText(decision.getFinalValue(), 0);
                        }
                    }
                }
            }

            try (FileOutputStream fos = new FileOutputStream(outputPath)) {
                doc.write(fos);
            }
        }
        return outputPath;
    }

    // ===== 审计日志 =====

    private FillAuditLogEntity createAuditLog(Long templateId, TemplateSlotEntity slot, FillDecisionEntity decision, List<CandidateResult> candidates) {
        FillAuditLogEntity log = new FillAuditLogEntity();
        log.setTemplateId(templateId);
        log.setSlotId(slot.getId().toString());
        log.setSlotLabel(slot.getLabel());
        log.setFinalValue(decision.getFinalValue());
        log.setFinalConfidence(decision.getFinalConfidence());
        log.setDecisionMode(decision.getDecisionMode());

        if (!candidates.isEmpty()) {
            CandidateResult top = candidates.get(0);
            // 查找源文档名
            SourceDocumentEntity srcDoc = sourceDocumentMapper.selectById(top.field.getDocId());
            log.setSourceDocName(srcDoc != null ? srcDoc.getFileName() : "unknown");
            log.setSourceText(top.field.getSourceText());
        }

        log.setReason(decision.getReason());

        String candidatesSummary = candidates.stream()
                .map(c -> c.field.getFieldValue() + "(score=" + String.format("%.2f", c.score) + ")")
                .collect(Collectors.joining(", "));
        log.setCandidatesSummary("[" + candidatesSummary + "]");

        return log;
    }

    // ===== 工具方法 =====

    private boolean isLabelCell(String value) {
        if (value == null || value.isEmpty()) return false;
        value = value.trim();
        // 过长（大段文字）不作为标签
        if (value.length() > 20) return false;
        // 过短
        if (value.length() < 2) return false;
        // 排除纯数字
        if (value.matches("^[\\d.]+$")) return false;
        // 排除说明/备注等内容
        if (value.contains("说明") || value.contains("填写要求") || value.contains("请注意")) return false;
        return true;
    }

    private String guessType(String label) {
        if (label == null) return "text";
        if (label.contains("日期") || label.contains("时间") || label.contains("起始") || label.contains("截止")) return "date";
        if (label.contains("电话") || label.contains("手机") || label.contains("联系方式")) return "phone";
        if (label.contains("邮箱") || label.contains("Email") || label.contains("email")) return "text";
        if (label.contains("金额") || label.contains("经费") || label.contains("预算") || label.contains("资助")) return "number";
        if (label.contains("单位") || label.contains("机构") || label.contains("学院") || label.contains("部门")) return "org";
        if (label.contains("负责人") || label.contains("主持人") || label.contains("姓名")) return "person";
        return "text";
    }

    private TemplateSlotEntity buildSlot(Long templateId, String sheetName, int row, int col, String label, String type) {
        TemplateSlotEntity slot = new TemplateSlotEntity();
        slot.setTemplateId(templateId);
        slot.setLabel(label);
        slot.setContext(sheetName);
        slot.setPosition("{\"sheet\":\"" + sheetName + "\",\"row\":" + row + ",\"col\":" + col + "}");
        slot.setExpectedType(type);
        slot.setRequired(true);
        slot.setSlotType("adjacent_blank");
        return slot;
    }

    private String getCellRef(int row, int col) {
        char colLetter = (char) ('A' + col);
        return "" + colLetter + (row + 1);
    }

    private String getCellStringValue(XSSFCell cell) {
        if (cell == null) return "";
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

    private String normalizeLabel(String label) {
        // 与ExtractionServiceImpl中的normalizeFieldKey逻辑一致
        if (label == null) return "";
        Map<String, List<String>> aliasMap = Map.ofEntries(
                Map.entry("project_name", List.of("项目名称", "课题名称", "申报项目名称")),
                Map.entry("owner", List.of("负责人", "项目负责人", "课题负责人", "主持人")),
                Map.entry("org_name", List.of("单位名称", "申报单位", "承担单位", "所在单位")),
                Map.entry("phone", List.of("联系电话", "手机号码", "电话", "手机号")),
                Map.entry("email", List.of("电子邮箱", "邮箱")),
                Map.entry("id_number", List.of("身份证号", "身份证号码", "证件号码")),
                Map.entry("address", List.of("地址", "通讯地址", "联系地址")),
                Map.entry("start_date", List.of("开始日期", "起始日期", "开始时间")),
                Map.entry("end_date", List.of("结束日期", "截止日期", "结束时间")),
                Map.entry("budget", List.of("经费", "预算", "资助金额", "项目经费")),
                Map.entry("dept_name", List.of("部门", "院系", "学院")),
                Map.entry("title", List.of("职称", "职务")),
                Map.entry("degree", List.of("学历", "学位")),
                Map.entry("research_field", List.of("研究方向", "研究领域"))
        );
        for (Map.Entry<String, List<String>> entry : aliasMap.entrySet()) {
            for (String alias : entry.getValue()) {
                if (label.contains(alias) || alias.contains(label)) {
                    return entry.getKey();
                }
            }
        }
        return label.toLowerCase();
    }

    private String getFileType(String fileName) {
        if (fileName == null) return "unknown";
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) return "unknown";
        return fileName.substring(dot + 1).toLowerCase();
    }

    private String extractJsonFromResponse(String response) {
        response = response.trim();
        if (response.startsWith("```json")) response = response.substring(7);
        else if (response.startsWith("```")) response = response.substring(3);
        if (response.endsWith("```")) response = response.substring(0, response.length() - 3);
        return response.trim();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    // ===== 内部类 =====

    private static class CandidateResult {
        ExtractedFieldEntity field;
        double score;
        double aliasScore;
        double typeScore;
        double voteScore;
        double contextScore;
        double confScore;
    }

    private static class LlmJudgment {
        String selectedFieldId;
        String selectedValue;
        double modelConfidence;
        String reason;
    }
}
