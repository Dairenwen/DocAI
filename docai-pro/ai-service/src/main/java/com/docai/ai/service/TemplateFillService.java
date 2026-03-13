package com.docai.ai.service;

import com.docai.ai.entity.FillAuditLogEntity;
import com.docai.ai.entity.FillDecisionEntity;
import com.docai.ai.entity.TemplateFileEntity;
import com.docai.ai.entity.TemplateSlotEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 模板填写服务 - 阶段3-8
 * 负责模板解析、候选召回、LLM判定、写回、审计
 */
public interface TemplateFillService {

    /**
     * 上传模板并解析槽位
     */
    TemplateFileEntity uploadTemplate(MultipartFile file, Long userId);

    /**
     * 解析模板槽位
     */
    List<TemplateSlotEntity> parseSlots(Long templateId);

    /**
     * 自动匹配并填表
     */
    Map<String, Object> autoFill(Long templateId, List<Long> docIds, Long userId);

    /**
     * 获取审计日志
     */
    List<FillAuditLogEntity> getAuditLog(Long templateId);

    /**
     * 获取填写决策详情
     */
    List<FillDecisionEntity> getDecisions(Long templateId);

    /**
     * 获取用户的模板列表
     */
    List<TemplateFileEntity> getUserTemplates(Long userId);

    /**
     * 下载填写结果文件
     */
    String downloadResult(Long templateId);
}
