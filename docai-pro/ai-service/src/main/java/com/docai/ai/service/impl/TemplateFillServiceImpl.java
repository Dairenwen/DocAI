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
import org.apache.poi.ss.usermodel.DateUtil;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.*;
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

        // 将文件持久化保存到 data/local-oss/template_files/ 目录
        Path savedPath;
        String ossKey = "";
        try {
            Path persistDir = java.nio.file.Paths.get("data", "local-oss", "template_files").toAbsolutePath().normalize();
            Files.createDirectories(persistDir);
            String safeName = java.nio.file.Paths.get(originalFilename).getFileName().toString()
                    .replace("\\", "_").replace("/", "_").replace(":", "_");
            savedPath = persistDir.resolve(safeName);
            if (Files.exists(savedPath)) {
                int ld = safeName.lastIndexOf('.');
                String n = ld > 0 ? safeName.substring(0, ld) : safeName;
                String e = ld > 0 ? safeName.substring(ld) : "";
                safeName = n + "_" + System.currentTimeMillis() + e;
                savedPath = persistDir.resolve(safeName);
            }
            try (var is = file.getInputStream()) {
                Files.copy(is, savedPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            ossKey = "template_files/" + safeName;
            log.info("模板文件已持久保存: {}", savedPath);
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
    public Map<String, Object> autoFill(Long templateId, List<Long> docIds, Long userId, String userRequirement) {
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

        // 当提取字段为空时，检查是否有header_below槽位可通过LLM直接从源文档提取
        boolean hasHeaderBelowSlots2 = slots.stream().anyMatch(s -> "header_below".equals(s.getSlotType()));
        if (allFields.isEmpty() && !hasHeaderBelowSlots2) {
            throw new RuntimeException("数据源文档中未提取到任何字段信息，无法进行自动填表");
        }
        // 如果allFields为空但有header_below槽位，创建一个伪字段以传递源文档信息
        List<Long> sourceDocIdsForFill = new ArrayList<>();
        if (allFields.isEmpty() && hasHeaderBelowSlots2) {
            log.info("提取字段为空但有数据表槽位，将通过LLM直接从源文档提取结构化数据");
            // 获取源文档ID列表
            List<SourceDocumentEntity> userDocs2 = sourceDocumentMapper.selectList(
                    new LambdaQueryWrapper<SourceDocumentEntity>()
                            .eq(SourceDocumentEntity::getUserId, userId)
                            .eq(SourceDocumentEntity::getUploadStatus, "parsed")
            );
            long fakeId = -1;
            for (SourceDocumentEntity doc : userDocs2) {
                sourceDocIdsForFill.add(doc.getId());
                // 创建伪字段以携带文档信息
                ExtractedFieldEntity placeholder = new ExtractedFieldEntity();
                placeholder.setId(fakeId--);
                placeholder.setDocId(doc.getId());
                placeholder.setFieldKey("_source_doc");
                placeholder.setFieldName("_source_doc");
                placeholder.setFieldValue(doc.getFileName());
                placeholder.setFieldType("text");
                placeholder.setConfidence(BigDecimal.ZERO);
                allFields.add(placeholder);
            }
        }

        // === 预去重：按 (fieldKey, fieldName, fieldValue) 分组，保留置信度最高的字段 ===
        // 同时过滤掉无效/占位符值
        Set<String> garbageValues = Set.of("—", "–", "-", "无", "N/A", "n/a", "null", "未知", "暂无", "待填写", "未填写", "/", "None", "none", "");
        Map<String, ExtractedFieldEntity> deduplicatedMap = new LinkedHashMap<>();
        for (ExtractedFieldEntity f : allFields) {
            if (f.getFieldValue() == null || f.getFieldValue().trim().isEmpty()) continue;
            if (garbageValues.contains(f.getFieldValue().trim())) continue;
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

        Map<Long, Double> requirementDocScores = buildRequirementDocScores(allFields, userRequirement);

        // === 阶段预处理：根据槽位标签预先构建字段索引，加速候选召回 ===
        // 按 fieldKey 索引字段，使候选召回时 O(1) 查找
        Map<String, List<ExtractedFieldEntity>> fieldsByKey = new HashMap<>();
        Map<String, List<ExtractedFieldEntity>> fieldsByName = new HashMap<>();
        for (ExtractedFieldEntity f : allFields) {
            if (f.getFieldKey() != null) {
                fieldsByKey.computeIfAbsent(f.getFieldKey(), k -> new ArrayList<>()).add(f);
            }
            if (f.getFieldName() != null) {
                fieldsByName.computeIfAbsent(f.getFieldName().trim().toLowerCase(), k -> new ArrayList<>()).add(f);
            }
        }
        // 预计算每个槽位标签对应的标准化 key，用于快速匹配
        Map<String, String> slotLabelToStdKey = new HashMap<>();
        for (TemplateSlotEntity slot : slots) {
            String stdKey = normalizeLabel(slot.getLabel());
            slotLabelToStdKey.put(slot.getLabel(), stdKey);
            // 预热：确保该标准key在fieldsByKey中已建立索引
            if (!fieldsByKey.containsKey(stdKey)) {
                // 通过别名词典尝试找到对应字段
                for (Map.Entry<String, List<String>> aliasEntry : aliasDict.entrySet()) {
                    if (aliasEntry.getKey().equals(stdKey)) {
                        for (String alias : aliasEntry.getValue()) {
                            List<ExtractedFieldEntity> matched = fieldsByName.get(alias.toLowerCase());
                            if (matched != null) {
                                fieldsByKey.computeIfAbsent(stdKey, k -> new ArrayList<>()).addAll(matched);
                            }
                        }
                        break;
                    }
                }
            }
        }
        log.info("字段索引预构建完成: fieldsByKey={}个key, fieldsByName={}个key, slotLabelToStdKey={}个映射",
                fieldsByKey.size(), fieldsByName.size(), slotLabelToStdKey.size());

        // 全局计时器：从此刻开始计算总填表时间（含表头引导提取）
        long fillStartTime = System.currentTimeMillis();
        long maxFillTimeMs = 45_000; // 45秒总上限（含表头引导提取+填表循环+写回）

        // === 表头引导提取：根据模板表头去源文档中查找并补充提取 ===
        // 对于非结构化文档(md/txt/docx)，按模板表头搜索源文档内容，提取上下文并补充字段
        try {
            List<ExtractedFieldEntity> headerGuided = headerGuidedExtraction(slots, docIds, userId, allFields, slotLabelToStdKey, userRequirement, fillStartTime, maxFillTimeMs);
            if (!headerGuided.isEmpty()) {
                log.info("表头引导提取补充: 补充了{}个字段", headerGuided.size());
                for (ExtractedFieldEntity f : headerGuided) {
                    // 避免重复：检查是否已有同名同值字段
                    boolean duplicate = allFields.stream().anyMatch(existing ->
                            Objects.equals(existing.getFieldKey(), f.getFieldKey())
                            && Objects.equals(existing.getFieldValue(), f.getFieldValue()));
                    if (!duplicate) {
                        allFields.add(f);
                        if (f.getFieldKey() != null) {
                            fieldsByKey.computeIfAbsent(f.getFieldKey(), k -> new ArrayList<>()).add(f);
                        }
                        if (f.getFieldName() != null) {
                            fieldsByName.computeIfAbsent(f.getFieldName().trim().toLowerCase(), k -> new ArrayList<>()).add(f);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("表头引导提取异常(不影响主流程): {}", e.getMessage());
        }

        // 生成审计批次ID
        String auditId = "audit_" + templateId + "_" + System.currentTimeMillis();

        // 清理该模板的旧填表决策和审计日志，避免数据残留
        fillDecisionMapper.delete(
                new LambdaQueryWrapper<FillDecisionEntity>().eq(FillDecisionEntity::getTemplateId, templateId)
        );
        fillAuditLogMapper.delete(
                new LambdaQueryWrapper<FillAuditLogEntity>().eq(FillAuditLogEntity::getTemplateId, templateId)
        );

        // 对每个槽位进行候选召回和决策
        log.info("开始填表: templateId={}, 槽位数={}, 候选字段数(去重后)={}, 别名词典数={}, 用户需求={}", templateId, slots.size(), allFields.size(), aliasDict.size(), userRequirement);
        List<FillDecisionEntity> decisions = new ArrayList<>();
        List<FillAuditLogEntity> auditLogs = new ArrayList<>();
        int filledCount = 0;
        int blankCount = 0;

        // 跟踪同名槽位已使用的值，用于多表场景中避免重复填入相同值
        Map<String, Set<String>> labelUsedValues = new HashMap<>();

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

            // header_below槽位跳过LLM逐槽匹配，由directTableCopy或llmMultiRowFill在写回阶段处理
            if ("header_below".equals(slot.getSlotType()) && hasHeaderBelowSlots) {
                // 先尝试候选召回
                List<CandidateResult> candidates = recallCandidates(slot, allFields, allFieldsRaw, aliasDict, userRequirement, requirementDocScores);
                FillDecisionEntity decision = new FillDecisionEntity();
                decision.setSlotId(slot.getId().toString());
                decision.setSlotLabel(slot.getLabel());
                if (!candidates.isEmpty()) {
                    // 保存最佳候选作为备用
                    CandidateResult top = candidates.get(0);
                    decision.setFinalValue(top.field.getFieldValue()); // 先保存备用值，directTableCopy成功时会覆盖
                    decision.setFinalConfidence(BigDecimal.valueOf(top.score).setScale(4, RoundingMode.HALF_UP));
                    decision.setDecisionMode("direct_copy_pending");
                    decision.setReason("header_below槽位，等待directTableCopy，备用候选: " + top.field.getFieldValue());
                } else {
                    decision.setFinalValue("");
                    decision.setFinalConfidence(BigDecimal.ZERO);
                    decision.setDecisionMode("direct_copy_pending");
                    decision.setReason("header_below槽位，等待directTableCopy从源Excel复制数据");
                }
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
            List<CandidateResult> candidates = recallCandidates(slot, allFields, allFieldsRaw, aliasDict, userRequirement, requirementDocScores);

            // 多表去重：如果同一标签已在其他表中使用某个值，优先选择未使用的候选
            String slotLabel = slot.getLabel().trim();
            Set<String> usedVals = labelUsedValues.getOrDefault(slotLabel, Collections.emptySet());
            if (!usedVals.isEmpty() && candidates.size() > 1) {
                // 将未使用过的候选排在前面
                List<CandidateResult> unused = candidates.stream()
                        .filter(c -> !usedVals.contains(c.field.getFieldValue().trim()))
                        .collect(Collectors.toList());
                if (!unused.isEmpty()) {
                    List<CandidateResult> reordered = new ArrayList<>(unused);
                    candidates.stream().filter(c -> usedVals.contains(c.field.getFieldValue().trim()))
                            .forEach(reordered::add);
                    candidates = reordered;
                }
            }

            // 阶段5：难例判定（超时则跳过LLM，使用总预算的70%作为截止线）
            boolean timeExceeded = (System.currentTimeMillis() - fillStartTime) > (long)(maxFillTimeMs * 0.70);
            FillDecisionEntity decision = makeDecision(slot, candidates, timeExceeded);

            // 阶段5.5：复合标签处理（如"国家/地区"→分别匹配并合并值）
            String compositeValue = resolveCompositeLabel(slot, allFields, allFieldsRaw, aliasDict, userRequirement, requirementDocScores);
            if (compositeValue != null && !compositeValue.isEmpty()) {
                decision.setFinalValue(compositeValue);
                decision.setFinalConfidence(BigDecimal.valueOf(0.85).setScale(4, RoundingMode.HALF_UP));
                decision.setDecisionMode("composite_match");
                decision.setReason("复合标签拆分匹配: " + slot.getLabel() + " → " + compositeValue);
            }

            // 阶段6：置信度融合
            applyConfidenceThreshold(decision);
            log.info("  决策结果: label={}, value={}, confidence={}, mode={}", 
                decision.getSlotLabel(), decision.getFinalValue(), decision.getFinalConfidence(), decision.getDecisionMode());

            decision.setTemplateId(templateId);
            decision.setAuditId(auditId);
            fillDecisionMapper.insert(decision);
            decisions.add(decision);

            // 记录已使用的值，供同名槽位多表去重
            if (decision.getFinalValue() != null && !decision.getFinalValue().isEmpty()) {
                labelUsedValues.computeIfAbsent(slotLabel, k -> new HashSet<>())
                        .add(decision.getFinalValue().trim());
            }

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

        // 兜底：对剩余空白槽位尝试贪心直接匹配（通过normalizeLabel对齐）
        if (blankCount > 0 && !allFields.isEmpty()) {
            log.info("存在{}个未匹配槽位，启用贪心匹配兜底", blankCount);
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

        // 终极兜底：对剩余空白槽位使用LLM从源文档直接提取（仅在时间充裕时执行）
        long elapsedBeforeFallback = System.currentTimeMillis() - fillStartTime;
        if (blankCount > 0 && !allFields.isEmpty() && elapsedBeforeFallback < maxFillTimeMs * 0.55) {
            log.info("贪心匹配后仍有{}个空白槽位，启用LLM终极兜底(已耗时{}ms)", blankCount, elapsedBeforeFallback);
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
                String requirementHint = "";
                if (userRequirement != null && !userRequirement.isBlank()) {
                    requirementHint = "\n\n用户需求条件：" + userRequirement +
                        "\n请只提取满足上述条件（如指定的时间、地点、日期、类型等）的数据。";
                }
                String llmPrompt = "请根据以下数据源内容，提取指定字段的值。\n\n数据源内容：\n" + content +
                    requirementHint +
                    "\n\n需要提取的字段：\n" + slotLabels +
                    "\n请以JSON格式返回，key为字段名，value为从数据源中提取到的值。如果找不到对应内容则value为空字符串。" +
                    "\n只返回JSON，不要任何其他文字。示例：{\"项目名称\":\"xxx\",\"负责人\":\"xxx\"}";
                String llmResp = safeLlmCall(llmPrompt, 12_000);
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

        // 强兜底：若仍有空白槽位，使用需求感知的最优候选强制回填，避免可用数据下出现空值
        if (blankCount > 0 && !allFields.isEmpty()) {
            log.info("启用强兜底非空填充: blankCount={}", blankCount);
            for (int i = 0; i < slots.size(); i++) {
                FillDecisionEntity decision = decisions.get(i);
                if (decision.getFinalValue() != null && !decision.getFinalValue().isBlank()) {
                    continue;
                }

                TemplateSlotEntity slot = slots.get(i);
                CandidateResult forced = selectBestMandatoryCandidate(slot, allFields, allFieldsRaw, aliasDict, userRequirement, requirementDocScores);
                if (forced == null || !isMeaningfulValue(forced.field.getFieldValue())) {
                    continue;
                }

                double forcedConfidence = Math.max(0.35, Math.min(0.78, forced.score));
                decision.setFinalValue(forced.field.getFieldValue().trim());
                decision.setFinalFieldId(forced.field.getId() != null ? forced.field.getId().toString() : null);
                decision.setFinalConfidence(BigDecimal.valueOf(forcedConfidence).setScale(4, RoundingMode.HALF_UP));
                decision.setDecisionMode((userRequirement != null && !userRequirement.isBlank()) ? "requirement_force_fill" : "mandatory_fallback");
                decision.setReason("强兜底非空填充: " + (forced.field.getFieldName() != null ? forced.field.getFieldName() : "候选字段")
                        + " (score=" + String.format("%.2f", forced.score) + ")");
                fillDecisionMapper.updateById(decision);
                filledCount++;
                blankCount--;
            }
            log.info("强兜底结束: filled={}, blank={}", filledCount, blankCount);
        }

        // 阶段7：模板写回
        String outputPath = null;
        try {
            // 收集header_below槽位的源文档路径，用于直接表格数据复制
            List<String> sourceExcelPaths = new ArrayList<>();
            if (hasHeaderBelowSlots) {
                List<Long> fieldDocIds2 = allFields.stream().map(ExtractedFieldEntity::getDocId).distinct().toList();
                for (Long did : fieldDocIds2) {
                    SourceDocumentEntity srcDoc = sourceDocumentMapper.selectById(did);
                    if (srcDoc == null || srcDoc.getStoragePath() == null) continue;
                    String sp = srcDoc.getStoragePath();
                    if (!(sp.endsWith(".xlsx") || sp.endsWith(".xls") || sp.endsWith(".csv"))) continue;

                    // 检查临时文件是否仍存在，不存在则尝试从OSS恢复
                    if (!new File(sp).exists() && srcDoc.getOssKey() != null && !srcDoc.getOssKey().isEmpty()) {
                        try {
                            var ossObj = ossService.getObject(srcDoc.getOssKey());
                            Path recovered = Files.createTempDirectory("docai_recovered_").resolve(srcDoc.getFileName());
                            try (var in = ossObj.getObjectContent()) {
                                Files.copy(in, recovered, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            }
                            sp = recovered.toString();
                            // 更新存储路径
                            srcDoc.setStoragePath(sp);
                            sourceDocumentMapper.updateById(srcDoc);
                            log.info("从OSS恢复源文件: {} -> {}", srcDoc.getOssKey(), sp);
                        } catch (Exception ex) {
                            log.warn("从OSS恢复源文件失败: {}", ex.getMessage());
                            continue;
                        }
                    }
                    if (new File(sp).exists()) {
                        sourceExcelPaths.add(sp);
                    }
                }
            }

            // 不做统计聚合，直接将数据复制到模板中
            outputPath = writeBack(tpl, slots, decisions, sourceExcelPaths, userRequirement, allFields);

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
            // 记录已通过表头检测产生header_below的table，避免重复创建below_blank槽位
            Set<Integer> headerDetectedTables = new HashSet<>();

            // 第一轮：表头行检测（类似Excel rule 4）
            for (int t = 0; t < tables.size(); t++) {
                XWPFTable table = tables.get(t);
                List<XWPFTableRow> rows = table.getRows();
                if (rows.size() < 2) continue; // 至少需要1个表头行+1个数据行

                // 检查第0行是否为表头（绝大多数cell为label且非空）
                XWPFTableRow firstRow = rows.get(0);
                var firstCells = firstRow.getTableCells();
                int nonEmpty = 0, labelCount = 0;
                for (var cell : firstCells) {
                    String text = cell.getText().trim();
                    if (!text.isEmpty()) {
                        nonEmpty++;
                        if (isLabelCell(text)) labelCount++;
                    }
                }
                // 表头条件：至少3个非空cell且80%以上为label
                if (nonEmpty < 3 || labelCount < nonEmpty * 0.8) continue;

                // 检查第1行是否为空（data row待填充）
                XWPFTableRow secondRow = rows.get(1);
                var secondCells = secondRow.getTableCells();
                int emptyCount = 0;
                for (var cell : secondCells) {
                    if (cell.getText().trim().isEmpty()) emptyCount++;
                }
                if (emptyCount < secondCells.size() * 0.6) continue; // 至少60%为空才是数据行

                log.info("Word表头行检测: table={}, 表头列数={}, 数据行数={}", t, nonEmpty, rows.size() - 1);
                headerDetectedTables.add(t);

                // 为每个header列创建header_below槽位，指向第1行
                for (int c = 0; c < firstCells.size(); c++) {
                    String headerText = firstCells.get(c).getText().trim();
                    if (headerText.isEmpty()) continue;

                    TemplateSlotEntity slot = new TemplateSlotEntity();
                    slot.setTemplateId(templateId);
                    slot.setLabel(headerText);
                    slot.setContext("Table" + t);
                    slot.setPosition("{\"table\":" + t + ",\"row\":1,\"col\":" + c + "}");
                    slot.setExpectedType(guessType(headerText));
                    slot.setRequired(true);
                    slot.setSlotType("header_below");
                    slots.add(slot);
                    log.info("解析槽位: label={}, position={}, type={}, slotType=header_below", headerText, slot.getPosition(), slot.getExpectedType());
                }
            }

            // 第二轮：常规槽位检测（跳过已检测为header的table）
            for (int t = 0; t < tables.size(); t++) {
                if (headerDetectedTables.contains(t)) continue;

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
                                                    Map<String, List<String>> aliasDict,
                                                    String userRequirement,
                                                    Map<Long, Double> requirementDocScores) {
        List<CandidateResult> candidates = new ArrayList<>();
        String slotLabel = slot.getLabel().trim();
        String slotLabelStd = normalizeLabel(slotLabel);
        List<String> reqTokens = extractRequirementTokens(userRequirement);
        boolean hasRequirement = !reqTokens.isEmpty();

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
            double reqScore = 0.0;

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
                        // 部分字符重叠匹配 — 对短标签(<=2字符)禁用，避免误匹配
                        int minLen = Math.min(slotLabel.length(), fn.length());
                        if (minLen > 2) {
                            int overlap = countOverlapChars(slotLabel, fn);
                            if (overlap >= 3 && overlap >= minLen * 0.6) {
                                aliasScore = 0.35;
                            }
                        }
                    }
                }
                // 复合标签子项匹配: "国家/地区"→分别检查"国家"和"地区"
                if (aliasScore == 0.0 && (slotLabel.contains("/") || slotLabel.contains("／"))) {
                    String[] subLabels = slotLabel.split("[/／]");
                    for (String sub : subLabels) {
                        sub = sub.trim();
                        if (sub.isEmpty()) continue;
                        String fn2 = field.getFieldName() != null ? field.getFieldName().trim() : "";
                        if (sub.equals(fn2) || fn2.contains(sub) || sub.contains(fn2)) {
                            aliasScore = 0.55;
                            break;
                        }
                        String subStd = normalizeLabel(sub);
                        String fStd = field.getFieldKey() != null ? field.getFieldKey() : fieldNameNorm;
                        if (!subStd.equals(sub.toLowerCase()) && subStd.equals(fStd)) {
                            aliasScore = 0.85;
                            break;
                        }
                    }
                }
            }

            // 2. 类型匹配 + 值类型验证
            if (slot.getExpectedType() != null && field.getFieldType() != null) {
                if (slot.getExpectedType().equals(field.getFieldType())) {
                    typeScore = 1.0;
                } else if ("text".equals(slot.getExpectedType())) {
                    typeScore = 0.3; // text类型兼容性较高
                }
            } else {
                typeScore = 0.3; // 类型未知时给低分
            }
            // 值类型交叉验证：如果值明显不符合槽位期望类型，强降权
            if (slot.getExpectedType() != null && field.getFieldValue() != null) {
                String ev = slot.getExpectedType();
                String fv = field.getFieldValue().trim();
                if ("number".equals(ev) && !fv.matches(".*\\d+.*")) {
                    typeScore = -0.3; // 数字类型槽位但值不含任何数字
                } else if ("date".equals(ev) && !fv.matches(".*\\d{2,}.*")) {
                    typeScore = -0.2; // 日期类型槽位但值不含数字
                } else if ("phone".equals(ev) && !fv.matches(".*\\d{5,}.*")) {
                    typeScore = -0.3; // 电话类型但值不含足够数字
                } else if ("person".equals(ev) && fv.matches("^\\d{6,}$")) {
                    typeScore = -0.3; // 人名类型但值全是数字
                }
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

            // 4c. 用户需求强匹配：匹配则显著加分，不匹配则显著降权
            if (hasRequirement) {
                double fieldReqScore = computeRequirementMatchScore(field, reqTokens);
                double docReqScore = requirementDocScores != null
                        ? requirementDocScores.getOrDefault(field.getDocId(), 0.0)
                        : 0.0;
                reqScore = Math.max(fieldReqScore, docReqScore * 0.9);
            }

            // 计算综合得分 — 当有用户需求时提高需求匹配权重
            double baseReqWeight = hasRequirement ? 0.22 : 0.14;
            double baseAliasWeight = hasRequirement ? 0.30 : 0.34;
            double score = baseAliasWeight * aliasScore + 0.15 * typeScore + 0.13 * voteScore + 0.10 * contextScore + 0.08 * confScore + baseReqWeight * reqScore;

            if (hasRequirement && reqScore <= -0.50 && aliasScore < 0.50) {
                continue;
            }

            // 有需求时允许低分候选进入排序，后续再用需求得分筛选；无需求保持原有阈值
            if (score > (hasRequirement ? -0.15 : 0.05)) {
                CandidateResult cr = new CandidateResult();
                cr.field = field;
                cr.score = score;
                cr.aliasScore = aliasScore;
                cr.typeScore = typeScore;
                cr.voteScore = voteScore;
                cr.contextScore = contextScore;
                cr.confScore = confScore;
                cr.reqScore = reqScore;
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

        if (hasRequirement && !deduped.isEmpty()) {
            List<CandidateResult> requirementPreferred = deduped.stream()
                    .filter(c -> c.reqScore > 0.0)
                    .collect(Collectors.toList());
            if (!requirementPreferred.isEmpty()) {
                return requirementPreferred;
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

        // 别名弱匹配时，只有在需求匹配也弱的情况下才拒填
        if (top1.aliasScore < 0.1 && top1.reqScore < 0.40) {
            decision.setFinalValue("");
            decision.setFinalConfidence(BigDecimal.ZERO);
            decision.setDecisionMode("fallback_blank");
            decision.setReason("无匹配字段名: 最佳候选 " + top1.field.getFieldName()
                    + " (aliasScore=" + String.format("%.2f", top1.aliasScore)
                    + ", reqScore=" + String.format("%.2f", top1.reqScore)
                    + ", score=" + String.format("%.2f", top1.score) + ")");
            return decision;
        }

        boolean needLlm = false;

        // 判定是否需要调用LLM
        if (candidates.size() >= 2) {
            CandidateResult top2 = candidates.get(1);
            // 条件1: Top1和Top2分差 < 0.05 且候选值不同 且Top1别名得分不高
            if (top1.score - top2.score < 0.05
                    && !Objects.equals(top1.field.getFieldValue(), top2.field.getFieldValue())
                    && top1.aliasScore < 0.80) needLlm = true;
        }
        // 条件2: Top1分数太低且别名中等
        if (top1.score < 0.30 && top1.aliasScore < 0.4) needLlm = true;
        // 跳过LLM：当别名完全匹配(>=0.90)时规则结果已足够可靠
        if (top1.aliasScore >= 0.90) needLlm = false;

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

        // 纯规则决策（此处aliasScore >= 0.1已在前面保证）
        decision.setFinalValue(top1.field.getFieldValue());
        decision.setFinalFieldId(top1.field.getId() != null ? top1.field.getId().toString() : null);
        decision.setFinalConfidence(BigDecimal.valueOf(top1.score).setScale(4, RoundingMode.HALF_UP));
        decision.setDecisionMode("rule_only");
        decision.setReason("规则Top1候选: " + top1.field.getFieldName() + " (score=" + String.format("%.2f", top1.score) + ")");

        return decision;
    }

    // ===== 阶段6：置信度阈值 =====

    private void applyConfidenceThreshold(FillDecisionEntity decision) {
        double conf = decision.getFinalConfidence() != null ? decision.getFinalConfidence().doubleValue() : 0;
        if (conf < 0.05) {
            decision.setFinalValue(""); // 拒填：置信度极低，避免错填
            decision.setDecisionMode("fallback_blank");
            decision.setReason(decision.getReason() + " [置信度=" + String.format("%.2f", conf) + " < 0.05, 拒填]");
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
                    .append("\"fieldId\":\"").append(cr.field.getId() != null ? cr.field.getId() : "").append("\",")
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

        String response = safeLlmCall(prompt, 10_000);
        if (response == null || response.isBlank()) {
            log.warn("LLM判定调用无响应，回退规则决策");
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
            // 验证LLM返回值有效性
            if (judgment.selectedValue == null || judgment.selectedValue.isEmpty()
                    || judgment.modelConfidence <= 0) {
                log.warn("LLM返回无效值或零置信度，回退规则决策");
                return null;
            }
            // 过滤占位符值
            Set<String> invalid = Set.of("—", "–", "-", "无", "N/A", "null", "未知", "暂无");
            if (invalid.contains(judgment.selectedValue.trim())) {
                log.warn("LLM返回占位符值[{}]，回退规则决策", judgment.selectedValue);
                return null;
            }
            return judgment;
        } catch (Exception e) {
            log.warn("解析LLM判定结果失败: {}", e.getMessage());
            return null;
        }
    }

    // ===== 阶段7：模板写回 =====

    private String writeBack(TemplateFileEntity tpl, List<TemplateSlotEntity> slots, List<FillDecisionEntity> decisions, List<String> sourceExcelPaths, String userRequirement, List<ExtractedFieldEntity> allFields) throws Exception {
        // 构建slotId -> decision的映射（使用slotId避免label重复冲突）
        Map<String, FillDecisionEntity> decisionMap = new HashMap<>();
        for (FillDecisionEntity d : decisions) {
            decisionMap.put(d.getSlotId(), d);
        }

        String outputPath;
        if ("xlsx".equals(tpl.getTemplateType())) {
            outputPath = writeBackExcel(tpl.getStoragePath(), slots, decisionMap, sourceExcelPaths, userRequirement, allFields);
        } else {
            outputPath = writeBackWord(tpl.getStoragePath(), slots, decisionMap, sourceExcelPaths, userRequirement, allFields);
        }

        tpl.setOutputPath(outputPath);
        templateFileMapper.updateById(tpl);

        return outputPath;
    }

    private String writeBackExcel(String templatePath, List<TemplateSlotEntity> slots, Map<String, FillDecisionEntity> decisionMap, List<String> sourceExcelPaths, String userRequirement, List<ExtractedFieldEntity> allFields) throws Exception {
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
            RequirementFilter requirementFilter = parseRequirementFilter(userRequirement);

            if (!headerSlots.isEmpty() && sourceExcelPaths != null && !sourceExcelPaths.isEmpty()) {
                directCopyDone = directTableCopy(workbook, headerSlots, sourceExcelPaths, decisionMap, requirementFilter);
            }

            // === 多行数据表LLM提取：从非Excel源文档(docx/md/txt/pdf)中提取结构化多行数据并追加到表格 ===
            // 不论directTableCopy是否成功，都检查是否有非Excel源文档需要通过LLM提取
            if (!headerSlots.isEmpty() && allFields != null && !allFields.isEmpty()) {
                // 筛选非Excel源文档的字段（当directCopyDone时只用非Excel文档，否则用所有文档）
                List<ExtractedFieldEntity> nonExcelFields = allFields;
                if (directCopyDone) {
                    Set<Long> excelDocIds = new HashSet<>();
                    for (ExtractedFieldEntity f : allFields) {
                        SourceDocumentEntity srcDoc = sourceDocumentMapper.selectById(f.getDocId());
                        if (srcDoc != null && srcDoc.getStoragePath() != null) {
                            String sp = srcDoc.getStoragePath().toLowerCase();
                            if (sp.endsWith(".xlsx") || sp.endsWith(".xls") || sp.endsWith(".csv")) {
                                excelDocIds.add(f.getDocId());
                            }
                        }
                    }
                    if (!excelDocIds.isEmpty()) {
                        nonExcelFields = allFields.stream()
                                .filter(f -> !excelDocIds.contains(f.getDocId()))
                                .collect(Collectors.toList());
                    }
                }

                if (!nonExcelFields.isEmpty()) {
                    boolean multiRowDone = llmMultiRowFill(workbook, headerSlots, nonExcelFields, userRequirement);
                    if (multiRowDone) {
                        directCopyDone = true;
                        log.info("LLM多行数据表填充完成");
                    }
                }
            }

            // 写入单值决策（对于非header_below槽位，或直接复制失败的情况）
            int writtenCount = 0;
            // 收集header_below列名，用于去重
            Set<String> headerBelowLabels = directCopyDone ? headerSlots.stream().map(s -> s.getLabel().trim()).collect(Collectors.toSet()) : Collections.emptySet();
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

                String fillValue = decision.getFinalValue();

                // 当LLM多行填充已完成时，与header_below同名的adjacent_blank槽位只取首行值
                if (directCopyDone && headerBelowLabels.contains(slot.getLabel().trim()) && fillValue.contains("\n")) {
                    fillValue = fillValue.split("\n")[0].trim();
                }

                // 复合标签：预处理分隔符统一为\n
                boolean isSlashLabel = slot.getLabel() != null && (slot.getLabel().contains("/") || slot.getLabel().contains("／"));
                if (isSlashLabel && !fillValue.contains("\n")) {
                    // 将常见分隔符统一为换行符
                    if (fillValue.contains("、") || fillValue.contains("，") || fillValue.contains("; ")) {
                        fillValue = fillValue.replaceAll("[、，]|; ", "\n");
                    }
                }

                // 复合标签或多行值 → 每个值占一行（不合并到同一单元格）
                boolean isCompositeMultiLine = fillValue.contains("\n") && 
                    ("composite_match".equals(decision.getDecisionMode()) || isSlashLabel);
                if (isCompositeMultiLine) {
                    String[] lines = fillValue.split("\n");
                    // 第一个值写在当前行
                    cell.setCellValue(lines[0]);
                    // 后续值写在下方的行
                    for (int li = 1; li < lines.length; li++) {
                        int newRowIdx = row + li;
                        XSSFRow newRow = sheet.getRow(newRowIdx);
                        if (newRow == null) newRow = sheet.createRow(newRowIdx);
                        XSSFCell newCell = newRow.getCell(col);
                        if (newCell == null) newCell = newRow.createCell(col);
                        if (newCell.getCellType() != CellType.FORMULA) {
                            String lineVal = lines[li].trim();
                            if (!lineVal.isEmpty() && !"0".equals(lineVal)) {
                                newCell.setCellValue(lineVal);
                            }
                        }
                    }
                } else if ("inline".equals(slot.getSlotType())) {
                    String existing = getCellStringValue(cell);
                    cell.setCellValue(existing + fillValue);
                } else {
                    // 非空值、且不为"0"时才写入
                    if (!"0".equals(fillValue.trim())) {
                        cell.setCellValue(fillValue);
                    }
                }
                writtenCount++;
                log.info("写入单元格: sheet={}, row={}, col={}, label={}, value={}", sheetName, row, col, slot.getLabel(), decision.getFinalValue());
            }

            // === 行级数据对齐：补填composite_match扩展行中的空缺单元格 ===
            // 概念：当某列写了多行数据时，同行其他列可能为空。从提取字段中按源行关联补填。
            if (allFields != null && !allFields.isEmpty()) {
                // 构建 slot标签 → col 的映射
                Map<String, Integer> labelToCol = new LinkedHashMap<>();
                Map<String, String> labelToSheet = new LinkedHashMap<>();
                Map<String, Integer> labelToBaseRow = new LinkedHashMap<>();
                for (TemplateSlotEntity slot : slots) {
                    JSONObject pos = JSON.parseObject(slot.getPosition());
                    labelToCol.put(slot.getLabel().trim(), pos.getIntValue("col"));
                    labelToSheet.put(slot.getLabel().trim(), pos.getString("sheet"));
                    labelToBaseRow.put(slot.getLabel().trim(), pos.getIntValue("row"));
                }

                // 按源文档行号分组提取字段（利用sourceLocation中的行号信息）
                Map<String, List<ExtractedFieldEntity>> fieldsBySourceRow = new LinkedHashMap<>();
                for (ExtractedFieldEntity f : allFields) {
                    if (f.getSourceLocation() == null || f.getFieldValue() == null || f.getFieldValue().isBlank()) continue;
                    // 提取源行标识：如 "table_row_3", "docx_table_data_row"
                    String rowKey = f.getDocId() + "_" + f.getSourceLocation().replaceAll("\\s+", "");
                    fieldsBySourceRow.computeIfAbsent(rowKey, k -> new ArrayList<>()).add(f);
                }

                // 关键字段标准化key → slot标签 映射
                Map<String, String> stdKeyToLabel = new HashMap<>();
                for (TemplateSlotEntity slot : slots) {
                    String stdKey = normalizeLabel(slot.getLabel());
                    stdKeyToLabel.putIfAbsent(stdKey, slot.getLabel().trim());
                    // 对复合标签也映射子项
                    String lbl = slot.getLabel().trim();
                    if (lbl.contains("/") || lbl.contains("／")) {
                        for (String part : lbl.split("[/／]")) {
                            part = part.trim();
                            if (!part.isEmpty()) {
                                String partKey = normalizeSingleLabel(part);
                                stdKeyToLabel.putIfAbsent(partKey, lbl);
                            }
                        }
                    }
                }

                // 对每个有多行数据的源行组，尝试补填空缺列
                for (Map.Entry<String, List<ExtractedFieldEntity>> rowGroup : fieldsBySourceRow.entrySet()) {
                    List<ExtractedFieldEntity> rowFields = rowGroup.getValue();
                    if (rowFields.size() < 2) continue; // 单字段行不需要对齐

                    // 找到这组字段中每个字段对应哪个模板列
                    Map<Integer, String> colToValue = new LinkedHashMap<>();
                    String targetSheetName = null;
                    for (ExtractedFieldEntity f : rowFields) {
                        String fKey = f.getFieldKey() != null ? f.getFieldKey() : "";
                        String fName = f.getFieldName() != null ? f.getFieldName().trim() : "";
                        String fNameStd = normalizeSingleLabel(fName);

                        // 尝试找到匹配的模板标签
                        String matchedLabel = stdKeyToLabel.get(fKey);
                        if (matchedLabel == null) matchedLabel = stdKeyToLabel.get(fNameStd);
                        if (matchedLabel == null) {
                            // 直接名称匹配
                            for (Map.Entry<String, Integer> le : labelToCol.entrySet()) {
                                if (le.getKey().contains(fName) || fName.contains(le.getKey())) {
                                    matchedLabel = le.getKey();
                                    break;
                                }
                            }
                        }

                        if (matchedLabel != null && labelToCol.containsKey(matchedLabel)) {
                            int col = labelToCol.get(matchedLabel);
                            String val = f.getFieldValue().trim();
                            if (!val.isEmpty() && !"0".equals(val)) {
                                colToValue.put(col, val);
                                if (targetSheetName == null) targetSheetName = labelToSheet.get(matchedLabel);
                            }
                        }
                    }

                    if (colToValue.size() < 2 || targetSheetName == null) continue;

                    // 查找模板中这些列对应的行中是否有空缺需要补填
                    XSSFSheet sheet = workbook.getSheet(targetSheetName);
                    if (sheet == null) continue;

                    // 找到已写入数据中匹配的模板行
                    int baseRow = labelToBaseRow.values().stream().findFirst().orElse(0);
                    int maxScan = Math.min(baseRow + 50, sheet.getLastRowNum() + 1);
                    for (int ri = baseRow; ri < maxScan; ri++) {
                        XSSFRow xrow = sheet.getRow(ri);
                        if (xrow == null) continue;

                        // 检查是否有任何列的值匹配这个源行的某个值
                        boolean rowMatches = false;
                        for (Map.Entry<Integer, String> cv : colToValue.entrySet()) {
                            XSSFCell c = xrow.getCell(cv.getKey());
                            if (c != null) {
                                String existing = getCellStringValue(c);
                                if (cv.getValue().equals(existing)) {
                                    rowMatches = true;
                                    break;
                                }
                            }
                        }

                        if (rowMatches) {
                            // 补填该行中的空缺列
                            for (Map.Entry<Integer, String> cv : colToValue.entrySet()) {
                                XSSFCell c = xrow.getCell(cv.getKey());
                                if (c == null) c = xrow.createCell(cv.getKey());
                                String existing = getCellStringValue(c);
                                if (existing.isEmpty() && c.getCellType() != CellType.FORMULA) {
                                    c.setCellValue(cv.getValue());
                                    writtenCount++;
                                    log.info("行对齐补填: sheet={}, row={}, col={}, value={}", targetSheetName, ri, cv.getKey(), cv.getValue());
                                }
                            }
                            break;
                        }
                    }
                }
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
     * 支持多源文件追加复制。
     */
    private boolean directTableCopy(XSSFWorkbook templateWorkbook, List<TemplateSlotEntity> headerSlots, List<String> sourceExcelPaths, Map<String, FillDecisionEntity> decisionMap, RequirementFilter requirementFilter) {
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
            // 检查源文件是否存在
            if (!new File(srcPath).exists()) {
                log.warn("直接表格复制: 源文件不存在(可能已被清理): {}", srcPath);
                continue;
            }
            try (FileInputStream srcFis = new FileInputStream(srcPath);
                 XSSFWorkbook srcWorkbook = new XSSFWorkbook(srcFis)) {

                for (int si = 0; si < srcWorkbook.getNumberOfSheets(); si++) {
                    XSSFSheet srcSheet = srcWorkbook.getSheetAt(si);
                    if (srcSheet.getLastRowNum() < 1) continue;

                    // 尝试在源表的每一行找到表头行（不一定在第0行）
                    int srcHeaderRowIdx = -1;
                    XSSFRow srcHeaderRow = null;
                    Map<Integer, Integer> colMapping = new LinkedHashMap<>();

                    for (int hr = 0; hr <= Math.min(5, srcSheet.getLastRowNum()); hr++) {
                        XSSFRow candidateRow = srcSheet.getRow(hr);
                        if (candidateRow == null) continue;
                        Map<Integer, Integer> candidateMapping = matchColumns(candidateRow, labelToCol);
                        if (candidateMapping.size() > colMapping.size()) {
                            colMapping = candidateMapping;
                            srcHeaderRowIdx = hr;
                            srcHeaderRow = candidateRow;
                        }
                    }

                    if (colMapping.isEmpty()) {
                        log.warn("直接表格复制: 源sheet={} 无匹配列，跳过此sheet", srcSheet.getSheetName());
                        continue;
                    }
                    log.info("直接表格复制: 源={}, sheet={}, 表头行={}, 匹配列数={}/{}, 数据行数={}",
                            srcPath, srcSheet.getSheetName(), srcHeaderRowIdx, colMapping.size(), labelToCol.size(), srcSheet.getLastRowNum());

                    // 复制数据行（从表头下一行开始）
                    for (int r = srcHeaderRowIdx + 1; r <= srcSheet.getLastRowNum(); r++) {
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
                        if (!rowMatchesRequirement(srcRow, rowValues, requirementFilter)) continue;

                        // 同时复制未匹配列对应的源数据（如果源行有比mapping更多的列）
                        // 对于额外的源列，按顺序写入模板中未被占用的列
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

        if (totalCopied == 0 && requirementFilter != null && !requirementFilter.isEmpty()) {
            log.warn("需求筛选未命中任何行，回退到无条件整表复制以保障多源文档合并能力");
            return directTableCopy(templateWorkbook, headerSlots, sourceExcelPaths, decisionMap, new RequirementFilter());
        }

        // 更新header_below槽位的填充决策
        if (decisionMap != null) {
            for (TemplateSlotEntity slot : headerSlots) {
                FillDecisionEntity decision = decisionMap.get(slot.getId().toString());
                if (decision == null) continue;
                if (totalCopied > 0) {
                    decision.setFinalValue("已复制" + totalCopied + "行数据");
                    decision.setFinalConfidence(new BigDecimal("0.95"));
                    decision.setDecisionMode("direct_table_copy");
                    decision.setReason("从源Excel直接复制" + totalCopied + "行数据，列: " + slot.getLabel());
                } else {
                    // 复制失败时保留备用单值，避免出现空值
                    if (decision.getFinalValue() != null && !decision.getFinalValue().isBlank()) {
                        decision.setDecisionMode("direct_copy_fallback_single");
                        decision.setReason("direct_copy未命中行，回退到单值候选填充");
                    } else {
                        decision.setFinalValue("");
                        decision.setFinalConfidence(BigDecimal.ZERO);
                        decision.setDecisionMode("fallback_blank");
                        decision.setReason("direct_copy失败(源文件不可用或列名不匹配)，且无备用候选");
                    }
                }
                fillDecisionMapper.updateById(decision);
            }
        }

        return totalCopied > 0;
    }

    private RequirementFilter parseRequirementFilter(String userRequirement) {
        RequirementFilter filter = new RequirementFilter();
        if (userRequirement == null || userRequirement.isBlank()) {
            return filter;
        }

        filter.tokens = extractRequirementTokens(userRequirement).stream()
                .filter(t -> !t.matches("^20\\d{2}([-/.年].*)?$"))
            .filter(this::isUsefulConstraintToken)
                .collect(Collectors.toList());

        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(20\\d{2}[年/-]\\d{1,2}[月/-]\\d{1,2}日?)")
                .matcher(userRequirement);
        List<LocalDate> dates = new ArrayList<>();
        while (m.find()) {
            LocalDate dt = parseDateLoose(m.group(1));
            if (dt != null) {
                dates.add(dt);
            }
        }
        if (dates.size() >= 2) {
            dates.sort(LocalDate::compareTo);
            filter.startDate = dates.get(0);
            filter.endDate = dates.get(dates.size() - 1);
        }

        return filter;
    }

    private boolean rowMatchesRequirement(XSSFRow srcRow, Map<Integer, String> rowValues, RequirementFilter filter) {
        if (filter == null || filter.isEmpty()) {
            return true;
        }

        StringBuilder allCellText = new StringBuilder();
        if (srcRow != null) {
            for (int c = 0; c < srcRow.getLastCellNum(); c++) {
                XSSFCell cell = srcRow.getCell(c);
                if (cell == null) continue;
                String val = getCellStringValue(cell);
                if (val != null && !val.isBlank()) {
                    allCellText.append(val).append(' ');
                }
            }
        }
        if (allCellText.length() == 0) {
            rowValues.values().stream().filter(Objects::nonNull).forEach(v -> allCellText.append(v).append(' '));
        }
        String joined = allCellText.toString().toLowerCase();

        if (filter.startDate != null && filter.endDate != null) {
            boolean hasDateInRange = false;
            if (srcRow != null) {
                for (int c = 0; c < srcRow.getLastCellNum(); c++) {
                    XSSFCell cell = srcRow.getCell(c);
                    if (cell == null) continue;
                    LocalDate date = null;
                    if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                        date = cell.getLocalDateTimeCellValue().toLocalDate();
                    }
                    if (date == null) {
                        date = parseDateLoose(getCellStringValue(cell));
                    }
                    if (date != null && !date.isBefore(filter.startDate) && !date.isAfter(filter.endDate)) {
                        hasDateInRange = true;
                        break;
                    }
                }
            } else {
                for (String value : rowValues.values()) {
                    LocalDate date = parseDateLoose(value);
                    if (date != null && !date.isBefore(filter.startDate) && !date.isAfter(filter.endDate)) {
                        hasDateInRange = true;
                        break;
                    }
                }
            }
            if (!hasDateInRange) {
                return false;
            }
        }

        if (filter.tokens != null && !filter.tokens.isEmpty()) {
            boolean tokenMatched = false;
            for (String token : filter.tokens) {
                if (token != null && !token.isBlank() && joined.contains(token.toLowerCase())) {
                    tokenMatched = true;
                    break;
                }
            }
            if (!tokenMatched) {
                return false;
            }
        }

        return true;
    }

    private LocalDate parseDateLoose(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String text = raw.trim();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(20\\d{2})[年/-]?(\\d{1,2})[月/-]?(\\d{1,2})")
                .matcher(text);
        if (!m.find()) return null;
        String normalized = m.group(1) + "-" + m.group(2) + "-" + m.group(3);
        try {
            return LocalDate.parse(normalized, DateTimeFormatter.ofPattern("yyyy-M-d"));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private boolean isUsefulConstraintToken(String token) {
        if (token == null || token.isBlank()) return false;
        String t = token.trim().toLowerCase();
        Set<String> stopWords = Set.of(
                "智能填表", "填表", "文件", "两个文件", "内容", "日期", "一列", "数据", "填入", "模板", "用户需求",
                "从", "到", "之间", "范围", "筛选", "过滤", "只", "仅", "优先", "以及", "并且", "要求"
        );
        if (stopWords.contains(t)) return false;
        if (t.matches(".*\\d.*")) return false;
        if (t.length() < 2) return false;
        return true;
    }

    /**
     * 匹配源Excel行的列到模板 header 列。
     * 使用多级匹配: 精确 → 忽略大小写 → 包含 → normalizeLabel
     */
    private Map<Integer, Integer> matchColumns(XSSFRow srcRow, Map<String, Integer> labelToCol) {
        Map<Integer, Integer> colMapping = new LinkedHashMap<>();
        Set<String> matched = new HashSet<>();
        for (int c = 0; c < srcRow.getLastCellNum(); c++) {
            XSSFCell hCell = srcRow.getCell(c);
            if (hCell == null) continue;
            String hVal = getCellStringValue(hCell).trim();
            if (hVal.isEmpty()) continue;
            for (Map.Entry<String, Integer> entry : labelToCol.entrySet()) {
                String tplLabel = entry.getKey();
                if (matched.contains(tplLabel)) continue;
                if (tplLabel.equals(hVal)
                        || tplLabel.equalsIgnoreCase(hVal)
                        || tplLabel.contains(hVal) || hVal.contains(tplLabel)
                        || normalizeLabel(tplLabel).equals(normalizeLabel(hVal))) {
                    colMapping.put(entry.getValue(), c);
                    matched.add(tplLabel);
                    break;
                }
                // 复合标签子项匹配：模板"国家/地区"匹配源列"国家"或"地区"
                if (tplLabel.contains("/") || tplLabel.contains("／")) {
                    boolean subMatched = false;
                    for (String sub : tplLabel.split("[/／]")) {
                        sub = sub.trim();
                        if (sub.isEmpty()) continue;
                        if (sub.equals(hVal) || sub.equalsIgnoreCase(hVal)
                                || sub.contains(hVal) || hVal.contains(sub)
                                || normalizeSingleLabel(sub).equals(normalizeSingleLabel(hVal))) {
                            subMatched = true;
                            break;
                        }
                    }
                    if (subMatched) {
                        colMapping.put(entry.getValue(), c);
                        matched.add(tplLabel);
                        break;
                    }
                }
            }
        }
        return colMapping;
    }

    /**
     * 多行数据表LLM提取填充。当模板为header_below类型但源文档不是Excel时，
     * 通过LLM从源文档文本中提取结构化表格数据，写入多行。
     */
    private boolean llmMultiRowFill(XSSFWorkbook workbook, List<TemplateSlotEntity> headerSlots,
                                     List<ExtractedFieldEntity> allFields, String userRequirement) {
        if (headerSlots.isEmpty()) return false;

        // 确定目标sheet和起始行
        JSONObject firstPos = JSON.parseObject(headerSlots.get(0).getPosition());
        String sheetName = firstPos.getString("sheet");
        int dataStartRow = firstPos.getIntValue("row"); // header_below slot指向的row就是数据起始行

        XSSFSheet sheet = workbook.getSheet(sheetName);
        if (sheet == null) return false;

        // 检查是否已有directTableCopy写入的行，确定实际写入起始行
        int actualStartRow = dataStartRow;
        int firstCol = JSON.parseObject(headerSlots.get(0).getPosition()).getIntValue("col");
        for (int r = dataStartRow; r <= sheet.getLastRowNum(); r++) {
            XSSFRow existingRow = sheet.getRow(r);
            if (existingRow == null) break;
            XSSFCell cell = existingRow.getCell(firstCol);
            if (cell == null || getCellStringValue(cell).isBlank()) break;
            actualStartRow = r + 1;
        }
        if (actualStartRow > dataStartRow) {
            log.info("LLM多行提取: 检测到已有{}行数据(directTableCopy)，将从row={}追加", actualStartRow - dataStartRow, actualStartRow);
        }

        // 收集列标签
        List<String> columnLabels = new ArrayList<>();
        Map<String, Integer> labelToCol = new LinkedHashMap<>();
        for (TemplateSlotEntity slot : headerSlots) {
            JSONObject pos = JSON.parseObject(slot.getPosition());
            String label = slot.getLabel().trim();
            int col = pos.getIntValue("col");
            columnLabels.add(label);
            labelToCol.put(label, col);
        }
        log.info("LLM多行提取: 列标签={}, 数据起始行={}", columnLabels, dataStartRow);

        // 收集源文档文本 - 优先读取原始文档
        StringBuilder sourceText = new StringBuilder();
        Set<Long> seenDocIds = new HashSet<>();
        Set<Long> docIds = allFields.stream().map(ExtractedFieldEntity::getDocId).collect(Collectors.toSet());
        for (Long did : docIds) {
            if (seenDocIds.add(did)) {
                SourceDocumentEntity srcDoc = sourceDocumentMapper.selectById(did);
                if (srcDoc != null && srcDoc.getStoragePath() != null) {
                    try {
                        String text = readDocumentText(srcDoc.getStoragePath(), srcDoc.getFileType());
                        if (text != null && !text.isBlank()) sourceText.append(text).append("\n");
                    } catch (Exception e) {
                        log.warn("读取源文档失败 docId={}: {}", did, e.getMessage());
                    }
                }
            }
        }
        // 如果原始文档读取失败，回退到sourceText字段
        if (sourceText.length() < 200) {
            for (ExtractedFieldEntity f : allFields) {
                if (f.getSourceText() != null && !f.getSourceText().isBlank()) {
                    sourceText.append(f.getSourceText()).append("\n");
                    break; // 一份文档的sourceText即可
                }
            }
        }

        String content = sourceText.toString();
        if (content.length() > 12000) content = content.substring(0, 12000);
        if (content.isBlank()) return false;

        // 构建LLM prompt，要求返回JSON数组
        String requirementHint = "";
        if (userRequirement != null && !userRequirement.isBlank()) {
            requirementHint = "\n用户筛选条件：" + userRequirement + "\n请只提取满足上述条件的数据行。";
        }

        String prompt = "你是专业的数据提取助手。请从以下文档中提取结构化表格数据。\n\n" +
                "文档内容：\n" + content + "\n\n" +
                requirementHint +
                "请按以下表头列提取数据，每个主要实体独立一行：\n" +
                "表头列：" + String.join("、", columnLabels) + "\n\n" +
                "【重要规则】\n" +
                "1. 返回JSON数组，每个元素是一个对象，key必须与表头列名完全一致\n" +
                "2. 只提取有详细数据描述的实体（如有人口、GDP等），不要提取仅被顺带提及的名称\n" +
                "3. 如果表头是'国家/地区'但文档主要描述的是省份/城市，请填写省份/城市名称，不要创建国家级汇总行\n" +
                "4. 数值只填纯数字和单位（如\"5775万\"、\"7.3万元\"），不要附加解释文字（如不要写\"68例\"而写\"68\"）\n" +
                "5. 如果原文中有中英文对照，优先使用中文（如Asia→\"亚洲\"）\n" +
                "6. 【关键】每个实体的每一列都必须认真从文档中查找对应数据，不要遗漏。仔细阅读每一段文字\n" +
                "7. 只返回JSON数组，不要任何其他文字\n\n" +
                "示例格式：[{\"列1\":\"值1\",\"列2\":\"值2\"},{\"列1\":\"值3\",\"列2\":\"值4\"}]";

        String llmResp = safeLlmCall(prompt, 45_000);
        if (llmResp == null || llmResp.isBlank()) {
            log.warn("LLM多行提取: LLM无响应");
            return false;
        }

        try {
            String jsonStr = extractJsonFromResponse(llmResp);
            com.alibaba.fastjson.JSONArray rows = JSON.parseArray(jsonStr);
            if (rows == null || rows.isEmpty()) {
                log.warn("LLM多行提取: 解析JSON数组为空");
                return false;
            }

            log.info("LLM多行提取: 成功提取{}行数据", rows.size());

            int writtenRows = 0;
            for (int i = 0; i < rows.size(); i++) {
                JSONObject rowData = rows.getJSONObject(i);
                if (rowData == null) continue;

                // 跳过汇总行：如果任何值包含换行符，说明是聚合数据
                boolean isSummaryRow = false;
                for (String key : rowData.keySet()) {
                    String v = rowData.getString(key);
                    if (v != null && v.contains("\n")) { isSummaryRow = true; break; }
                }
                if (isSummaryRow) {
                    log.info("LLM多行提取: 跳过汇总行(含换行值) index={}", i);
                    continue;
                }

                int rowIdx = actualStartRow + writtenRows;
                XSSFRow xrow = sheet.getRow(rowIdx);
                if (xrow == null) xrow = sheet.createRow(rowIdx);

                boolean rowHasData = false;
                for (Map.Entry<String, Integer> lc : labelToCol.entrySet()) {
                    String label = lc.getKey();
                    int col = lc.getValue();

                    // 尝试精确匹配和模糊匹配
                    String val = rowData.getString(label);
                    if (val == null || val.isBlank()) {
                        // 模糊匹配：检查key包含label或反之
                        for (String key : rowData.keySet()) {
                            if (key.contains(label) || label.contains(key)) {
                                val = rowData.getString(key);
                                break;
                            }
                        }
                    }

                    if (val != null && !val.isBlank() && !"未知".equals(val.trim())) {
                        XSSFCell cell = xrow.getCell(col);
                        if (cell == null) cell = xrow.createCell(col);
                        if (cell.getCellType() != CellType.FORMULA) {
                            cell.setCellValue(val.trim());
                            rowHasData = true;
                        }
                    }
                }
                if (rowHasData) writtenRows++;
            }

            log.info("LLM多行提取写入完成: 共写入{}行", writtenRows);

            // 检查是否有空单元格，如果有就进行补填
            if (writtenRows > 0) {
                List<String> missingInfo = new ArrayList<>();
                for (int r = 0; r < writtenRows; r++) {
                    int rowIdx = actualStartRow + r;
                    XSSFRow xrow = sheet.getRow(rowIdx);
                    if (xrow == null) continue;
                    // 获取第一列的值作为实体标识
                    String entityName = "";
                    Integer firstCol2 = labelToCol.values().iterator().next();
                    XSSFCell firstCell = xrow.getCell(firstCol2);
                    if (firstCell != null) entityName = firstCell.getStringCellValue();
                    if (entityName.isBlank()) continue;

                    for (Map.Entry<String, Integer> lc : labelToCol.entrySet()) {
                        XSSFCell cell = xrow.getCell(lc.getValue());
                        if (cell == null || cell.getStringCellValue().isBlank()) {
                            missingInfo.add(entityName + " -> " + lc.getKey());
                        }
                    }
                }

                if (!missingInfo.isEmpty() && missingInfo.size() <= 80) {
                    log.info("LLM多行补填: 发现{}个空单元格，发起补填请求", missingInfo.size());
                    String retryPrompt = "你是数据提取助手。以下文档中有一些数据未被完整提取。\n\n" +
                            "文档内容：\n" + content + "\n\n" +
                            "以下实体的以下列缺少数据，请从文档中查找并补充：\n" +
                            String.join("\n", missingInfo) + "\n\n" +
                            "【规则】\n" +
                            "1. 返回JSON数组，每个元素格式：{\"entity\":\"实体名\",\"column\":\"列名\",\"value\":\"值\"}\n" +
                            "2. 数值只填纯数字和单位（如\"5775万\"、\"7.3万元\"），不附加解释文字\n" +
                            "3. 如果确实找不到数据，不需要返回该条\n" +
                            "4. 只返回JSON数组，不要其他文字";

                    String retryResp = safeLlmCall(retryPrompt, 30_000);
                    if (retryResp != null && !retryResp.isBlank()) {
                        try {
                            String retryJson = extractJsonFromResponse(retryResp);
                            com.alibaba.fastjson.JSONArray fills = JSON.parseArray(retryJson);
                            if (fills != null) {
                                int filled = 0;
                                Integer firstColIdx2 = labelToCol.values().iterator().next();
                                for (int fi = 0; fi < fills.size(); fi++) {
                                    JSONObject fo = fills.getJSONObject(fi);
                                    if (fo == null) continue;
                                    String entity = fo.getString("entity");
                                    String column = fo.getString("column");
                                    String value = fo.getString("value");
                                    if (entity == null || column == null || value == null || value.isBlank()) continue;

                                    // 在已写入的行中找到匹配的实体
                                    Integer colIdx = labelToCol.get(column);
                                    if (colIdx == null) {
                                        // 模糊匹配列名
                                        for (Map.Entry<String, Integer> lc : labelToCol.entrySet()) {
                                            if (lc.getKey().contains(column) || column.contains(lc.getKey())) {
                                                colIdx = lc.getValue(); break;
                                            }
                                        }
                                    }
                                    if (colIdx == null) continue;

                                    for (int r = 0; r < writtenRows; r++) {
                                        XSSFRow xr = sheet.getRow(actualStartRow + r);
                                        if (xr == null) continue;
                                        XSSFCell nameCell = xr.getCell(firstColIdx2);
                                        if (nameCell != null && nameCell.getStringCellValue().contains(entity)) {
                                            XSSFCell targetCell = xr.getCell(colIdx);
                                            if (targetCell == null) targetCell = xr.createCell(colIdx);
                                            if (targetCell.getCellType() != CellType.FORMULA &&
                                                    (targetCell.getStringCellValue() == null || targetCell.getStringCellValue().isBlank())) {
                                                targetCell.setCellValue(value.trim());
                                                filled++;
                                            }
                                            break;
                                        }
                                    }
                                }
                                log.info("LLM多行补填完成: 补填了{}个单元格", filled);
                            }
                        } catch (Exception e) {
                            log.warn("LLM多行补填解析失败: {}", e.getMessage());
                        }
                    }
                }
            }

            return writtenRows > 0;
        } catch (Exception e) {
            log.warn("LLM多行提取解析失败: {}", e.getMessage());
            return false;
        }
    }

    private String writeBackWord(String templatePath, List<TemplateSlotEntity> slots, Map<String, FillDecisionEntity> decisionMap,
                                 List<String> sourceExcelPaths, String userRequirement, List<ExtractedFieldEntity> allFields) throws Exception {
        int lastDot = templatePath.lastIndexOf('.');
        String outputPath = lastDot > 0
                ? templatePath.substring(0, lastDot) + "_filled" + templatePath.substring(lastDot)
                : templatePath + "_filled";
        try (FileInputStream fis = new FileInputStream(templatePath);
             XWPFDocument doc = new XWPFDocument(fis)) {

            List<XWPFTable> tables = doc.getTables();

            // === 处理header_below槽位：将xlsx数据直接复制到Word表格中 ===
            List<TemplateSlotEntity> headerSlots = slots.stream()
                    .filter(s -> "header_below".equals(s.getSlotType()))
                    .collect(Collectors.toList());

            Set<Integer> filledTables = new HashSet<>();
            if (!headerSlots.isEmpty() && sourceExcelPaths != null && !sourceExcelPaths.isEmpty()) {
                // 按table分组header_below slots
                Map<Integer, List<TemplateSlotEntity>> slotsByTable = new LinkedHashMap<>();
                for (TemplateSlotEntity slot : headerSlots) {
                    JSONObject pos = JSON.parseObject(slot.getPosition());
                    int tableIdx = pos.getIntValue("table");
                    slotsByTable.computeIfAbsent(tableIdx, k -> new ArrayList<>()).add(slot);
                }

                // 提取每个表格上方段落 + 表格内已有数据 作为上下文，用于筛选和排序数据
                Map<Integer, String> tableContextText = new HashMap<>();
                List<org.apache.poi.xwpf.usermodel.IBodyElement> bodyElements = doc.getBodyElements();
                for (int i = 0; i < bodyElements.size(); i++) {
                    if (bodyElements.get(i) instanceof XWPFTable) {
                        int tableIdx = tables.indexOf(bodyElements.get(i));
                        if (tableIdx >= 0) {
                            // 收集表格前面的段落文本
                            StringBuilder ctx = new StringBuilder();
                            for (int j = i - 1; j >= Math.max(0, i - 5); j--) {
                                if (bodyElements.get(j) instanceof XWPFParagraph) {
                                    String pText = ((XWPFParagraph) bodyElements.get(j)).getText();
                                    if (pText != null && !pText.isBlank()) {
                                        ctx.insert(0, pText + "\n");
                                    }
                                } else {
                                    break; // 遇到非段落元素停止
                                }
                            }
                            // 收集表格内已有文本（表头+已填写的数据行）
                            XWPFTable tbl = tables.get(tableIdx);
                            for (int r = 0; r < Math.min(tbl.getRows().size(), 5); r++) {
                                XWPFTableRow row = tbl.getRows().get(r);
                                for (var cell : row.getTableCells()) {
                                    String cellText = cell.getText();
                                    if (cellText != null && !cellText.isBlank()) {
                                        ctx.append(cellText).append(" ");
                                    }
                                }
                                ctx.append("\n");
                            }
                            tableContextText.put(tableIdx, ctx.toString());
                        }
                    }
                }

                // === 通用多表筛选：从段落差异+源数据推断每个表的筛选条件 ===
                // 策略：找到一列，使得每个表的段落文本匹配到该列的不同唯一值
                Map<Integer, int[]> filterColMap = new HashMap<>();  // tableIdx -> [colIdx]
                Map<Integer, String> filterValueMap = new HashMap<>(); // tableIdx -> filterValue
                Map<Integer, List<String>> tableToSourceFiles = new HashMap<>(); // tableIdx -> 分配的源文件列表

                if (slotsByTable.size() > 1) {
                    // 从源xlsx中收集每列的唯一值
                    Map<Integer, Set<String>> colUniqueValues = new LinkedHashMap<>();
                    for (String srcPath2 : sourceExcelPaths) {
                        if (!new File(srcPath2).exists()) continue;
                        try (FileInputStream srcFis2 = new FileInputStream(srcPath2);
                             XSSFWorkbook srcWb2 = new XSSFWorkbook(srcFis2)) {
                            for (int si2 = 0; si2 < srcWb2.getNumberOfSheets(); si2++) {
                                XSSFSheet sh2 = srcWb2.getSheetAt(si2);
                                XSSFRow hdr2 = sh2.getRow(0);
                                if (hdr2 == null) continue;
                                int totalCols = hdr2.getLastCellNum();
                                for (int c2 = 0; c2 < totalCols; c2++) {
                                    colUniqueValues.computeIfAbsent(c2, k -> new LinkedHashSet<>());
                                }
                                for (int r2 = 1; r2 <= Math.min(sh2.getLastRowNum(), 500); r2++) {
                                    XSSFRow row2 = sh2.getRow(r2);
                                    if (row2 == null) continue;
                                    for (int c2 = 0; c2 < totalCols; c2++) {
                                        XSSFCell cl2 = row2.getCell(c2);
                                        if (cl2 != null) {
                                            String v = getCellStringValue(cl2).trim();
                                            if (!v.isEmpty() && v.length() <= 30) {
                                                colUniqueValues.get(c2).add(v);
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (Exception ignore) {}
                    }

                    // 获取每个表的段落上下文
                    Map<Integer, String> tableContexts = new LinkedHashMap<>();
                    for (int tableIdx : slotsByTable.keySet()) {
                        String ctx = tableContextText.getOrDefault(tableIdx, "");
                        if (userRequirement != null) ctx = ctx + "\n" + userRequirement;
                        tableContexts.put(tableIdx, ctx);
                    }
                    List<Integer> tableIdxList = new ArrayList<>(slotsByTable.keySet());

                    // 尝试每一列，看能否区分所有表
                    // 策略：对每一列，收集每个表段落中匹配的该列值，然后用贪心分配
                    int bestFilterCol = -1;
                    Map<Integer, String> bestAssignment = null;
                    int bestScore = 0;

                    for (Map.Entry<Integer, Set<String>> colEntry : colUniqueValues.entrySet()) {
                        int colIdx = colEntry.getKey();
                        Set<String> uniqueVals = colEntry.getValue();
                        if (uniqueVals.size() <= 1 || uniqueVals.size() > 100) continue;

                        // 收集每个表段落中匹配的该列值（长度>=3以避免子串误匹配）
                        Map<Integer, List<String>> tableCandidates = new LinkedHashMap<>();
                        for (int tableIdx : tableIdxList) {
                            String context = tableContexts.get(tableIdx);
                            List<String> candidates = new ArrayList<>();
                            for (String uv : uniqueVals) {
                                if (uv.length() < 3) continue;
                                if (context.contains(uv)) {
                                    candidates.add(uv);
                                }
                            }
                            // 按长度降序排列（更长的值更优先）
                            candidates.sort((a, b) -> b.length() - a.length());
                            tableCandidates.put(tableIdx, candidates);
                        }

                        // 贪心分配：先处理候选最少的表，确保每个表分配到不同的值
                        List<Integer> sortedByNumCandidates = new ArrayList<>(tableIdxList);
                        sortedByNumCandidates.sort((a, b) -> tableCandidates.get(a).size() - tableCandidates.get(b).size());

                        Map<Integer, String> assignment = new LinkedHashMap<>();
                        Set<String> usedValues = new HashSet<>();
                        boolean valid = true;
                        int minLen = Integer.MAX_VALUE;

                        for (int tableIdx : sortedByNumCandidates) {
                            List<String> candidates = tableCandidates.get(tableIdx);
                            String chosen = null;
                            for (String c : candidates) {
                                if (!usedValues.contains(c)) {
                                    chosen = c;
                                    break;
                                }
                            }
                            if (chosen == null) {
                                valid = false;
                                break;
                            }
                            assignment.put(tableIdx, chosen);
                            usedValues.add(chosen);
                            minLen = Math.min(minLen, chosen.length());
                        }

                        if (valid && assignment.size() == tableIdxList.size() && minLen > bestScore) {
                            bestFilterCol = colIdx;
                            bestAssignment = assignment;
                            bestScore = minLen;
                        }
                    }

                    if (bestAssignment != null) {
                        for (Map.Entry<Integer, String> entry : bestAssignment.entrySet()) {
                            filterColMap.put(entry.getKey(), new int[]{bestFilterCol});
                            filterValueMap.put(entry.getKey(), entry.getValue());
                            log.info("Word表格复制: table={}, 通用筛选: col={}, value={}", entry.getKey(), bestFilterCol, entry.getValue());
                        }
                    } else {
                        log.info("Word表格复制: 未找到可区分各表的筛选列，尝试按源文件名匹配表格");
                    }

                    // === 当列值筛选未成功且有多个源文件时，按文件名与表格上下文的相似度分配源文件到表格 ===
                    if (bestAssignment == null && sourceExcelPaths.size() >= slotsByTable.size()) {
                        // 对每个表的段落上下文与每个源文件名做相似匹配
                        Map<Integer, Map<String, Double>> tableFileScores = new LinkedHashMap<>();
                        for (int tableIdx2 : tableIdxList) {
                            String ctx = tableContexts.getOrDefault(tableIdx2, "").toLowerCase();
                            Map<String, Double> fileScores = new LinkedHashMap<>();
                            for (String srcPath : sourceExcelPaths) {
                                String fileName = new File(srcPath).getName().toLowerCase()
                                        .replaceAll("\\.(xlsx|xls|csv)$", "")
                                        .replaceAll("[_\\-\\s]+", "");
                                double score = 0.0;
                                // 文件名中的每个字符序列(>=2字)是否出现在上下文中
                                for (int len = Math.min(fileName.length(), 10); len >= 2; len--) {
                                    for (int start = 0; start <= fileName.length() - len; start++) {
                                        String sub = fileName.substring(start, start + len);
                                        if (ctx.contains(sub)) {
                                            score = Math.max(score, (double) len / fileName.length());
                                        }
                                    }
                                }
                                fileScores.put(srcPath, score);
                            }
                            tableFileScores.put(tableIdx2, fileScores);
                        }
                        // 贪心分配：候选最少的表优先选择得分最高的文件
                        Set<String> usedFiles = new HashSet<>();
                        List<Integer> sortedTables2 = new ArrayList<>(tableIdxList);
                        sortedTables2.sort((a, b) -> {
                            long countA = tableFileScores.get(a).values().stream().filter(s -> s > 0).count();
                            long countB = tableFileScores.get(b).values().stream().filter(s -> s > 0).count();
                            return Long.compare(countA, countB);
                        });
                        boolean allAssigned = true;
                        Map<Integer, String> fileAssignment = new LinkedHashMap<>();
                        for (int tableIdx2 : sortedTables2) {
                            Map<String, Double> scores = tableFileScores.get(tableIdx2);
                            String bestFile = null;
                            double bestScore2 = 0.0;
                            for (Map.Entry<String, Double> fs : scores.entrySet()) {
                                if (!usedFiles.contains(fs.getKey()) && fs.getValue() > bestScore2) {
                                    bestScore2 = fs.getValue();
                                    bestFile = fs.getKey();
                                }
                            }
                            if (bestFile != null && bestScore2 > 0.0) {
                                fileAssignment.put(tableIdx2, bestFile);
                                usedFiles.add(bestFile);
                            } else {
                                allAssigned = false;
                            }
                        }
                        if (!fileAssignment.isEmpty()) {
                            for (Map.Entry<Integer, String> fa : fileAssignment.entrySet()) {
                                tableToSourceFiles.computeIfAbsent(fa.getKey(), k -> new ArrayList<>()).add(fa.getValue());
                                log.info("Word表格复制: table={}, 按文件名匹配源文件: {}", fa.getKey(), fa.getValue());
                            }
                            // 未分配的源文件加入所有未分配表
                            List<String> unassignedFiles = sourceExcelPaths.stream()
                                    .filter(p -> !usedFiles.contains(p)).collect(Collectors.toList());
                            if (!unassignedFiles.isEmpty()) {
                                for (int tableIdx2 : tableIdxList) {
                                    if (!fileAssignment.containsKey(tableIdx2)) {
                                        tableToSourceFiles.computeIfAbsent(tableIdx2, k -> new ArrayList<>()).addAll(unassignedFiles);
                                    }
                                }
                            }
                        }
                    }
                }

                // 对每个table的header_below进行directTableCopy
                for (Map.Entry<Integer, List<TemplateSlotEntity>> entry : slotsByTable.entrySet()) {
                    int tableIdx = entry.getKey();
                    List<TemplateSlotEntity> tableHeaderSlots = entry.getValue();
                    if (tableIdx >= tables.size()) continue;

                    XWPFTable targetTable = tables.get(tableIdx);
                    int dataStartRow = 1; // 第0行是表头

                    // 构建label -> col映射
                    Map<String, Integer> labelToCol = new LinkedHashMap<>();
                    for (TemplateSlotEntity s : tableHeaderSlots) {
                        if (s.getPosition() == null || s.getLabel() == null) continue;
                        JSONObject pos = JSON.parseObject(s.getPosition());
                        labelToCol.put(s.getLabel().trim(), pos.getIntValue("col"));
                    }

                    // 获取此表的筛选条件（通用方式）
                    String filterValue = filterValueMap.get(tableIdx);
                    int filterColIdx = filterColMap.containsKey(tableIdx) ? filterColMap.get(tableIdx)[0] : -1;

                    int totalCopied = 0;
                    int maxRows = targetTable.getRows().size() - 1; // 排除表头行

                    // 确定此表应使用的源文件列表
                    List<String> tableSrcPaths = tableToSourceFiles.getOrDefault(tableIdx, sourceExcelPaths);

                    for (String srcPath : tableSrcPaths) {
                        if (!new File(srcPath).exists()) continue;

                        try (FileInputStream srcFis = new FileInputStream(srcPath);
                             XSSFWorkbook srcWorkbook = new XSSFWorkbook(srcFis)) {

                            for (int si = 0; si < srcWorkbook.getNumberOfSheets(); si++) {
                                XSSFSheet srcSheet = srcWorkbook.getSheetAt(si);
                                if (srcSheet.getLastRowNum() < 1) continue;

                                // 找表头行并匹配列
                                int srcHeaderRowIdx = -1;
                                Map<Integer, Integer> colMapping = new LinkedHashMap<>(); // destCol -> srcCol

                                for (int hr = 0; hr <= Math.min(5, srcSheet.getLastRowNum()); hr++) {
                                    XSSFRow candidateRow = srcSheet.getRow(hr);
                                    if (candidateRow == null) continue;
                                    Map<Integer, Integer> cm = matchColumns(candidateRow, labelToCol);
                                    if (cm.size() > colMapping.size()) {
                                        colMapping = cm;
                                        srcHeaderRowIdx = hr;
                                    }
                                }

                                if (colMapping.isEmpty()) continue;

                                log.info("Word表格复制: table={}, 源sheet={}, 匹配列={}/{}, 筛选列={}, 筛选值={}",
                                        tableIdx, srcSheet.getSheetName(), colMapping.size(), labelToCol.size(), filterColIdx, filterValue);

                                // 复制匹配行到Word表格
                                for (int r = srcHeaderRowIdx + 1; r <= srcSheet.getLastRowNum(); r++) {
                                    if (totalCopied >= maxRows) break;
                                    XSSFRow srcRow = srcSheet.getRow(r);
                                    if (srcRow == null) continue;

                                    // 通用筛选：根据指定列和值过滤行
                                    if (filterValue != null && filterColIdx >= 0) {
                                        XSSFCell filterCell = srcRow.getCell(filterColIdx);
                                        if (filterCell == null) continue;
                                        String cellVal = getCellStringValue(filterCell);
                                        if (!cellVal.contains(filterValue) && !filterValue.contains(cellVal)) continue;
                                    }

                                    // 检查是否有数据
                                    boolean hasData = false;
                                    Map<Integer, String> rowValues = new LinkedHashMap<>();
                                    for (Map.Entry<Integer, Integer> cm : colMapping.entrySet()) {
                                        XSSFCell srcCell = srcRow.getCell(cm.getValue());
                                        String val = srcCell != null ? getCellStringValue(srcCell) : "";
                                        rowValues.put(cm.getKey(), val);
                                        if (!val.isEmpty()) hasData = true;
                                    }
                                    if (!hasData) continue;

                                    // 写入Word表格行
                                    int destRowIdx = dataStartRow + totalCopied;
                                    List<XWPFTableRow> tableRows = targetTable.getRows();
                                    if (destRowIdx >= tableRows.size()) break;

                                    XWPFTableRow destRow = tableRows.get(destRowIdx);
                                    var destCells = destRow.getTableCells();

                                    for (Map.Entry<Integer, String> rv : rowValues.entrySet()) {
                                        String val = rv.getValue();
                                        if (val.isEmpty()) continue;
                                        int col = rv.getKey();
                                        if (col >= destCells.size()) continue;

                                        var destCell = destCells.get(col);
                                        if (!destCell.getParagraphs().isEmpty()) {
                                            XWPFParagraph para = destCell.getParagraphs().get(0);
                                            List<XWPFRun> runs = para.getRuns();
                                            if (runs.isEmpty()) {
                                                para.createRun().setText(val);
                                            } else {
                                                runs.get(0).setText(val, 0);
                                            }
                                        }
                                    }
                                    totalCopied++;
                                }
                            }
                        } catch (Exception e) {
                            log.warn("Word表格复制失败(table={}, 源={}): {}", tableIdx, srcPath, e.getMessage());
                        }
                    }

                    log.info("Word表格复制完成: table={}, 复制{}行", tableIdx, totalCopied);
                    if (totalCopied > 0) {
                        filledTables.add(tableIdx);
                        // 更新decisions
                        for (TemplateSlotEntity s : tableHeaderSlots) {
                            FillDecisionEntity d = decisionMap.get(s.getId().toString());
                            if (d != null) {
                                d.setFinalValue("已复制" + totalCopied + "行数据");
                                d.setFinalConfidence(new BigDecimal("0.95"));
                                d.setDecisionMode("direct_table_copy_word");
                                fillDecisionMapper.updateById(d);
                            }
                        }
                    }
                }

                // === 非Excel源文档数据填入Word表格：从docx/md/txt源文档的已提取字段或LLM提取结构化数据 ===
                if (allFields != null && !allFields.isEmpty()) {
                    // 找出非Excel源文档的字段
                    Set<Long> excelDocIds = new HashSet<>();
                    for (ExtractedFieldEntity f : allFields) {
                        SourceDocumentEntity srcDoc = sourceDocumentMapper.selectById(f.getDocId());
                        if (srcDoc != null && srcDoc.getStoragePath() != null) {
                            String sp = srcDoc.getStoragePath().toLowerCase();
                            if (sp.endsWith(".xlsx") || sp.endsWith(".xls") || sp.endsWith(".csv")) {
                                excelDocIds.add(f.getDocId());
                            }
                        }
                    }
                    List<ExtractedFieldEntity> nonExcelFields = allFields.stream()
                            .filter(f -> !excelDocIds.contains(f.getDocId()))
                            .collect(Collectors.toList());

                    if (!nonExcelFields.isEmpty()) {
                        log.info("Word表格: 发现{}个非Excel源文档字段，尝试LLM填充", nonExcelFields.size());
                        for (Map.Entry<Integer, List<TemplateSlotEntity>> entry2 : slotsByTable.entrySet()) {
                            int tableIdx2 = entry2.getKey();
                            List<TemplateSlotEntity> tableSlots2 = entry2.getValue();
                            if (tableIdx2 >= tables.size()) continue;
                            XWPFTable targetTable2 = tables.get(tableIdx2);
                            int maxDataRows = targetTable2.getRows().size() - 1;

                            // 检查此表是否还有空的数据行
                            int emptyStartRow = 1;
                            for (int r = 1; r <= maxDataRows; r++) {
                                XWPFTableRow row = targetTable2.getRows().get(r);
                                boolean hasData = false;
                                for (var cell : row.getTableCells()) {
                                    if (cell.getText() != null && !cell.getText().trim().isEmpty()) {
                                        hasData = true; break;
                                    }
                                }
                                if (hasData) emptyStartRow = r + 1;
                                else break;
                            }
                            if (emptyStartRow > maxDataRows) continue; // 表已满

                            // 收集列标签
                            List<String> colLabels = new ArrayList<>();
                            Map<String, Integer> colMap = new LinkedHashMap<>();
                            for (TemplateSlotEntity s : tableSlots2) {
                                if (s.getPosition() == null || s.getLabel() == null) continue;
                                JSONObject pos = JSON.parseObject(s.getPosition());
                                colMap.put(s.getLabel().trim(), pos.getIntValue("col"));
                                colLabels.add(s.getLabel().trim());
                            }

                            // 收集非Excel源文档的原始文本
                            StringBuilder srcText = new StringBuilder();
                            Set<Long> seenDocs = new HashSet<>();
                            for (ExtractedFieldEntity f : nonExcelFields) {
                                if (seenDocs.add(f.getDocId())) {
                                    SourceDocumentEntity srcDoc = sourceDocumentMapper.selectById(f.getDocId());
                                    if (srcDoc != null && srcDoc.getStoragePath() != null) {
                                        try {
                                            String text = readDocumentText(srcDoc.getStoragePath(), srcDoc.getFileType());
                                            if (text != null && !text.isBlank()) srcText.append(text).append("\n");
                                        } catch (Exception e) {
                                            log.warn("读取非Excel源文档失败: {}", e.getMessage());
                                        }
                                    }
                                }
                            }
                            if (srcText.length() == 0) continue;

                            String content = srcText.toString();
                            if (content.length() > 12000) content = content.substring(0, 12000);

                            String reqHint = "";
                            if (userRequirement != null && !userRequirement.isBlank()) {
                                reqHint = "\n用户筛选条件：" + userRequirement + "\n请只提取满足上述条件的数据行。";
                            }
                            // 获取此表上方段落文本+已有单元格数据作为上下文提示
                            String tableCtx = tableContextText.getOrDefault(tableIdx2, "");
                            String ctxHint = "";
                            if (!tableCtx.isBlank()) {
                                ctxHint = "\n此表格的上下文信息（包括标题和表格中已有数据）：\n" + tableCtx.trim() +
                                        "\n【重要】请仅提取与上述表格上下文相关的数据，不要提取属于其他表格的数据。";
                            }

                            String prompt = "你是专业的数据提取助手。请从以下文档中提取结构化表格数据。\n\n" +
                                    "文档内容：\n" + content + "\n\n" +
                                    reqHint + ctxHint +
                                    "\n请按以下表头列提取数据，每个主要实体独立一行：\n" +
                                    "表头列：" + String.join("、", colLabels) + "\n\n" +
                                    "【规则】\n" +
                                    "1. 返回JSON数组，每个元素的key必须与表头列名完全一致\n" +
                                    "2. 数值只填纯数字和单位，不附加解释文字\n" +
                                    "3. 每个实体的每一列都必须认真查找数据，不要遗漏\n" +
                                    "4. 如果上下文中提到了地名、类别等限定信息，请严格只提取该地区/类别的数据\n" +
                                    "5. 只返回JSON数组，不要其他文字\n" +
                                    "示例：[{\"列1\":\"值1\",\"列2\":\"值2\"}]";

                            String resp = safeLlmCall(prompt, 30_000);
                            if (resp != null && !resp.isBlank()) {
                                try {
                                    String jsonStr = extractJsonFromResponse(resp);
                                    com.alibaba.fastjson.JSONArray rows = JSON.parseArray(jsonStr);
                                    if (rows != null && !rows.isEmpty()) {
                                        int written = 0;
                                        for (int i = 0; i < rows.size() && (emptyStartRow + written) <= maxDataRows; i++) {
                                            JSONObject rowData = rows.getJSONObject(i);
                                            if (rowData == null) continue;

                                            int destRowIdx = emptyStartRow + written;
                                            XWPFTableRow destRow = targetTable2.getRows().get(destRowIdx);
                                            var destCells = destRow.getTableCells();
                                            boolean rowHasData = false;

                                            for (Map.Entry<String, Integer> cm : colMap.entrySet()) {
                                                String val = rowData.getString(cm.getKey());
                                                if (val == null || val.isBlank()) {
                                                    // 模糊匹配key
                                                    for (String key : rowData.keySet()) {
                                                        if (key.contains(cm.getKey()) || cm.getKey().contains(key)) {
                                                            val = rowData.getString(key);
                                                            break;
                                                        }
                                                    }
                                                }
                                                if (val == null || val.isBlank()) continue;
                                                int col = cm.getValue();
                                                if (col >= destCells.size()) continue;

                                                var destCell = destCells.get(col);
                                                if (!destCell.getParagraphs().isEmpty()) {
                                                    XWPFParagraph para = destCell.getParagraphs().get(0);
                                                    List<XWPFRun> runs = para.getRuns();
                                                    if (runs.isEmpty()) {
                                                        para.createRun().setText(val);
                                                    } else {
                                                        runs.get(0).setText(val, 0);
                                                    }
                                                    rowHasData = true;
                                                }
                                            }
                                            if (rowHasData) written++;
                                        }

                                        if (written > 0) {
                                            filledTables.add(tableIdx2);
                                            log.info("Word表格LLM填充: table={}, 从非Excel源文档写入{}行", tableIdx2, written);
                                            for (TemplateSlotEntity s : tableSlots2) {
                                                FillDecisionEntity d = decisionMap.get(s.getId().toString());
                                                if (d != null) {
                                                    String prev = d.getFinalValue();
                                                    String newVal = (prev != null && !prev.isEmpty() ? prev + " + " : "") + "LLM提取" + written + "行";
                                                    d.setFinalValue(newVal);
                                                    d.setFinalConfidence(new BigDecimal("0.85"));
                                                    d.setDecisionMode("llm_multi_row_word");
                                                    fillDecisionMapper.updateById(d);
                                                }
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    log.warn("Word表格LLM填充解析失败(table={}): {}", tableIdx2, e.getMessage());
                                }
                            }
                        }
                    }
                }

                // === 合并多表到第一张表（仅当没有按表分配源文件或筛选条件时） ===
                // 当每个表有独立的数据源（按文件名匹配或列值筛选），表格应保持独立不合并
                boolean hasPerTableAssignment = !filterValueMap.isEmpty() || !tableToSourceFiles.isEmpty();
                if (filledTables.size() > 1 && !hasPerTableAssignment) {
                    List<Integer> sortedTables = new ArrayList<>(filledTables);
                    Collections.sort(sortedTables);
                    int firstTableIdx = sortedTables.get(0);
                    XWPFTable firstTable = tables.get(firstTableIdx);

                    // 检查所有filled表头是否一致
                    boolean sameHeaders = true;
                    XWPFTableRow firstHeader = firstTable.getRows().get(0);
                    List<String> firstHeaderTexts = new ArrayList<>();
                    for (var cell : firstHeader.getTableCells()) firstHeaderTexts.add(cell.getText().trim());

                    for (int i = 1; i < sortedTables.size(); i++) {
                        XWPFTable otherTable = tables.get(sortedTables.get(i));
                        XWPFTableRow otherHeader = otherTable.getRows().get(0);
                        var otherCells = otherHeader.getTableCells();
                        if (otherCells.size() != firstHeaderTexts.size()) { sameHeaders = false; break; }
                        for (int c = 0; c < otherCells.size(); c++) {
                            if (!otherCells.get(c).getText().trim().equals(firstHeaderTexts.get(c))) {
                                sameHeaders = false; break;
                            }
                        }
                        if (!sameHeaders) break;
                    }

                    if (sameHeaders) {
                        log.info("合并{}个同结构表格到第一张表(table={})", sortedTables.size(), firstTableIdx);
                        int colCount = firstHeaderTexts.size();

                        // 将后续表的数据行追加到第一张表
                        for (int i = 1; i < sortedTables.size(); i++) {
                            XWPFTable otherTable = tables.get(sortedTables.get(i));
                            for (int r = 1; r < otherTable.getRows().size(); r++) {
                                XWPFTableRow srcRow = otherTable.getRows().get(r);
                                var srcCells = srcRow.getTableCells();
                                boolean hasData = false;
                                for (var sc : srcCells) {
                                    if (!sc.getText().trim().isEmpty()) { hasData = true; break; }
                                }
                                if (!hasData) continue;

                                // 在第一张表末尾创建新行
                                XWPFTableRow newRow = firstTable.createRow();
                                var newCells = newRow.getTableCells();
                                for (int c = 0; c < Math.min(colCount, srcCells.size()); c++) {
                                    String val = srcCells.get(c).getText().trim();
                                    if (val.isEmpty()) continue;
                                    if (c < newCells.size()) {
                                        var destCell = newCells.get(c);
                                        if (!destCell.getParagraphs().isEmpty()) {
                                            XWPFParagraph para = destCell.getParagraphs().get(0);
                                            List<XWPFRun> runs = para.getRuns();
                                            if (runs.isEmpty()) {
                                                para.createRun().setText(val);
                                            } else {
                                                runs.get(0).setText(val, 0);
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 删除后续表格及其上方的描述段落
                        // 从后向前删除以避免索引偏移问题
                        for (int i = sortedTables.size() - 1; i >= 1; i--) {
                            int tblIdx = sortedTables.get(i);
                            XWPFTable tblToRemove = tables.get(tblIdx);

                            // 找到此表格在body elements中的位置并删除上方段落
                            int bodyPos = -1;
                            for (int b = 0; b < bodyElements.size(); b++) {
                                if (bodyElements.get(b) == tblToRemove) { bodyPos = b; break; }
                            }
                            if (bodyPos >= 0) {
                                // 先删除上方段落（从表格位置向上找描述段落）
                                List<Integer> parasToRemove = new ArrayList<>();
                                for (int b = bodyPos - 1; b >= 0; b--) {
                                    if (bodyElements.get(b) instanceof XWPFParagraph) {
                                        parasToRemove.add(b);
                                    } else {
                                        break;
                                    }
                                }
                                // 从后向前删除：先删表格，再删段落
                                doc.removeBodyElement(bodyPos);
                                for (int pi = parasToRemove.size() - 1; pi >= 0; pi--) {
                                    doc.removeBodyElement(parasToRemove.get(pi));
                                }
                            } else {
                                // fallback: 只删除表格内容
                                int pos2 = doc.getPosOfTable(tblToRemove);
                                if (pos2 >= 0) doc.removeBodyElement(pos2);
                            }
                        }

                        int totalMerged = firstTable.getRows().size() - 1; // 减去表头
                        log.info("合并完成: 第一张表共{}行数据", totalMerged);
                    }
                }
            }

            // === 处理非header_below槽位的常规写值 ===
            for (TemplateSlotEntity slot : slots) {
                // header_below已处理，跳过
                if ("header_below".equals(slot.getSlotType())) continue;

                FillDecisionEntity decision = decisionMap.get(slot.getId().toString());
                if (decision == null || decision.getFinalValue() == null || decision.getFinalValue().isEmpty()) continue;

                JSONObject pos = JSON.parseObject(slot.getPosition());
                int tableIdx = pos.getIntValue("table");
                int rowIdx = pos.getIntValue("row");
                int colIdx = pos.getIntValue("col");

                if (tableIdx >= tables.size()) continue;

                XWPFTable table = tables.get(tableIdx);
                List<XWPFTableRow> rows = table.getRows();
                if (rowIdx >= rows.size()) continue;

                var cells = rows.get(rowIdx).getTableCells();
                if (colIdx >= cells.size()) continue;

                var targetCell = cells.get(colIdx);

                if ("inline".equals(slot.getSlotType())) {
                    String text = targetCell.getText();
                    String newText = text.replaceAll("([：:])\\s*[_\\s]*$", "$1" + decision.getFinalValue());
                    if (!targetCell.getParagraphs().isEmpty()) {
                        XWPFParagraph para = targetCell.getParagraphs().get(0);
                        List<XWPFRun> runs = para.getRuns();
                        if (!runs.isEmpty()) {
                            for (int i = runs.size() - 1; i >= 0; i--) {
                                para.removeRun(i);
                            }
                            XWPFRun newRun = para.createRun();
                            newRun.setText(newText);
                        }
                    }
                } else {
                    String val = decision.getFinalValue();
                    if (!targetCell.getParagraphs().isEmpty()) {
                        XWPFParagraph para = targetCell.getParagraphs().get(0);
                        List<XWPFRun> runs = para.getRuns();
                        if (runs.isEmpty()) {
                            para.createRun().setText(val);
                        } else {
                            runs.get(0).setText(val, 0);
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
        if (label.contains("人口") || label.contains("数量") || label.contains("总数") || label.contains("面积") || label.contains("GDP")) return "number";
        if (label.contains("单位") || label.contains("机构") || label.contains("学院") || label.contains("部门")) return "org";
        if (label.contains("负责人") || label.contains("主持人") || label.contains("姓名")) return "person";
        if (label.contains("国家") || label.contains("首都") || label.contains("省会") || label.contains("城市")) return "text";
        if (label.contains("地址") || label.contains("通讯")) return "text";
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
                    if (DateUtil.isCellDateFormatted(cell)) {
                        yield cell.getLocalDateTimeCellValue().toLocalDate().toString();
                    }
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
     * 表头引导提取：根据模板表头去源文档内容中查找上下文，使用LLM精准提取字段值。
     * 用于补充常规提取阶段可能遗漏的字段，特别是非结构化文档(md/txt/docx)。
     */
    private List<ExtractedFieldEntity> headerGuidedExtraction(
            List<TemplateSlotEntity> slots, List<Long> docIds, Long userId,
            List<ExtractedFieldEntity> existingFields,
            Map<String, String> slotLabelToStdKey, String userRequirement,
            long fillStartTime, long maxFillTimeMs) {

        List<ExtractedFieldEntity> supplementary = new ArrayList<>();

        // 收集需要补充提取的槽位（现有字段中未匹配到的）
        Set<String> existingKeys = new HashSet<>();
        for (ExtractedFieldEntity f : existingFields) {
            if (f.getFieldKey() != null && f.getFieldValue() != null && !f.getFieldValue().isBlank()) {
                existingKeys.add(f.getFieldKey());
            }
        }

        List<String> missingLabels = new ArrayList<>();
        Map<String, String> labelToStdKey = new HashMap<>();
        for (TemplateSlotEntity slot : slots) {
            String label = slot.getLabel().trim();
            String stdKey = slotLabelToStdKey.getOrDefault(label, label.toLowerCase());
            if (!existingKeys.contains(stdKey)) {
                missingLabels.add(label);
                labelToStdKey.put(label, stdKey);
            }
        }

        if (missingLabels.isEmpty()) {
            log.info("表头引导提取: 所有槽位已有对应字段，无需补充提取");
            return supplementary;
        }

        log.info("表头引导提取: 发现{}个未匹配槽位，将从源文档中搜索: {}", missingLabels.size(), missingLabels);

        // 确定目标源文档
        List<Long> targetDocIds;
        if (docIds != null && !docIds.isEmpty()) {
            targetDocIds = docIds;
        } else {
            targetDocIds = existingFields.stream()
                    .map(ExtractedFieldEntity::getDocId)
                    .distinct()
                    .collect(Collectors.toList());
        }

        // === 步骤1: 直接从源docx文件表格中提取key-value字段 ===
        Set<String> foundLabels = new HashSet<>();
        for (Long did : targetDocIds) {
            SourceDocumentEntity srcDoc = sourceDocumentMapper.selectById(did);
            if (srcDoc == null || srcDoc.getStoragePath() == null) continue;
            if ("docx".equals(srcDoc.getFileType())) {
                try {
                    List<ExtractedFieldEntity> tableFields = extractFieldsFromDocxTables(
                            did, srcDoc.getStoragePath(), missingLabels, labelToStdKey);
                    for (ExtractedFieldEntity f : tableFields) {
                        if (f.getFieldValue() != null && !f.getFieldValue().isBlank() && isMeaningfulValue(f.getFieldValue())) {
                            supplementary.add(f);
                            foundLabels.add(f.getFieldName());
                            log.info("表头引导(docx表格直接提取): {} = {}", f.getFieldName(), f.getFieldValue());
                        }
                    }
                } catch (Exception e) {
                    log.warn("表头引导: 直接读取docx表格失败 docId={}: {}", did, e.getMessage());
                }
            }
        }

        // 更新仍缺失的标签
        List<String> stillMissing = missingLabels.stream()
                .filter(label -> !foundLabels.contains(label))
                .collect(Collectors.toList());
        if (stillMissing.isEmpty()) {
            log.info("表头引导提取: 所有缺失字段已通过docx表格直接提取完成");
            return supplementary;
        }

        // === 步骤2: 读取所有源文档的原始文本内容 ===
        Map<Long, String> docTexts = new LinkedHashMap<>();
        for (Long did : targetDocIds) {
            SourceDocumentEntity srcDoc = sourceDocumentMapper.selectById(did);
            if (srcDoc == null || srcDoc.getStoragePath() == null) continue;
            try {
                String filePath = srcDoc.getStoragePath();
                String text = readDocumentText(filePath, srcDoc.getFileType());
                if (text != null && !text.isBlank()) {
                    docTexts.put(did, text);
                }
            } catch (Exception e) {
                log.warn("表头引导提取: 读取源文档失败 docId={}: {}", did, e.getMessage());
            }
        }

        if (docTexts.isEmpty()) {
            log.info("表头引导提取: 无可读取的源文档文本");
            return supplementary;
        }

        // === 步骤3: 直接正则提取（标签：值、标签: 值、标签 值等模式） ===
        for (String label : new ArrayList<>(stillMissing)) {
            String stdKey = labelToStdKey.getOrDefault(label, label.toLowerCase());
            // 构建搜索词：标签本身 + 别名 + 复合标签子项
            List<String> searchTerms = new ArrayList<>();
            searchTerms.add(label);
            // 复合标签子项扩展：如"国家/地区"→增加"国家"和"地区"
            if (label.contains("/") || label.contains("／")) {
                for (String part : label.split("[/／]")) {
                    part = part.trim();
                    if (!part.isEmpty() && !searchTerms.contains(part)) {
                        searchTerms.add(part);
                    }
                }
            }
            Map<String, List<String>> aliasMap = getFieldAliasMap();
            String normKey = normalizeLabel(label);
            if (!normKey.equals(label.toLowerCase())) {
                List<String> aliases = aliasMap.get(normKey);
                if (aliases != null) searchTerms.addAll(aliases);
            }
            // 对复合标签的每个子项也扩展别名
            if (label.contains("/") || label.contains("／")) {
                for (String part : label.split("[/／]")) {
                    part = part.trim();
                    if (part.isEmpty()) continue;
                    String partKey = normalizeSingleLabel(part);
                    if (!partKey.equals(part.toLowerCase())) {
                        List<String> partAliases = aliasMap.get(partKey);
                        if (partAliases != null) {
                            for (String a : partAliases) {
                                if (!searchTerms.contains(a)) searchTerms.add(a);
                            }
                        }
                    }
                }
            }

            boolean isComposite = label.contains("/") || label.contains("／");
            boolean extracted = false;
            for (Map.Entry<Long, String> docEntry : docTexts.entrySet()) {
                if (extracted && !isComposite) break;
                String docText = docEntry.getValue();
                for (String term : searchTerms) {
                    if (extracted && !isComposite) break;
                    // 匹配 "term：值" 或 "term: 值" 模式，值不含换行
                    java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                            java.util.regex.Pattern.quote(term) + "[：:：]\\s*([^\\n]{1,200})",
                            java.util.regex.Pattern.CASE_INSENSITIVE);
                    java.util.regex.Matcher m = p.matcher(docText);
                    while (m.find()) {
                        String val = m.group(1).trim();
                        // 清理末尾可能的标点或无关内容
                        val = val.replaceAll("[。；;]+$", "").trim();
                        if (!val.isEmpty() && isMeaningfulValue(val)) {
                            // 对复合标签的每个子项分别生成字段，使用子标签的标准化key
                            String fieldKey = stdKey;
                            String fieldName = label;
                            if (isComposite) {
                                String termNorm = normalizeSingleLabel(term);
                                if (!termNorm.equals(term.toLowerCase())) {
                                    fieldKey = termNorm;
                                }
                                fieldName = term; // 使用实际匹配的子项名作为字段名
                            }
                            ExtractedFieldEntity field = new ExtractedFieldEntity();
                            field.setDocId(docEntry.getKey());
                            field.setFieldKey(fieldKey);
                            field.setFieldName(fieldName);
                            field.setFieldValue(val);
                            field.setFieldType("text");
                            field.setSourceText("直接正则提取: " + term + "：" + val);
                            field.setSourceLocation("regex_match");
                            field.setConfidence(new BigDecimal("0.85"));
                            supplementary.add(field);
                            foundLabels.add(label);
                            extracted = true;
                            log.info("表头引导(正则直接提取): {} = {}", fieldName, val);
                        }
                        if (!isComposite) break; // 非复合标签只取第一个匹配
                    }
                }
            }
        }

        // 更新仍缺失的标签
        stillMissing = stillMissing.stream()
                .filter(label -> !foundLabels.contains(label))
                .collect(Collectors.toList());
        if (stillMissing.isEmpty()) {
            log.info("表头引导提取: 所有缺失字段已通过直接提取完成");
            return supplementary;
        }

        // === 步骤4: 使用LLM对剩余缺失字段进行精准上下文提取 ===
        // 时间预算检查：如果已用超过总预算的40%，跳过LLM提取避免超时
        long elapsedMs = System.currentTimeMillis() - fillStartTime;
        if (elapsedMs > maxFillTimeMs * 0.4) {
            log.info("表头引导提取: 已耗时{}ms，跳过LLM步骤以确保填表在时间预算内完成", elapsedMs);
            return supplementary;
        }

        StringBuilder contextBuilder = new StringBuilder();
        for (String label : stillMissing) {
            for (Map.Entry<Long, String> docEntry : docTexts.entrySet()) {
                String docText = docEntry.getValue();
                String context = extractContextForLabel(docText, label);
                if (context != null && !context.isBlank()) {
                    contextBuilder.append("[").append(label).append("] docId=").append(docEntry.getKey()).append(":\n");
                    contextBuilder.append(context).append("\n\n");
                }
            }
        }

        String allContext = contextBuilder.toString();
        if (allContext.isBlank()) {
            // 如果按标签搜索没有结果，提供全文概要
            for (Map.Entry<Long, String> docEntry : docTexts.entrySet()) {
                String text = docEntry.getValue();
                if (text.length() > 5000) text = text.substring(0, 5000);
                allContext += "文档(docId=" + docEntry.getKey() + "):\n" + text + "\n\n";
            }
        }
        if (allContext.length() > 15000) {
            allContext = allContext.substring(0, 15000);
        }

        // 使用LLM对缺失字段进行精准提取
        String requirementHint = "";
        if (userRequirement != null && !userRequirement.isBlank()) {
            requirementHint = "\n用户需求条件：" + userRequirement + "\n请只提取满足上述条件的数据。";
        }

        StringBuilder labelsStr = new StringBuilder();
        for (String label : stillMissing) {
            labelsStr.append("- ").append(label).append("\n");
        }

        String prompt = "你是一个专业的文档信息提取助手。请从以下文档内容中精准提取指定字段的值。\n\n" +
                "【重要提示】\n" +
                "1. 仔细搜索文档中每个字段对应的上下文，字段名可能以不同形式出现（如\"项目名称\"可能写作\"课题名称\"）\n" +
                "2. 对于表格中的数据，注意表头与数据行的对应关系\n" +
                "3. 对于叙述性文本，提取段落中包含的实体信息\n" +
                "4. 对于包含'/'的复合字段（如\"国家/省份\"），请将'/'两侧作为不同类别分别提取所有值，用换行符分隔。例如\"国家/省份\"应返回所有国家和所有省份，如\"美国\\n中国\\n加利福尼亚\\n广东省\"\n" +
                "5. 如果同一字段在文档中出现多次且值不同，全部列出，用换行符分隔\n\n" +
                "文档内容片段：\n" + allContext +
                requirementHint +
                "\n需要提取的字段：\n" + labelsStr +
                "\n请严格按JSON格式返回，key为字段名（必须与上面列出的字段名完全一致），value为提取到的值。" +
                "如果在文档中确实找不到某字段的值，该字段value设为空字符串。" +
                "\n只返回JSON，不要任何其他文字。";

        try {
            String llmResp = safeLlmCall(prompt, 12_000);
            if (llmResp != null && !llmResp.isBlank()) {
                String jsonStr = extractJsonFromResponse(llmResp);
                JSONObject result = JSON.parseObject(jsonStr);
                if (result != null) {
                    for (String label : stillMissing) {
                        String val = result.getString(label);
                        if (val == null || val.isBlank()) {
                            // 模糊匹配
                            for (String key : result.keySet()) {
                                if (key.contains(label) || label.contains(key)) {
                                    val = result.getString(key);
                                    break;
                                }
                            }
                        }
                        if (val != null && !val.isBlank() && isMeaningfulValue(val)) {
                            String stdKey = labelToStdKey.getOrDefault(label, label.toLowerCase());
                            boolean isCompositeLabel = label.contains("/") || label.contains("／");
                            // 对复合标签的多行值，拆分为独立字段便于后续resolveCompositeLabel匹配
                            if (isCompositeLabel && (val.contains("\n") || val.contains("\\n"))) {
                                String[] subVals = val.replace("\\n", "\n").split("[\\n]+");
                                for (String sv : subVals) {
                                    sv = sv.trim();
                                    if (sv.isEmpty() || !isMeaningfulValue(sv)) continue;
                                    ExtractedFieldEntity field = new ExtractedFieldEntity();
                                    field.setDocId(targetDocIds.get(0));
                                    field.setFieldKey(stdKey);
                                    field.setFieldName(label);
                                    field.setFieldValue(sv);
                                    field.setFieldType("text");
                                    field.setSourceText("表头引导LLM提取: " + label + " → " + sv);
                                    field.setSourceLocation("header_guided_llm");
                                    field.setConfidence(new BigDecimal("0.80"));
                                    supplementary.add(field);
                                }
                                log.info("表头引导LLM提取(复合拆分): {} = {}", label, val);
                            } else {
                                ExtractedFieldEntity field = new ExtractedFieldEntity();
                                field.setDocId(targetDocIds.get(0));
                                field.setFieldKey(stdKey);
                                field.setFieldName(label);
                                field.setFieldValue(val.trim());
                                field.setFieldType("text");
                                field.setSourceText("表头引导LLM提取: " + label);
                                field.setSourceLocation("header_guided_llm");
                                field.setConfidence(new BigDecimal("0.80"));
                                supplementary.add(field);
                                log.info("表头引导LLM提取成功: {} = {}", label, val);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("表头引导LLM提取失败: {}", e.getMessage());
        }

        return supplementary;
    }

    /**
     * 直接从源docx文件的表格中提取字段值。
     * 逐行扫描docx表格，对于key-value结构的行（标签+值），检查标签是否匹配模板槽位。
     * 这比文本搜索更精准，因为直接利用了表格的结构化信息。
     */
    private List<ExtractedFieldEntity> extractFieldsFromDocxTables(
            Long docId, String filePath, List<String> targetLabels, Map<String, String> labelToStdKey) throws Exception {
        List<ExtractedFieldEntity> fields = new ArrayList<>();
        Map<String, List<String>> aliasMap = getFieldAliasMap();

        // 判断是否有复合标签
        Set<String> compositeLabels = new HashSet<>();
        for (String label : targetLabels) {
            if (label.contains("/") || label.contains("／")) compositeLabels.add(label);
        }

        // 构建标签匹配索引：searchTerm → targetLabel, 以及其所有别名
        Map<String, String> labelMatchMap = new LinkedHashMap<>(); // searchTerm → targetLabel
        // 另外记录 searchTerm → 对应的标准化子key（用于复合标签的子项区分）
        Map<String, String> termToSubKey = new LinkedHashMap<>();
        for (String label : targetLabels) {
            labelMatchMap.put(label, label);
            // 复合标签子项扩展：如"国家/地区"→增加"国家"和"地区"
            if (label.contains("/") || label.contains("／")) {
                for (String part : label.split("[/／]")) {
                    part = part.trim();
                    if (!part.isEmpty()) {
                        labelMatchMap.putIfAbsent(part, label);
                        termToSubKey.put(part, normalizeSingleLabel(part));
                    }
                }
            }
            String normKey = normalizeLabel(label);
            if (!normKey.equals(label.toLowerCase())) {
                List<String> aliases = aliasMap.get(normKey);
                if (aliases != null) {
                    for (String alias : aliases) {
                        labelMatchMap.putIfAbsent(alias, label);
                    }
                }
            }
            // 对复合子项也扩展别名
            if (label.contains("/") || label.contains("／")) {
                for (String part : label.split("[/／]")) {
                    part = part.trim();
                    if (part.isEmpty()) continue;
                    String partKey = normalizeSingleLabel(part);
                    if (!partKey.equals(part.toLowerCase())) {
                        List<String> partAliases = aliasMap.get(partKey);
                        if (partAliases != null) {
                            for (String a : partAliases) {
                                labelMatchMap.putIfAbsent(a, label);
                                termToSubKey.putIfAbsent(a, partKey);
                            }
                        }
                    }
                }
            }
        }

        Set<String> foundLabels = new HashSet<>();
        Set<String> foundValues = new HashSet<>(); // 跟踪复合标签已提取的值避免重复

        try (FileInputStream fis = new FileInputStream(filePath);
             XWPFDocument doc = new XWPFDocument(fis)) {

            for (XWPFTable table : doc.getTables()) {
                // === 先检测是否为多行数据表格（有表头行+数据行） ===
                List<XWPFTableRow> rows = table.getRows();
                if (rows.size() >= 2) {
                    // 检查第一行是否是表头行
                    XWPFTableRow headerRow = rows.get(0);
                    var headerCells = headerRow.getTableCells();
                    // 构建 列号 → matchedLabel 的映射
                    Map<Integer, String> colToLabel = new LinkedHashMap<>();
                    Map<Integer, String> colToSubKey = new LinkedHashMap<>();
                    for (int ci = 0; ci < headerCells.size(); ci++) {
                        String headerText = headerCells.get(ci).getText().trim().replaceAll("[：:（()）\\s]+$", "").trim();
                        if (headerText.isEmpty()) continue;
                        for (Map.Entry<String, String> entry : labelMatchMap.entrySet()) {
                            String searchTerm = entry.getKey();
                            if (headerText.equals(searchTerm) || headerText.equalsIgnoreCase(searchTerm)
                                    || headerText.contains(searchTerm) || searchTerm.contains(headerText)) {
                                colToLabel.put(ci, entry.getValue());
                                // 记录子项的标准化key
                                String subKey = termToSubKey.getOrDefault(searchTerm, 
                                        labelToStdKey.getOrDefault(entry.getValue(), entry.getValue().toLowerCase()));
                                colToSubKey.put(ci, subKey);
                                break;
                            }
                        }
                    }
                    // 如果表头匹配到了目标标签，从数据行中逐行提取
                    if (!colToLabel.isEmpty()) {
                        for (int ri = 1; ri < rows.size(); ri++) {
                            var dataCells = rows.get(ri).getTableCells();
                            for (Map.Entry<Integer, String> ce : colToLabel.entrySet()) {
                                int ci = ce.getKey();
                                String matchedLabel = ce.getValue();
                                if (ci >= dataCells.size()) continue;
                                String cellVal = dataCells.get(ci).getText().trim();
                                if (cellVal.isEmpty()) continue;
                                String valSig = matchedLabel + ":" + cellVal;
                                if (foundValues.contains(valSig)) continue;
                                
                                String subKey = colToSubKey.getOrDefault(ci, 
                                        labelToStdKey.getOrDefault(matchedLabel, matchedLabel.toLowerCase()));
                                ExtractedFieldEntity field = new ExtractedFieldEntity();
                                field.setDocId(docId);
                                field.setFieldKey(subKey);
                                field.setFieldName(matchedLabel);
                                field.setFieldValue(cellVal);
                                field.setFieldType("text");
                                field.setSourceText("docx表格数据行: 第" + (ri+1) + "行第" + (ci+1) + "列 → " + cellVal);
                                field.setSourceLocation("docx_table_data_row");
                                field.setConfidence(new BigDecimal("0.88"));
                                fields.add(field);
                                foundValues.add(valSig);
                                foundLabels.add(matchedLabel);
                            }
                        }
                        continue; // 已作为多行数据表格处理，跳过KV扫描
                    }
                }

                // === KV对扫描：逐行扫描标签-值对 ===
                for (XWPFTableRow row : rows) {
                    var cells = row.getTableCells();
                    if (cells.size() < 2) continue;

                    // 按kv对扫描（每2个单元格一对）
                    for (int c = 0; c < cells.size() - 1; c += 2) {
                        String cellKey = cells.get(c).getText().trim();
                        String cellVal = cells.get(c + 1).getText().trim();
                        if (cellKey.isEmpty() || cellVal.isEmpty()) continue;
                        if (cellKey.length() > 40) continue; // 太长不像标签

                        // 清理标签中的冒号等符号
                        String cleanKey = cellKey.replaceAll("[：:（()）\\s]+$", "").trim();

                        // 检查是否匹配某个目标标签
                        String matchedLabel = null;
                        String matchedTerm = null;
                        for (Map.Entry<String, String> entry : labelMatchMap.entrySet()) {
                            String searchTerm = entry.getKey();
                            if (cleanKey.equals(searchTerm)
                                    || cleanKey.equalsIgnoreCase(searchTerm)
                                    || cleanKey.contains(searchTerm)
                                    || searchTerm.contains(cleanKey)) {
                                matchedLabel = entry.getValue();
                                matchedTerm = searchTerm;
                                break;
                            }
                        }

                        if (matchedLabel != null) {
                            boolean isComposite = compositeLabels.contains(matchedLabel);
                            String valSig = matchedLabel + ":" + cellVal;
                            // 非复合标签只取第一个，复合标签允许多个不同值
                            if (!isComposite && foundLabels.contains(matchedLabel)) continue;
                            if (isComposite && foundValues.contains(valSig)) continue;
                            
                            String subKey = matchedTerm != null ? termToSubKey.getOrDefault(matchedTerm, 
                                    labelToStdKey.getOrDefault(matchedLabel, matchedLabel.toLowerCase()))
                                    : labelToStdKey.getOrDefault(matchedLabel, matchedLabel.toLowerCase());
                            ExtractedFieldEntity field = new ExtractedFieldEntity();
                            field.setDocId(docId);
                            field.setFieldKey(subKey);
                            field.setFieldName(matchedLabel);
                            field.setFieldValue(cellVal);
                            field.setFieldType("text");
                            field.setSourceText("docx表格: " + cellKey + " → " + cellVal);
                            field.setSourceLocation("docx_table_direct");
                            field.setConfidence(new BigDecimal("0.90"));
                            fields.add(field);
                            foundLabels.add(matchedLabel);
                            foundValues.add(valSig);
                        }
                    }

                    // 也检查单独第一列是标签、第二列是值的情况（非严格kv对）
                    if (cells.size() >= 2 && cells.size() % 2 != 0) {
                        // 对于奇数列数的行，第一列可能是标签
                        String firstCell = cells.get(0).getText().trim().replaceAll("[：:]+$", "").trim();
                        String secondCell = cells.get(1).getText().trim();
                        if (!firstCell.isEmpty() && !secondCell.isEmpty() && firstCell.length() <= 40) {
                            String matchedLabel = null;
                            String matchedTerm = null;
                            for (Map.Entry<String, String> entry : labelMatchMap.entrySet()) {
                                String searchTerm = entry.getKey();
                                if (firstCell.equals(searchTerm)
                                        || firstCell.equalsIgnoreCase(searchTerm)
                                        || firstCell.contains(searchTerm)
                                        || searchTerm.contains(firstCell)) {
                                    matchedLabel = entry.getValue();
                                    matchedTerm = searchTerm;
                                    break;
                                }
                            }
                            if (matchedLabel != null) {
                                boolean isComposite = compositeLabels.contains(matchedLabel);
                                String valSig = matchedLabel + ":" + secondCell;
                                if (!isComposite && foundLabels.contains(matchedLabel)) continue;
                                if (isComposite && foundValues.contains(valSig)) continue;
                                
                                String subKey = matchedTerm != null ? termToSubKey.getOrDefault(matchedTerm,
                                        labelToStdKey.getOrDefault(matchedLabel, matchedLabel.toLowerCase()))
                                        : labelToStdKey.getOrDefault(matchedLabel, matchedLabel.toLowerCase());
                                ExtractedFieldEntity field = new ExtractedFieldEntity();
                                field.setDocId(docId);
                                field.setFieldKey(subKey);
                                field.setFieldName(matchedLabel);
                                field.setFieldValue(secondCell);
                                field.setFieldType("text");
                                field.setSourceText("docx表格: " + firstCell + " → " + secondCell);
                                field.setSourceLocation("docx_table_direct");
                                field.setConfidence(new BigDecimal("0.88"));
                                fields.add(field);
                                foundLabels.add(matchedLabel);
                                foundValues.add(valSig);
                            }
                        }
                    }
                }
            }
        }

        return fields;
    }

    /**
     * 在文档文本中搜索标签关键词，提取其上下文。
     * 使用别名扩展搜索词，扩大上下文窗口（前后各500字符），提升非结构化文档的提取命中率。
     */
    private String extractContextForLabel(String docText, String label) {
        if (docText == null || label == null) return null;

        // 搜索策略: 完整标签 → 复合标签子项 → 别名词汇 → 标签核心词
        List<String> searchTerms = new ArrayList<>();
        searchTerms.add(label);

        // 复合标签子项扩展：如"国家/地区"→增加"国家"和"地区"
        if (label.contains("/") || label.contains("／")) {
            for (String part : label.split("[/／]")) {
                part = part.trim();
                if (!part.isEmpty() && !searchTerms.contains(part)) {
                    searchTerms.add(part);
                }
            }
        }

        // 通过normalizeLabel查找该标签对应的标准key，再获取所有同组别名
        String stdKey = normalizeLabel(label);
        if (!stdKey.equals(label.toLowerCase())) {
            Map<String, List<String>> aliasMap = getFieldAliasMap();
            List<String> aliases = aliasMap.get(stdKey);
            if (aliases != null) {
                for (String alias : aliases) {
                    if (!searchTerms.contains(alias)) {
                        searchTerms.add(alias);
                    }
                }
            }
        }

        // 去掉常见后缀如"名称"、"编号"等，提取核心词
        String core = label.replaceAll("(名称|编号|日期|时间|号码|号|金额|地址|方向|领域|类型|级别)$", "");
        if (!core.equals(label) && core.length() >= 2 && !searchTerms.contains(core)) {
            searchTerms.add(core);
        }

        StringBuilder result = new StringBuilder();
        for (String term : searchTerms) {
            int searchFrom = 0;
            int found = 0;
            while (found < 5) { // 最多提取5个匹配的上下文
                int idx = docText.indexOf(term, searchFrom);
                if (idx < 0) break;

                int start = Math.max(0, idx - 500);
                int end = Math.min(docText.length(), idx + term.length() + 500);
                String ctx = docText.substring(start, end).trim();
                if (!ctx.isBlank()) {
                    result.append("...").append(ctx).append("...\n");
                }
                searchFrom = idx + term.length();
                found++;
            }
            if (found > 0) break; // 第一个搜索词有结果则不用后续搜索
        }

        return result.length() > 0 ? result.toString() : null;
    }

    /**
     * 获取字段别名映射表，用于扩展搜索词汇。
     */
    private static Map<String, List<String>> getFieldAliasMap() {
        return Map.ofEntries(
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
                Map.entry("project_type", List.of("项目类型", "类型", "项目类别")),
                Map.entry("project_level", List.of("项目级别", "级别")),
                Map.entry("keywords", List.of("关键词", "关键字")),
                Map.entry("abstract_text", List.of("摘要", "项目摘要", "内容摘要")),
                Map.entry("project_number", List.of("项目编号", "课题编号", "合同编号", "编号")),
                Map.entry("discipline", List.of("学科分类", "学科方向", "所属学科")),
                Map.entry("funding_amount", List.of("资助金额", "拨款金额", "合同金额")),
                Map.entry("apply_date", List.of("申报日期", "申请日期", "填报日期")),
                // 通用地理/数量类字段
                Map.entry("population", List.of("人口", "总人口", "人口数", "人口数量", "常住人口")),
                Map.entry("country", List.of("国家", "国", "国别", "国家名称")),
                Map.entry("state_province", List.of("州", "省", "省份", "自治区", "自治州", "行政区")),
                Map.entry("area_size", List.of("面积", "占地面积", "国土面积", "总面积")),
                Map.entry("capital", List.of("首都", "省会", "首府", "行政中心")),
                Map.entry("gdp", List.of("GDP", "国内生产总值", "生产总值", "经济总量")),
                Map.entry("language", List.of("语言", "官方语言", "通用语言", "主要语言")),
                Map.entry("currency", List.of("货币", "货币单位", "流通货币")),
                Map.entry("region", List.of("地区", "区域", "所在地区", "行政区划")),
                Map.entry("city", List.of("城市", "市", "所在城市")),
                Map.entry("description", List.of("描述", "简介", "概况", "说明", "介绍", "备注")),
                Map.entry("name", List.of("名称", "姓名", "名字")),
                Map.entry("quantity", List.of("数量", "个数", "总数", "数目"))
        );
    }

    /**
     * 读取源文档的文本内容（支持多种格式）
     */
    private String readDocumentText(String filePath, String fileType) {
        try {
            if ("xlsx".equals(fileType) || "xls".equals(fileType)) {
                return readExcelText(filePath);
            } else if ("docx".equals(fileType)) {
                return readDocxText(filePath);
            } else {
                // txt, md等纯文本格式
                return java.nio.file.Files.readString(Path.of(filePath), java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.warn("读取文档文本失败: {}", e.getMessage());
            return null;
        }
    }

    private String readExcelText(String filePath) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (FileInputStream fis = new FileInputStream(filePath);
             XSSFWorkbook workbook = new XSSFWorkbook(fis)) {
            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                XSSFSheet sheet = workbook.getSheetAt(s);
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
                    if (!rowText.isEmpty()) sb.append(rowText).append("\n");
                }
            }
        }
        return sb.toString();
    }

    private String readDocxText(String filePath) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (FileInputStream fis = new FileInputStream(filePath);
             XWPFDocument doc = new XWPFDocument(fis)) {
            for (XWPFParagraph para : doc.getParagraphs()) {
                String text = para.getText();
                if (text != null && !text.trim().isEmpty()) {
                    sb.append(text.trim()).append("\n");
                }
            }
            // 表格: 智能检测key-value行，输出为"键：值"格式
            for (XWPFTable table : doc.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    var cells = row.getTableCells();
                    if (cells.isEmpty()) continue;

                    // 检测当前行是否为key-value结构
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
                        StringBuilder rowText = new StringBuilder();
                        cells.forEach(cell -> {
                            String cellText = cell.getText();
                            if (cellText != null && !cellText.trim().isEmpty()) {
                                if (!rowText.isEmpty()) rowText.append(" | ");
                                rowText.append(cellText.trim());
                            }
                        });
                        if (!rowText.isEmpty()) sb.append(rowText).append("\n");
                    }
                }
            }
        }
        return sb.toString();
    }

    /**
     * 处理复合标签（如"国家/地区"），将/两侧的属性分别匹配，收集所有值分行填入。
     * 先收集第一属性（如国家）的所有值，再收集第二属性（如地区）的所有值。
     */
    private String resolveCompositeLabel(TemplateSlotEntity originalSlot,
                                          List<ExtractedFieldEntity> allFields,
                                          List<ExtractedFieldEntity> allFieldsRaw,
                                          Map<String, List<String>> aliasDict,
                                          String userRequirement,
                                          Map<Long, Double> requirementDocScores) {
        String label = originalSlot.getLabel().trim();
        if (!label.contains("/") && !label.contains("／")) return null;

        String[] parts = label.split("[/／]");
        if (parts.length < 2) return null;

        List<String> allValues = new ArrayList<>();

        // 合并两个字段列表去重搜索
        List<ExtractedFieldEntity> combinedFields = new ArrayList<>(allFields);
        if (allFieldsRaw != null) {
            Set<String> existingVals = new HashSet<>();
            for (ExtractedFieldEntity f : allFields) {
                if (f.getFieldValue() != null) existingVals.add(f.getFieldKey() + ":" + f.getFieldValue().trim());
            }
            for (ExtractedFieldEntity f : allFieldsRaw) {
                String sig = f.getFieldKey() + ":" + (f.getFieldValue() != null ? f.getFieldValue().trim() : "");
                if (!existingVals.contains(sig)) {
                    combinedFields.add(f);
                }
            }
        }

        // 按子标签顺序收集：先收集所有"国家"值，再收集所有"地区/省份"值
        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty()) continue;

            String partStd = normalizeSingleLabel(part);

            // 收集该子标签在别名词典中的所有别名词
            Set<String> partAliases = new HashSet<>();
            partAliases.add(part);
            partAliases.add(partStd);
            List<String> dictAliases = aliasDict.get(partStd);
            if (dictAliases != null) partAliases.addAll(dictAliases);

            // 遍历所有字段，收集匹配该子标签的所有值
            for (ExtractedFieldEntity f : combinedFields) {
                if (f.getFieldValue() == null || f.getFieldValue().isBlank()) continue;
                String val = f.getFieldValue().trim();
                if (val.isEmpty() || "0".equals(val)) continue;
                if (allValues.contains(val)) continue; // 避免重复值

                boolean matches = false;
                String fKey = f.getFieldKey() != null ? f.getFieldKey() : "";
                String fName = f.getFieldName() != null ? f.getFieldName().trim() : "";
                String fNameStd = !fName.isEmpty() ? normalizeSingleLabel(fName) : "";

                // 1. 精确匹配 fieldKey 或 normalizedName
                if (partStd.equals(fKey) || partStd.equals(fNameStd)) {
                    matches = true;
                }
                // 2. 严格名称匹配
                if (!matches && !fName.isEmpty() && (part.equals(fName) || fName.equals(part))) {
                    matches = true;
                }
                // 3. 字段key或name匹配别名集合
                if (!matches) {
                    for (String alias : partAliases) {
                        if (alias.equals(fKey) || alias.equalsIgnoreCase(fName) || alias.equals(fNameStd)) {
                            matches = true;
                            break;
                        }
                    }
                }
                // 4. 包含匹配（字段名包含子标签或子标签包含字段名，但字段名至少2字符）
                if (!matches && fName.length() >= 2 && (fName.contains(part) || part.contains(fName))) {
                    matches = true;
                }
                // 5. 别名词典双向匹配：字段key匹配子标签别名
                if (!matches && !fKey.isEmpty()) {
                    List<String> fKeyAliases = aliasDict.get(fKey);
                    if (fKeyAliases != null) {
                        for (String fAlias : fKeyAliases) {
                            if (partAliases.contains(fAlias) || part.equals(fAlias)) {
                                matches = true;
                                break;
                            }
                        }
                    }
                }
                // 6. 字段自带aliases匹配
                if (!matches && f.getAliases() != null && !f.getAliases().isBlank()) {
                    try {
                        String aliasesStr = f.getAliases();
                        if (aliasesStr.startsWith("[")) {
                            com.alibaba.fastjson2.JSONArray arr = com.alibaba.fastjson2.JSONArray.parseArray(aliasesStr);
                            for (int i = 0; i < arr.size(); i++) {
                                String a = arr.getString(i);
                                if (a != null && (partAliases.contains(a) || part.equals(a) || partStd.equals(normalizeSingleLabel(a)))) {
                                    matches = true;
                                    break;
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }

                if (matches) {
                    // 如果值包含多行或逗号分隔的多个值，拆分后分别添加
                    if (val.contains("\n") || val.contains("、") || val.contains("，")) {
                        String[] subVals = val.split("[\\n、，]+");
                        for (String sv : subVals) {
                            sv = sv.trim();
                            if (!sv.isEmpty() && !"0".equals(sv) && !allValues.contains(sv)) {
                                allValues.add(sv);
                            }
                        }
                    } else {
                        allValues.add(val);
                    }
                }
            }
        }

        if (!allValues.isEmpty()) {
            return String.join("\n", allValues);
        }
        return null;
    }

    private String normalizeLabel(String label) {
        // 与ExtractionServiceImpl中的normalizeFieldKey逻辑一致
        if (label == null) return "";
        // 清理标签文字
        label = label.trim().replaceAll("[：:（()）\\s]+$", "").trim();
        // 复合标签如"国家/地区"：尝试用第一个子项进行标准化
        if (label.contains("/") || label.contains("／")) {
            String[] parts = label.split("[/／]");
            if (parts.length >= 2 && parts[0].trim().length() >= 1) {
                String firstPart = parts[0].trim();
                String result = normalizeSingleLabel(firstPart);
                if (!result.equals(firstPart.toLowerCase())) {
                    return result; // 第一个子项命中别名，使用它
                }
                // 尝试第二个子项
                String secondPart = parts[1].trim();
                result = normalizeSingleLabel(secondPart);
                if (!result.equals(secondPart.toLowerCase())) {
                    return result;
                }
            }
        }
        return normalizeSingleLabel(label);
    }

    private String normalizeSingleLabel(String label) {
        if (label == null || label.isEmpty()) return "";
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
                Map.entry("duration", List.of("执行期限", "研究期限", "项目期限", "实施周期")),
                Map.entry("project_number", List.of("项目编号", "课题编号", "合同编号", "编号")),
                Map.entry("discipline", List.of("学科分类", "学科方向", "所属学科")),
                Map.entry("funding_amount", List.of("资助金额", "拨款金额", "合同金额")),
                Map.entry("contact_person", List.of("联系人", "联络人")),
                Map.entry("apply_date", List.of("申报日期", "申请日期", "填报日期")),
                Map.entry("id_card", List.of("身份证", "身份证号码", "证件号")),
                // 通用地理/数量类字段
                Map.entry("population", List.of("人口", "总人口", "人口数", "人口数量", "常住人口")),
                Map.entry("country", List.of("国家", "国", "国别", "国家名称")),
                Map.entry("state_province", List.of("州", "省", "省份", "自治区", "自治州", "行政区")),
                Map.entry("area_size", List.of("面积", "占地面积", "国土面积", "总面积")),
                Map.entry("capital", List.of("首都", "省会", "首府", "行政中心")),
                Map.entry("gdp", List.of("GDP", "国内生产总值", "生产总值", "经济总量")),
                Map.entry("language_field", List.of("语言", "官方语言", "通用语言", "主要语言")),
                Map.entry("currency", List.of("货币", "货币单位", "流通货币")),
                Map.entry("region", List.of("地区", "区域", "所在地区", "行政区划")),
                Map.entry("city", List.of("城市", "市", "所在城市")),
                Map.entry("description", List.of("描述", "简介", "概况", "说明", "介绍", "备注")),
                Map.entry("quantity", List.of("数量", "个数", "总数", "数目"))
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

    private List<String> extractRequirementTokens(String requirement) {
        if (requirement == null || requirement.isBlank()) {
            return Collections.emptyList();
        }
        String normalized = requirement
                .replace('（', '(')
                .replace('）', ')')
                .replace('：', ':')
                .replace('，', ',')
                .replace('。', '.')
                .trim()
                .toLowerCase();

        String[] raw = normalized.split("[\\s,.;:、/|]+");
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        for (String t : raw) {
            if (t == null) continue;
            String token = t.trim();
            if (token.length() < 2) continue;
            if (token.matches("^[0-9]+$")) continue;
            if (token.matches("^(只|仅|需要|必须|按照|根据|以及|还有|并且|和|与|的|在)$")) continue;
            tokens.add(token);
        }

        // 额外抓取典型时间/日期模式，提高日期条件命中率
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(20\\d{2}[-年/.]?(0?[1-9]|1[0-2])?([-月/.]?(0?[1-9]|[12]\\d|3[01]))?)")
                .matcher(normalized);
        while (m.find()) {
            String k = m.group(1);
            if (k != null && k.length() >= 4) {
                tokens.add(k);
                tokens.add(k.replace("年", "-").replace("月", "-").replace("日", "").replace("/", "-").replace(".", "-"));
            }
        }

        return new ArrayList<>(tokens);
    }

    private double computeRequirementMatchScore(ExtractedFieldEntity field, List<String> reqTokens) {
        if (reqTokens == null || reqTokens.isEmpty()) return 0.0;

        String content = ((field.getFieldName() != null ? field.getFieldName() : "") + " "
                + (field.getFieldValue() != null ? field.getFieldValue() : "") + " "
                + (field.getSourceText() != null ? field.getSourceText() : "")).toLowerCase();

        int matched = 0;
        int strongMatched = 0;
        for (String token : reqTokens) {
            if (token == null || token.isBlank()) continue;
            if (content.contains(token)) {
                matched++;
                if ((field.getFieldValue() != null && field.getFieldValue().toLowerCase().contains(token))
                        || (field.getFieldName() != null && field.getFieldName().toLowerCase().contains(token))) {
                    strongMatched++;
                }
            }
        }

        if (matched == 0) {
            return -0.8;
        }

        double ratio = (double) matched / reqTokens.size();
        double strongRatio = (double) strongMatched / reqTokens.size();
        return Math.min(1.2, ratio * 0.9 + strongRatio * 0.5);
    }

    private CandidateResult selectBestMandatoryCandidate(TemplateSlotEntity slot,
                                                          List<ExtractedFieldEntity> allFields,
                                                          List<ExtractedFieldEntity> allFieldsRaw,
                                                          Map<String, List<String>> aliasDict,
                                                          String userRequirement,
                                                          Map<Long, Double> requirementDocScores) {
        List<CandidateResult> candidates = recallCandidates(slot, allFields, allFieldsRaw, aliasDict, userRequirement, requirementDocScores);
        for (CandidateResult cr : candidates) {
            if (cr != null && cr.field != null && isMeaningfulValue(cr.field.getFieldValue())) {
                return cr;
            }
        }

        // 兜底兜底：按标签重叠度选择一个可用值
        String slotLabel = slot.getLabel() != null ? slot.getLabel().trim().toLowerCase() : "";
        ExtractedFieldEntity best = null;
        int bestOverlap = -1;
        for (ExtractedFieldEntity f : allFields) {
            if (!isMeaningfulValue(f.getFieldValue())) continue;
            String name = (f.getFieldName() != null ? f.getFieldName() : "").trim().toLowerCase();
            int overlap = countOverlapChars(slotLabel, name);
            if (overlap > bestOverlap) {
                best = f;
                bestOverlap = overlap;
            }
        }
        if (best == null) return null;
        CandidateResult cr = new CandidateResult();
        cr.field = best;
        cr.score = 0.35;
        cr.aliasScore = 0.2;
        cr.reqScore = 0.0;
        return cr;
    }

    private boolean isMeaningfulValue(String value) {
        if (value == null) return false;
        String v = value.trim();
        if (v.isEmpty()) return false;
        Set<String> invalid = Set.of("—", "–", "-", "无", "n/a", "N/A", "null", "未知", "暂无", "待填写", "未填写", "/", "none", "None");
        return !invalid.contains(v);
    }

    private Map<Long, Double> buildRequirementDocScores(List<ExtractedFieldEntity> fields, String requirement) {
        List<String> reqTokens = extractRequirementTokens(requirement);
        if (fields == null || fields.isEmpty() || reqTokens.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, StringBuilder> docTextMap = new HashMap<>();
        for (ExtractedFieldEntity field : fields) {
            if (field.getDocId() == null) continue;
            StringBuilder builder = docTextMap.computeIfAbsent(field.getDocId(), k -> new StringBuilder());
            if (field.getFieldName() != null) builder.append(field.getFieldName()).append(' ');
            if (field.getFieldValue() != null) builder.append(field.getFieldValue()).append(' ');
            if (field.getSourceText() != null) builder.append(field.getSourceText()).append(' ');
        }

        Map<Long, Double> docScores = new HashMap<>();
        for (Map.Entry<Long, StringBuilder> entry : docTextMap.entrySet()) {
            String text = entry.getValue().toString().toLowerCase();
            int matched = 0;
            for (String token : reqTokens) {
                if (token != null && !token.isBlank() && text.contains(token)) {
                    matched++;
                }
            }
            if (matched == 0) {
                docScores.put(entry.getKey(), -0.6);
            } else {
                double ratio = (double) matched / reqTokens.size();
                docScores.put(entry.getKey(), Math.min(1.0, ratio + 0.2));
            }
        }
        return docScores;
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

    /**
     * 带超时机制的LLM调用包装。在独立线程执行LLM调用，超时后立即返回null，不阻塞主流程。
     */
    private String safeLlmCall(String prompt, long timeoutMs) {
        try {
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return llmService.generateText(prompt);
                } catch (Exception e) {
                    log.warn("LLM调用异常: {}", e.getMessage());
                    return null;
                }
            });
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.warn("LLM调用超时({}ms)，跳过", timeoutMs);
            return null;
        } catch (Exception e) {
            log.warn("LLM调用失败: {}", e.getMessage());
            return null;
        }
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
        double reqScore;
    }

    private static class RequirementFilter {
        LocalDate startDate;
        LocalDate endDate;
        List<String> tokens = Collections.emptyList();

        boolean isEmpty() {
            return startDate == null && endDate == null && (tokens == null || tokens.isEmpty());
        }
    }

    private static class LlmJudgment {
        String selectedFieldId;
        String selectedValue;
        double modelConfidence;
        String reason;
    }
}
