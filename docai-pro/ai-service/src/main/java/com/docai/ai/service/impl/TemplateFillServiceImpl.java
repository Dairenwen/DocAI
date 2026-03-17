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

        // 先将文件保存到本地，避免 MultipartFile 流被多次消费
        Path tempDir;
        Path savedPath;
        try {
            tempDir = Files.createTempDirectory("docai_template_");
            savedPath = tempDir.resolve(originalFilename);
            try (var is = file.getInputStream()) {
                Files.copy(is, savedPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new RuntimeException("文件保存失败: " + e.getMessage());
        }

        // 文件已落盘后再上传OSS
        String ossKey = "";
        try {
            String fileUrl = ossService.uploadFile(file, "template_files/");
            ossKey = getOssKey(fileUrl);
        } catch (Exception ex) {
            log.warn("模板上传OSS失败，继续使用本地路径: {}", ex.getMessage());
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
                log.info("解析槽位: label={}, position={}, type={}, slotType={}", slot.getLabel(), slot.getPosition(), slot.getExpectedType(), slot.getSlotType());
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

        // 获取所有候选字段（仅当前用户的已解析文档）
        List<ExtractedFieldEntity> allFields;
        if (docIds != null && !docIds.isEmpty()) {
            // 验证docIds都属于当前用户，且只使用已解析完成的文档
            List<SourceDocumentEntity> userDocs = sourceDocumentMapper.selectList(
                    new LambdaQueryWrapper<SourceDocumentEntity>()
                            .eq(SourceDocumentEntity::getUserId, userId)
                            .in(SourceDocumentEntity::getId, docIds)
                            .eq(SourceDocumentEntity::getUploadStatus, "parsed")
            );
            if (userDocs.isEmpty()) {
                // 检查是否存在但还在解析中的文档
                long parsingCount = sourceDocumentMapper.selectCount(
                        new LambdaQueryWrapper<SourceDocumentEntity>()
                                .eq(SourceDocumentEntity::getUserId, userId)
                                .in(SourceDocumentEntity::getId, docIds)
                                .eq(SourceDocumentEntity::getUploadStatus, "parsing")
                );
                if (parsingCount > 0) {
                    throw new RuntimeException("所选文档仍在提取中，请等待提取完成后再执行填表操作");
                }
                throw new RuntimeException("未找到有效的已提取数据源文档");
            }
            List<Long> validDocIds = userDocs.stream().map(SourceDocumentEntity::getId).toList();
            allFields = extractedFieldMapper.selectList(
                    new LambdaQueryWrapper<ExtractedFieldEntity>().in(ExtractedFieldEntity::getDocId, validDocIds)
            );
        } else {
            // 仅获取当前用户的已解析文档
            List<SourceDocumentEntity> userDocs = sourceDocumentMapper.selectList(
                    new LambdaQueryWrapper<SourceDocumentEntity>()
                            .eq(SourceDocumentEntity::getUserId, userId)
                            .eq(SourceDocumentEntity::getUploadStatus, "parsed")
            );
            if (userDocs.isEmpty()) {
                // 检查是否存在但还在解析中的文档
                long parsingCount = sourceDocumentMapper.selectCount(
                        new LambdaQueryWrapper<SourceDocumentEntity>()
                                .eq(SourceDocumentEntity::getUserId, userId)
                                .eq(SourceDocumentEntity::getUploadStatus, "parsing")
                );
                if (parsingCount > 0) {
                    throw new RuntimeException("所有文档仍在提取中（" + parsingCount + "个），请等待提取完成后再执行填表操作");
                }
                throw new RuntimeException("当前用户没有已提取的源文档数据，请先在文档管理页面上传并提取文档");
            }
            List<Long> userDocIds = userDocs.stream().map(SourceDocumentEntity::getId).toList();
            allFields = extractedFieldMapper.selectList(
                    new LambdaQueryWrapper<ExtractedFieldEntity>().in(ExtractedFieldEntity::getDocId, userDocIds)
            );
        }

        if (allFields.isEmpty()) {
            throw new RuntimeException("数据源文档中未提取到任何字段信息，无法进行自动填表");
        }

        // === 预去重：按 (fieldKey, fieldName, fieldValue) 分组，保留置信度最高的字段 ===
        Map<String, ExtractedFieldEntity> deduplicatedMap = new LinkedHashMap<>();
        for (ExtractedFieldEntity f : allFields) {
            if (f.getFieldValue() == null || f.getFieldValue().trim().isEmpty()) continue;
            String dedupeKey = (f.getFieldKey() != null ? f.getFieldKey() : "") + "|||"
                    + (f.getFieldName() != null ? f.getFieldName().trim() : "") + "|||"
                    + f.getFieldValue().trim();
            ExtractedFieldEntity existing = deduplicatedMap.get(dedupeKey);
            if (existing == null) {
                deduplicatedMap.put(dedupeKey, f);
            } else {
                // 保留置信度更高的
                double existConf = existing.getConfidence() != null ? existing.getConfidence().doubleValue() : 0;
                double newConf = f.getConfidence() != null ? f.getConfidence().doubleValue() : 0;
                if (newConf > existConf) {
                    deduplicatedMap.put(dedupeKey, f);
                }
            }
        }
        // 记录原始字段数用于投票统计
        List<ExtractedFieldEntity> allFieldsRaw = allFields;
        allFields = new ArrayList<>(deduplicatedMap.values());
        log.info("字段预去重: 原始{}个 → 去重后{}个", allFieldsRaw.size(), allFields.size());

        // 获取别名词典
        List<FieldAliasDictEntity> aliasDictList = fieldAliasDictMapper.selectList(null);
        Map<String, List<String>> aliasDict = new HashMap<>();
        for (FieldAliasDictEntity a : aliasDictList) {
            aliasDict.computeIfAbsent(a.getStandardKey(), k -> new ArrayList<>()).add(a.getAliasName());
        }

        // 生成审计批次ID
        String auditId = "audit_" + templateId + "_" + System.currentTimeMillis();
        long fillStartTime = System.currentTimeMillis();
        long maxFillTimeMs = 40_000; // 40秒上限，给写回和网络留余量（总目标<60s）

        // 清理该模板的旧填表决策和审计日志，避免数据残留
        fillDecisionMapper.delete(
                new LambdaQueryWrapper<FillDecisionEntity>().eq(FillDecisionEntity::getTemplateId, templateId)
        );
        fillAuditLogMapper.delete(
                new LambdaQueryWrapper<FillAuditLogEntity>().eq(FillAuditLogEntity::getTemplateId, templateId)
        );

        // 对每个槽位进行候选召回和决策
        log.info("开始填表: templateId={}, 槽位数={}, 候选字段数(去重后)={}, 别名词典数={}", templateId, slots.size(), allFields.size(), aliasDict.size());
        List<FillDecisionEntity> decisions = new ArrayList<>();
        List<FillAuditLogEntity> auditLogs = new ArrayList<>();
        int filledCount = 0;
        int blankCount = 0;

        // 检查是否有header_below槽位可通过directTableCopy处理，避免昂贵的逐槽LLM调用
        boolean hasHeaderBelowSlots = slots.stream().anyMatch(s -> "header_below".equals(s.getSlotType()));
        boolean hasSourceExcel = false;
        if (hasHeaderBelowSlots) {
            List<Long> fieldDocIds = allFields.stream().map(ExtractedFieldEntity::getDocId).distinct().toList();
            for (Long did : fieldDocIds) {
                SourceDocumentEntity srcDoc = sourceDocumentMapper.selectById(did);
                if (srcDoc != null && srcDoc.getStoragePath() != null &&
                        (srcDoc.getStoragePath().endsWith(".xlsx") || srcDoc.getStoragePath().endsWith(".xls"))) {
                    hasSourceExcel = true;
                    break;
                }
            }
        }

        for (TemplateSlotEntity slot : slots) {
            log.debug("处理槽位: id={}, label={}, type={}", slot.getId(), slot.getLabel(), slot.getExpectedType());

            // header_below槽位如果有源Excel，跳过LLM逐槽匹配，由directTableCopy在写回阶段处理
            if ("header_below".equals(slot.getSlotType()) && hasSourceExcel) {
                FillDecisionEntity decision = new FillDecisionEntity();
                decision.setSlotId(slot.getId().toString());
                decision.setSlotLabel(slot.getLabel());
                decision.setFinalValue(""); // 占位，由directTableCopy填充
                decision.setFinalConfidence(BigDecimal.ZERO);
                decision.setDecisionMode("direct_copy_pending");
                decision.setReason("header_below槽位，等待directTableCopy从源Excel复制数据");
                decision.setTemplateId(templateId);
                decision.setAuditId(auditId);
                fillDecisionMapper.insert(decision);
                decisions.add(decision);
                blankCount++;

                FillAuditLogEntity auditLog = createAuditLog(templateId, slot, decision, Collections.emptyList());
                auditLog.setAuditId(auditId);
                auditLog.setUserId(userId);
                fillAuditLogMapper.insert(auditLog);
                auditLogs.add(auditLog);
                continue;
            }

            // 阶段4：候选召回
            List<CandidateResult> candidates = recallCandidates(slot, allFields, allFieldsRaw, aliasDict);

            // 阶段5：难例判定（超时则跳过LLM）
            boolean timeExceeded = (System.currentTimeMillis() - fillStartTime) > maxFillTimeMs;
            FillDecisionEntity decision = makeDecision(slot, candidates, timeExceeded);

            // 阶段6：置信度融合
            applyConfidenceThreshold(decision);
            log.info("  决策结果: label={}, value={}, confidence={}, mode={}", 
                decision.getSlotLabel(), decision.getFinalValue(), decision.getFinalConfidence(), decision.getDecisionMode());

            decision.setTemplateId(templateId);
            decision.setAuditId(auditId);
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
            auditLog.setAuditId(auditId);
            auditLog.setUserId(userId);
            fillAuditLogMapper.insert(auditLog);
            auditLogs.add(auditLog);
        }

        // 兜底：如果所有决策都为空，尝试贪心直接匹配（通过normalizeLabel对齐）
        if (filledCount == 0 && !allFields.isEmpty()) {
            log.warn("所有槽位均未匹配到数据，启用贪心匹配兜底");
            Map<String, ExtractedFieldEntity> fieldByKey = new HashMap<>();
            Map<String, ExtractedFieldEntity> fieldByName = new HashMap<>();
            for (ExtractedFieldEntity f : allFields) {
                if (f.getFieldValue() != null && !f.getFieldValue().trim().isEmpty()) {
                    if (f.getFieldKey() != null) fieldByKey.putIfAbsent(f.getFieldKey(), f);
                    if (f.getFieldName() != null) fieldByName.putIfAbsent(f.getFieldName().trim().toLowerCase(), f);
                }
            }
            for (int i = 0; i < slots.size(); i++) {
                TemplateSlotEntity slot = slots.get(i);
                FillDecisionEntity decision = decisions.get(i);
                if (decision.getFinalValue() != null && !decision.getFinalValue().isEmpty()) continue;
                
                String label = slot.getLabel().trim();
                String labelStd = normalizeLabel(label);
                ExtractedFieldEntity matched = fieldByKey.get(labelStd);
                if (matched == null) matched = fieldByName.get(label.toLowerCase());
                if (matched == null) {
                    // 尝试模糊包含匹配
                    for (ExtractedFieldEntity f : allFields) {
                        if (f.getFieldValue() != null && !f.getFieldValue().trim().isEmpty() && f.getFieldName() != null) {
                            String fn = f.getFieldName().trim();
                            if (label.contains(fn) || fn.contains(label)) {
                                matched = f;
                                break;
                            }
                        }
                    }
                }
                if (matched != null) {
                    decision.setFinalValue(matched.getFieldValue());
                    decision.setFinalConfidence(new BigDecimal("0.50"));
                    decision.setDecisionMode("greedy_fallback");
                    decision.setReason("贪心兜底匹配: " + matched.getFieldName() + " -> " + matched.getFieldValue());
                    fillDecisionMapper.updateById(decision);
                    filledCount++;
                    blankCount--;
                    log.info("贪心兜底: slot={} -> field={}, value={}", label, matched.getFieldName(), matched.getFieldValue());
                }
            }
        }

        log.info("填表完成: templateId={}, filled={}, blank={}, total={}", templateId, filledCount, blankCount, slots.size());

        // 终极兜底：如果贪心匹配后仍有大量空白，使用LLM从源文档直接提取
        if (filledCount == 0 && !allFields.isEmpty()) {
            log.warn("贪心匹配后仍全部为空，启用LLM终极兜底");
            try {
                StringBuilder sourceContent = new StringBuilder();
                for (ExtractedFieldEntity f : allFields) {
                    if (f.getSourceText() != null && !f.getSourceText().isBlank()) {
                        sourceContent.append(f.getSourceText()).append("\n");
                    }
                    if (f.getFieldName() != null && f.getFieldValue() != null) {
                        sourceContent.append(f.getFieldName()).append(": ").append(f.getFieldValue()).append("\n");
                    }
                }
                if (sourceContent.length() < 100) {
                    List<Long> fieldDocIds = allFields.stream().map(ExtractedFieldEntity::getDocId).distinct().toList();
                    for (Long did : fieldDocIds) {
                        SourceDocumentEntity srcDoc = sourceDocumentMapper.selectById(did);
                        if (srcDoc != null && srcDoc.getStoragePath() != null) {
                            try {
                                String text = java.nio.file.Files.readString(Path.of(srcDoc.getStoragePath()), java.nio.charset.StandardCharsets.UTF_8);
                                if (text.length() > 5000) text = text.substring(0, 5000);
                                sourceContent.append(text).append("\n");
                            } catch (Exception ignored) {}
                        }
                    }
                }
                String content = sourceContent.toString();
                if (content.length() > 8000) content = content.substring(0, 8000);
                StringBuilder slotLabels = new StringBuilder();
                List<Integer> blankIndices = new ArrayList<>();
                for (int i = 0; i < slots.size(); i++) {
                    FillDecisionEntity d = decisions.get(i);
                    if (d.getFinalValue() == null || d.getFinalValue().isEmpty()) {
                        slotLabels.append("- ").append(slots.get(i).getLabel()).append("\n");
                        blankIndices.add(i);
                    }
                }
                String llmPrompt = "请根据以下数据源内容，提取指定字段的值。\n\n数据源内容：\n" + content +
                    "\n\n需要提取的字段：\n" + slotLabels +
                    "\n请以JSON格式返回，key为字段名，value为从数据源中提取到的值。如果找不到对应内容则value为空字符串。" +
                    "\n只返回JSON，不要任何其他文字。示例：{\"项目名称\":\"xxx\",\"负责人\":\"xxx\"}";
                String llmResp = llmService.generateText(llmPrompt);
                if (llmResp != null && !llmResp.isBlank()) {
                    String jsonStr = extractJsonFromResponse(llmResp);
                    JSONObject llmResult = JSON.parseObject(jsonStr);
                    if (llmResult != null) {
                        for (int idx : blankIndices) {
                            TemplateSlotEntity slot = slots.get(idx);
                            FillDecisionEntity decision = decisions.get(idx);
                            String label = slot.getLabel().trim();
                            String val = llmResult.getString(label);
                            if (val == null || val.isBlank()) {
                                for (String key : llmResult.keySet()) {
                                    if (key.contains(label) || label.contains(key)) {
                                        val = llmResult.getString(key);
                                        break;
                                    }
                                }
                            }
                            if (val != null && !val.isBlank()) {
                                decision.setFinalValue(val);
                                decision.setFinalConfidence(new BigDecimal("0.65"));
                                decision.setDecisionMode("llm_fallback");
                                decision.setReason("LLM终极兜底提取");
                                fillDecisionMapper.updateById(decision);
                                filledCount++;
                                blankCount--;
                                log.info("LLM兜底: slot={}, value={}", label, val);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("LLM终极兜底失败: {}", e.getMessage());
            }
        }

        log.info("最终填表结果: templateId={}, filled={}, blank={}, total={}", templateId, filledCount, blankCount, slots.size());

        // 阶段7：模板写回
        String outputPath = null;
        try {
            // 收集header_below槽位的源文档路径，用于直接表格数据复制
            List<String> sourceExcelPaths = new ArrayList<>();
            if (hasHeaderBelowSlots) {
                List<Long> fieldDocIds2 = allFields.stream().map(ExtractedFieldEntity::getDocId).distinct().toList();
                for (Long did : fieldDocIds2) {
                    SourceDocumentEntity srcDoc = sourceDocumentMapper.selectById(did);
                    if (srcDoc != null && srcDoc.getStoragePath() != null
                            && (srcDoc.getStoragePath().endsWith(".xlsx") || srcDoc.getStoragePath().endsWith(".xls") || srcDoc.getStoragePath().endsWith(".csv"))) {
                        sourceExcelPaths.add(srcDoc.getStoragePath());
                    }
                }
            }

            // 统计聚合阶段：对adjacent_blank等非header_below槽位，如果label对应header_below列，
            // 则从源Excel读取该列所有数值并计算统计值（SUM/COUNT/AVG/MAX/MIN）
            if (hasHeaderBelowSlots && !sourceExcelPaths.isEmpty()) {
                Set<String> headerLabels = slots.stream()
                        .filter(s -> "header_below".equals(s.getSlotType()))
                        .map(s -> s.getLabel().trim())
                        .collect(Collectors.toSet());

                for (int i = 0; i < slots.size(); i++) {
                    TemplateSlotEntity slot = slots.get(i);
                    if ("header_below".equals(slot.getSlotType())) continue;

                    String label = slot.getLabel().trim();
                    // 检查该槽位label是否匹配某个header_below列（精确或包含匹配）
                    String matchedHeader = null;
                    for (String hl : headerLabels) {
                        if (hl.equalsIgnoreCase(label) || label.contains(hl) || hl.contains(label)
                                || normalizeLabel(hl).equals(normalizeLabel(label))) {
                            matchedHeader = hl;
                            break;
                        }
                    }
                    if (matchedHeader == null) continue;

                    // 确定聚合类型
                    String aggType = detectAggregationType(label);
                    // 从源Excel读取该列数据并聚合
                    String aggResult = computeColumnAggregation(sourceExcelPaths, matchedHeader, aggType);
                    if (aggResult != null) {
                        FillDecisionEntity decision = decisions.get(i);
                        decision.setFinalValue(aggResult);
                        decision.setFinalConfidence(new BigDecimal("0.90"));
                        decision.setDecisionMode("statistical_aggregation");
                        decision.setReason(aggType.toUpperCase() + "统计聚合: " + matchedHeader + " = " + aggResult);
                        fillDecisionMapper.updateById(decision);
                        if (decision.getFinalValue() != null && !decision.getFinalValue().isEmpty()) {
                            filledCount++;
                            blankCount--;
                        }
                        log.info("统计聚合: slot={}, header={}, aggType={}, result={}", label, matchedHeader, aggType, aggResult);
                    }
                }
            }

            outputPath = writeBack(tpl, slots, decisions, sourceExcelPaths);

            // 重新计算填充计数（writeBack中directTableCopy会更新decisions）
            filledCount = 0;
            blankCount = 0;
            for (FillDecisionEntity d : decisions) {
                if (d.getFinalValue() != null && !d.getFinalValue().isEmpty()) {
                    filledCount++;
                } else {
                    blankCount++;
                }
            }
        } catch (Exception e) {
            log.error("模板写回失败: {}", e.getMessage(), e);
            // 即使写回失败也返回结果，只是无法下载
            outputPath = null;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("templateId", templateId);
        result.put("outputFile", outputPath);
        result.put("filledCount", filledCount);
        result.put("blankCount", blankCount);
        result.put("totalSlots", slots.size());
        result.put("auditId", auditId);
        result.put("decisions", decisions);

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

                // 记录每行已通过规则1/2/3产生的slot列号，避免表头检测重复
                Set<String> slottedPositions = new HashSet<>();

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
                            boolean slotAdded = false;
                            // 规则1: 右边为空 → 槽位在右边
                            XSSFCell rightCell = row.getCell(c + 1);
                            if (rightCell == null || getCellStringValue(rightCell).isEmpty()) {
                                TemplateSlotEntity slot = buildSlot(templateId, sheetName, r, c + 1, cellValue, guessType(cellValue));
                                slots.add(slot);
                                slotAdded = true;
                                slottedPositions.add(sheetName + "!" + r + "!" + (c + 1));
                            }

                            // 规则2: 下面为空 → 槽位在下面 (only if no right slot found)
                            if (!slotAdded) {
                                XSSFRow nextRow = sheet.getRow(r + 1);
                                if (nextRow != null) {
                                    XSSFCell belowCell = nextRow.getCell(c);
                                    if (belowCell == null || getCellStringValue(belowCell).isEmpty()) {
                                        TemplateSlotEntity slot = buildSlot(templateId, sheetName, r + 1, c, cellValue, guessType(cellValue));
                                        slots.add(slot);
                                        slotAdded = true;
                                        slottedPositions.add(sheetName + "!" + (r + 1) + "!" + c);
                                    }
                                }
                            }

                            // 规则3: 包含"：____"或": ____"
                            if (!slotAdded && cellValue.matches(".*[：:]\\s*[_\\s]*$")) {
                                TemplateSlotEntity slot = buildSlot(templateId, sheetName, r, c, cellValue.replaceAll("[：:].*", "").trim(), guessType(cellValue));
                                slot.setSlotType("inline");
                                slots.add(slot);
                                slottedPositions.add(sheetName + "!" + r + "!" + c);
                            }
                        }
                    }
                }

                // 规则4: 表头行检测 — 整行大部分为标签且下方行为空或不存在
                for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                    XSSFRow row = sheet.getRow(r);
                    if (row == null) continue;
                    int nonEmptyCells = 0;
                    int labelCells = 0;
                    List<Integer> headerCols = new ArrayList<>();
                    List<String> headerLabels = new ArrayList<>();
                    for (int c = 0; c < row.getLastCellNum(); c++) {
                        XSSFCell cell = row.getCell(c);
                        if (cell == null) continue;
                        String val = getCellStringValue(cell);
                        if (val.isEmpty()) continue;
                        nonEmptyCells++;
                        if (isLabelCell(val)) {
                            labelCells++;
                            headerCols.add(c);
                            headerLabels.add(val);
                        }
                    }
                    // 至少2个标签单元格且超过一半是标签→认定为表头行
                    if (labelCells >= 2 && labelCells >= nonEmptyCells * 0.5) {
                        int targetRow = r + 1; // 数据应填入表头下一行
                        XSSFRow dataRow = sheet.getRow(targetRow);
                        for (int i = 0; i < headerCols.size(); i++) {
                            int c = headerCols.get(i);
                            String posKey = sheetName + "!" + targetRow + "!" + c;
                            if (slottedPositions.contains(posKey)) continue; // 已有slot
                            // 检查目标单元格是否为空（可填）
                            boolean targetEmpty = true;
                            if (dataRow != null) {
                                XSSFCell targetCell = dataRow.getCell(c);
                                if (targetCell != null && !getCellStringValue(targetCell).isEmpty()) {
                                    targetEmpty = false;
                                }
                            }
                            if (targetEmpty) {
                                TemplateSlotEntity slot = buildSlot(templateId, sheetName, targetRow, c, headerLabels.get(i), guessType(headerLabels.get(i)));
                                slot.setSlotType("header_below");
                                slots.add(slot);
                                slottedPositions.add(posKey);
                                log.info("表头行检测: sheet={}, headerRow={}, label={}, targetPos=[{},{}]", sheetName, r, headerLabels.get(i), targetRow, c);
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
                                                    List<ExtractedFieldEntity> allFieldsRaw,
                                                    Map<String, List<String>> aliasDict) {
        List<CandidateResult> candidates = new ArrayList<>();
        String slotLabel = slot.getLabel().trim();
        String slotLabelStd = normalizeLabel(slotLabel);

        // 统计多文档投票（使用原始未去重字段列表以准确反映跨文档出现频率）
        Map<String, Integer> valueVotes = new HashMap<>();
        for (ExtractedFieldEntity f : allFieldsRaw) {
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
            String fieldNameNorm = field.getFieldName() != null ? normalizeLabel(field.getFieldName().trim()) : "";
            if (slotLabel.equals(field.getFieldName()) || slotLabelStd.equals(field.getFieldKey())) {
                aliasScore = 1.0;
            } else if (field.getFieldName() != null && slotLabel.equalsIgnoreCase(field.getFieldName().trim())) {
                aliasScore = 1.0;
            } else if (!slotLabelStd.equals(slotLabel.toLowerCase()) && slotLabelStd.equals(fieldNameNorm)) {
                // 两者通过normalizeLabel映射到同一标准key
                aliasScore = 0.95;
            } else {
                // 检查别名词典
                for (Map.Entry<String, List<String>> entry : aliasDict.entrySet()) {
                    if (entry.getValue().contains(slotLabel) && entry.getKey().equals(field.getFieldKey())) {
                        aliasScore = 0.95;
                        break;
                    }
                    if (entry.getValue().stream().anyMatch(a -> slotLabel.contains(a) || a.contains(slotLabel))
                            && entry.getKey().equals(field.getFieldKey())) {
                        aliasScore = 0.80;
                        break;
                    }
                    // 检查别名与字段名的交叉匹配
                    if (field.getFieldName() != null && entry.getValue().stream().anyMatch(a ->
                            field.getFieldName().contains(a) || a.contains(field.getFieldName()))
                            && entry.getValue().stream().anyMatch(a ->
                            slotLabel.contains(a) || a.contains(slotLabel))) {
                        aliasScore = 0.75;
                        break;
                    }
                }
                // 内置别名映射匹配
                if (aliasScore == 0.0) {
                    String slotStd = normalizeLabel(slotLabel);
                    String fieldStd = field.getFieldKey() != null ? field.getFieldKey() : normalizeLabel(field.getFieldName() != null ? field.getFieldName() : "");
                    if (!slotStd.equals(slotLabel.toLowerCase()) && slotStd.equals(fieldStd)) {
                        aliasScore = 0.90;
                    }
                }
                // 模糊匹配
                if (aliasScore == 0.0 && field.getFieldName() != null) {
                    String fn = field.getFieldName().trim();
                    if (slotLabel.contains(fn) || fn.contains(slotLabel)) {
                        aliasScore = 0.60;
                    } else {
                        // 部分字符重叠匹配
                        int overlap = countOverlapChars(slotLabel, fn);
                        if (overlap >= 2 && overlap >= Math.min(slotLabel.length(), fn.length()) * 0.5) {
                            aliasScore = 0.40;
                        }
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
            // 4b. sourceText直接包含slot标签 → 增强上下文匹配
            if (contextScore < 0.5 && field.getSourceText() != null
                    && field.getSourceText().contains(slotLabel)) {
                contextScore = Math.max(contextScore, 0.7);
            }

            // 计算综合得分
            double score = 0.40 * aliasScore + 0.20 * typeScore + 0.20 * voteScore + 0.10 * contextScore + 0.10 * confScore;

            if (score > 0.01 && (aliasScore > 0.0 || contextScore > 0.3)) { // 至少有别名匹配或上下文匹配
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

        // 排序并按值去重：相同fieldValue只保留最高分候选
        candidates.sort((a, b) -> Double.compare(b.score, a.score));
        List<CandidateResult> deduped = new ArrayList<>();
        Set<String> seenValues = new HashSet<>();
        for (CandidateResult cr : candidates) {
            String val = cr.field.getFieldValue().trim();
            if (seenValues.add(val)) {
                deduped.add(cr);
                if (deduped.size() >= 5) break;
            }
        }

        return deduped;
    }

    // ===== 阶段5：难例判定 =====

    private FillDecisionEntity makeDecision(TemplateSlotEntity slot, List<CandidateResult> candidates, boolean skipLlm) {
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
            // 条件1: Top1和Top2分差 < 0.08 且候选值不同
            if (top1.score - top2.score < 0.08
                    && !Objects.equals(top1.field.getFieldValue(), top2.field.getFieldValue())) needLlm = true;
        }
        // 条件2: Top1分数太低且别名完全没命中
        if (top1.score < 0.40 && top1.aliasScore < 0.3) needLlm = true;

        if (needLlm && !skipLlm) {
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
        if (conf < 0.20) {
            decision.setFinalValue(""); // 拒填：置信度极低，避免错填
            decision.setDecisionMode("fallback_blank");
            decision.setReason(decision.getReason() + " [置信度=" + String.format("%.2f", conf) + " < 0.20, 拒填]");
        } else if (conf < 0.70) {
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

    private String writeBack(TemplateFileEntity tpl, List<TemplateSlotEntity> slots, List<FillDecisionEntity> decisions, List<String> sourceExcelPaths) throws Exception {
        // 构建slotId -> decision的映射（使用slotId避免label重复冲突）
        Map<String, FillDecisionEntity> decisionMap = new HashMap<>();
        for (FillDecisionEntity d : decisions) {
            decisionMap.put(d.getSlotId(), d);
        }

        String outputPath;
        if ("xlsx".equals(tpl.getTemplateType())) {
            outputPath = writeBackExcel(tpl.getStoragePath(), slots, decisionMap, sourceExcelPaths);
        } else {
            outputPath = writeBackWord(tpl.getStoragePath(), slots, decisionMap);
        }

        tpl.setOutputPath(outputPath);
        templateFileMapper.updateById(tpl);

        return outputPath;
    }

    private String writeBackExcel(String templatePath, List<TemplateSlotEntity> slots, Map<String, FillDecisionEntity> decisionMap, List<String> sourceExcelPaths) throws Exception {
        // 只替换最后一个点（文件扩展名前）
        int lastDot = templatePath.lastIndexOf('.');
        String outputPath = lastDot > 0
                ? templatePath.substring(0, lastDot) + "_filled" + templatePath.substring(lastDot)
                : templatePath + "_filled";
        
        log.info("Excel写回: 模板={}, 输出={}, 槽位数={}, 决策数={}", templatePath, outputPath, slots.size(), decisionMap.size());
        
        try (FileInputStream fis = new FileInputStream(templatePath);
             XSSFWorkbook workbook = new XSSFWorkbook(fis)) {

            // 检测是否有header_below槽位且有匹配的源Excel可以直接复制
            List<TemplateSlotEntity> headerSlots = slots.stream()
                    .filter(s -> "header_below".equals(s.getSlotType()))
                    .collect(Collectors.toList());
            boolean directCopyDone = false;

            if (!headerSlots.isEmpty() && sourceExcelPaths != null && !sourceExcelPaths.isEmpty()) {
                directCopyDone = directTableCopy(workbook, headerSlots, sourceExcelPaths, decisionMap);
            }

            // 写入单值决策（对于非header_below槽位，或直接复制失败的情况）
            int writtenCount = 0;
            for (TemplateSlotEntity slot : slots) {
                // 如果是header_below且已直接复制成功，跳过单值写入
                if (directCopyDone && "header_below".equals(slot.getSlotType())) continue;

                FillDecisionEntity decision = decisionMap.get(slot.getId().toString());
                if (decision == null || decision.getFinalValue() == null || decision.getFinalValue().isEmpty()) {
                    log.debug("跳过槽位 {}: decision={}", slot.getLabel(), decision != null ? decision.getDecisionMode() : "null");
                    continue;
                }

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
                    String existing = getCellStringValue(cell);
                    cell.setCellValue(existing + decision.getFinalValue());
                } else {
                    cell.setCellValue(decision.getFinalValue());
                }
                writtenCount++;
                log.info("写入单元格: sheet={}, row={}, col={}, label={}, value={}", sheetName, row, col, slot.getLabel(), decision.getFinalValue());
            }

            log.info("Excel写回完成: 共写入{}个单元格到 {}, 直接表格复制={}", writtenCount, outputPath, directCopyDone);
            try (FileOutputStream fos = new FileOutputStream(outputPath)) {
                workbook.write(fos);
            }
        }
        return outputPath;
    }

    /**
     * 直接从源Excel复制数据行到模板。
     * 匹配模板header标签与源Excel表头，然后将所有数据行复制到模板中。
     */
    private boolean directTableCopy(XSSFWorkbook templateWorkbook, List<TemplateSlotEntity> headerSlots, List<String> sourceExcelPaths, Map<String, FillDecisionEntity> decisionMap) {
        // 提取header slot的标签和目标位置
        // headerSlots 同一sheet，同一targetRow，不同col
        if (headerSlots.isEmpty()) return false;
        JSONObject firstPos = JSON.parseObject(headerSlots.get(0).getPosition());
        String targetSheetName = firstPos.getString("sheet");
        int targetStartRow = firstPos.getIntValue("row");

        XSSFSheet targetSheet = templateWorkbook.getSheet(targetSheetName);
        if (targetSheet == null) return false;

        // 构建 label -> col 映射
        Map<String, Integer> labelToCol = new LinkedHashMap<>();
        for (TemplateSlotEntity slot : headerSlots) {
            JSONObject p = JSON.parseObject(slot.getPosition());
            labelToCol.put(slot.getLabel().trim(), p.getIntValue("col"));
        }

        int totalCopied = 0;
        for (String srcPath : sourceExcelPaths) {
            try (FileInputStream srcFis = new FileInputStream(srcPath);
                 XSSFWorkbook srcWorkbook = new XSSFWorkbook(srcFis)) {

                for (int si = 0; si < srcWorkbook.getNumberOfSheets(); si++) {
                    XSSFSheet srcSheet = srcWorkbook.getSheetAt(si);
                    if (srcSheet.getLastRowNum() < 1) continue; // 至少需要表头+1行数据

                    // 读取源表头
                    XSSFRow srcHeaderRow = srcSheet.getRow(0);
                    if (srcHeaderRow == null) continue;

                    // 匹配列: 模板label → 源列index
                    Map<Integer, Integer> colMapping = new LinkedHashMap<>(); // targetCol -> srcCol
                    for (int c = 0; c < srcHeaderRow.getLastCellNum(); c++) {
                        XSSFCell hCell = srcHeaderRow.getCell(c);
                        if (hCell == null) continue;
                        String hVal = getCellStringValue(hCell).trim();
                        if (hVal.isEmpty()) continue;
                        // 精确或模糊匹配
                        for (Map.Entry<String, Integer> entry : labelToCol.entrySet()) {
                            String tplLabel = entry.getKey();
                            if (tplLabel.equalsIgnoreCase(hVal)
                                    || tplLabel.contains(hVal) || hVal.contains(tplLabel)
                                    || normalizeLabel(tplLabel).equals(normalizeLabel(hVal))) {
                                colMapping.put(entry.getValue(), c);
                                break;
                            }
                        }
                    }

                    if (colMapping.isEmpty()) continue;
                    log.info("直接表格复制: 源={}, sheet={}, 匹配列数={}/{}, 数据行数={}",
                            srcPath, srcSheet.getSheetName(), colMapping.size(), labelToCol.size(), srcSheet.getLastRowNum());

                    // 复制数据行
                    for (int r = 1; r <= srcSheet.getLastRowNum(); r++) {
                        XSSFRow srcRow = srcSheet.getRow(r);
                        if (srcRow == null) continue;

                        boolean hasData = false;
                        Map<Integer, String> rowValues = new LinkedHashMap<>();
                        for (Map.Entry<Integer, Integer> cm : colMapping.entrySet()) {
                            int destCol = cm.getKey();
                            int srcCol = cm.getValue();
                            XSSFCell srcCell = srcRow.getCell(srcCol);
                            String val = srcCell != null ? getCellStringValue(srcCell) : "";
                            if (!val.isEmpty()) hasData = true;
                            rowValues.put(destCol, val);
                        }
                        if (!hasData) continue;

                        int destRowIdx = targetStartRow + totalCopied;
                        XSSFRow destRow = targetSheet.getRow(destRowIdx);
                        if (destRow == null) destRow = targetSheet.createRow(destRowIdx);

                        for (Map.Entry<Integer, String> rv : rowValues.entrySet()) {
                            if (rv.getValue().isEmpty()) continue;
                            XSSFCell destCell = destRow.getCell(rv.getKey());
                            if (destCell == null) destCell = destRow.createCell(rv.getKey());
                            destCell.setCellValue(rv.getValue());
                        }
                        totalCopied++;
                    }
                }
            } catch (Exception e) {
                log.warn("直接表格复制失败(源={}): {}", srcPath, e.getMessage());
            } catch (OutOfMemoryError oom) {
                log.error("直接表格复制内存不足(源={}), 跳过该文件", srcPath);
                System.gc();
            }
        }

        log.info("直接表格复制完成: 共复制{}行数据到sheet={}, 从row={}开始", totalCopied, targetSheetName, targetStartRow);

        // 更新header_below槽位的填充决策，反映实际复制结果
        if (totalCopied > 0 && decisionMap != null) {
            for (TemplateSlotEntity slot : headerSlots) {
                FillDecisionEntity decision = decisionMap.get(slot.getId().toString());
                if (decision == null) continue;
                decision.setFinalValue("已复制" + totalCopied + "行数据");
                decision.setFinalConfidence(new BigDecimal("0.95"));
                decision.setDecisionMode("direct_table_copy");
                decision.setReason("从源Excel直接复制" + totalCopied + "行数据，列: " + slot.getLabel());
                fillDecisionMapper.updateById(decision);
            }
        }

        return totalCopied > 0;
    }

    private String writeBackWord(String templatePath, List<TemplateSlotEntity> slots, Map<String, FillDecisionEntity> decisionMap) throws Exception {
        int lastDot = templatePath.lastIndexOf('.');
        String outputPath = lastDot > 0
                ? templatePath.substring(0, lastDot) + "_filled" + templatePath.substring(lastDot)
                : templatePath + "_filled";
        try (FileInputStream fis = new FileInputStream(templatePath);
             XWPFDocument doc = new XWPFDocument(fis)) {

            for (TemplateSlotEntity slot : slots) {
                FillDecisionEntity decision = decisionMap.get(slot.getId().toString());
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
        if (value.length() > 50) return false;
        // 过短
        if (value.length() < 1) return false;
        // 排除纯数字
        if (value.matches("^[\\d.]+$")) return false;
        // 排除说明/备注等内容
        if (value.contains("填写要求") || value.contains("请注意")) return false;
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
        // 清理标签文字
        label = label.trim().replaceAll("[：:（()）\\s]+$", "").trim();
        Map<String, List<String>> aliasMap = Map.ofEntries(
                Map.entry("project_name", List.of("项目名称", "课题名称", "申报项目名称", "项目", "课题")),
                Map.entry("owner", List.of("负责人", "项目负责人", "课题负责人", "主持人", "申请人", "申报人", "联系人")),
                Map.entry("org_name", List.of("单位名称", "申报单位", "承担单位", "所在单位", "工作单位", "单位")),
                Map.entry("phone", List.of("联系电话", "手机号码", "电话", "手机号", "手机", "电话号码", "联系方式")),
                Map.entry("email", List.of("电子邮箱", "邮箱", "Email", "邮件")),
                Map.entry("id_number", List.of("身份证号", "身份证号码", "证件号码", "身份证")),
                Map.entry("address", List.of("地址", "通讯地址", "联系地址", "详细地址")),
                Map.entry("start_date", List.of("开始日期", "起始日期", "开始时间", "起始时间", "立项时间", "项目开始")),
                Map.entry("end_date", List.of("结束日期", "截止日期", "结束时间", "截止时间", "完成时间", "项目结束")),
                Map.entry("budget", List.of("经费", "预算", "资助金额", "项目经费", "总经费", "申请经费", "拨款金额")),
                Map.entry("dept_name", List.of("部门", "院系", "学院", "系别", "专业")),
                Map.entry("title", List.of("职称", "职务", "技术职称")),
                Map.entry("degree", List.of("学历", "学位", "最高学历")),
                Map.entry("research_field", List.of("研究方向", "研究领域", "专业方向")),
                Map.entry("gender", List.of("性别")),
                Map.entry("age", List.of("年龄")),
                Map.entry("birth_date", List.of("出生日期", "出生年月", "生日")),
                Map.entry("nationality", List.of("民族", "国籍")),
                Map.entry("political_status", List.of("政治面貌")),
                Map.entry("zip_code", List.of("邮编", "邮政编码")),
                Map.entry("fax", List.of("传真", "传真号码")),
                Map.entry("bank_account", List.of("银行账号", "账号", "开户行")),
                Map.entry("project_type", List.of("项目类型", "类型", "项目类别")),
                Map.entry("project_level", List.of("项目级别", "级别")),
                Map.entry("keywords", List.of("关键词", "关键字")),
                Map.entry("abstract_text", List.of("摘要", "项目摘要", "内容摘要")),
                Map.entry("member", List.of("成员", "团队成员", "参与人", "参加人员", "项目成员")),
                Map.entry("duration", List.of("执行期限", "研究期限", "项目期限", "实施周期"))
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

    private int countOverlapChars(String a, String b) {
        if (a == null || b == null) return 0;
        Set<Character> setA = new HashSet<>();
        for (char c : a.toCharArray()) setA.add(c);
        int count = 0;
        for (char c : b.toCharArray()) {
            if (setA.contains(c)) count++;
        }
        return count;
    }

    // ===== 统计聚合 =====

    /**
     * 根据槽位标签检测需要的聚合类型。
     */
    private String detectAggregationType(String label) {
        if (label == null) return "sum";
        String l = label.toLowerCase();
        if (l.contains("平均") || l.contains("均值") || l.contains("avg")) return "avg";
        if (l.contains("最大") || l.contains("max") || l.contains("峰值")) return "max";
        if (l.contains("最小") || l.contains("min") || l.contains("最低")) return "min";
        if (l.contains("数量") || l.contains("个数") || l.contains("count") || l.contains("条数")) return "count";
        // 默认：对于数值列做SUM，对于非数值列做COUNT
        return "sum";
    }

    /**
     * 从源Excel读取指定列标题的所有数据值，并计算聚合结果。
     */
    private String computeColumnAggregation(List<String> sourceExcelPaths, String headerLabel, String aggType) {
        List<Double> numericValues = new ArrayList<>();
        int totalRows = 0;

        for (String srcPath : sourceExcelPaths) {
            try (FileInputStream fis = new FileInputStream(srcPath);
                 XSSFWorkbook wb = new XSSFWorkbook(fis)) {
                for (int si = 0; si < wb.getNumberOfSheets(); si++) {
                    XSSFSheet sheet = wb.getSheetAt(si);
                    if (sheet.getLastRowNum() < 1) continue;
                    XSSFRow headerRow = sheet.getRow(0);
                    if (headerRow == null) continue;

                    // 查找匹配的列
                    int targetCol = -1;
                    for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                        XSSFCell hCell = headerRow.getCell(c);
                        if (hCell == null) continue;
                        String hVal = getCellStringValue(hCell).trim();
                        if (hVal.isEmpty()) continue;
                        if (hVal.equalsIgnoreCase(headerLabel)
                                || hVal.contains(headerLabel) || headerLabel.contains(hVal)
                                || normalizeLabel(hVal).equals(normalizeLabel(headerLabel))) {
                            targetCol = c;
                            break;
                        }
                    }
                    if (targetCol < 0) continue;

                    // 读取该列所有数据行
                    for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                        XSSFRow row = sheet.getRow(r);
                        if (row == null) continue;
                        XSSFCell cell = row.getCell(targetCol);
                        if (cell == null) continue;
                        totalRows++;

                        if (cell.getCellType() == CellType.NUMERIC) {
                            numericValues.add(cell.getNumericCellValue());
                        } else {
                            String val = getCellStringValue(cell).trim();
                            if (!val.isEmpty()) {
                                try {
                                    numericValues.add(Double.parseDouble(val.replaceAll("[,，%％]", "")));
                                } catch (NumberFormatException ignored) {}
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("统计聚合读取源文件失败({}): {}", srcPath, e.getMessage());
            }
        }

        if ("count".equals(aggType)) {
            return String.valueOf(totalRows);
        }
        if (numericValues.isEmpty()) {
            return "count".equals(aggType) ? String.valueOf(totalRows) : null;
        }

        double result;
        switch (aggType) {
            case "avg":
                result = numericValues.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                break;
            case "max":
                result = numericValues.stream().mapToDouble(Double::doubleValue).max().orElse(0);
                break;
            case "min":
                result = numericValues.stream().mapToDouble(Double::doubleValue).min().orElse(0);
                break;
            default: // sum
                result = numericValues.stream().mapToDouble(Double::doubleValue).sum();
                break;
        }

        // 格式化：整数不显示小数点，否则保留2位
        if (result == Math.floor(result) && !Double.isInfinite(result)) {
            return String.valueOf((long) result);
        }
        return BigDecimal.valueOf(result).setScale(2, RoundingMode.HALF_UP).toPlainString();
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
