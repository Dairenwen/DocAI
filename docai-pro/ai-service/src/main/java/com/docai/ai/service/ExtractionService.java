package com.docai.ai.service;

import com.docai.ai.entity.ExtractedFieldEntity;
import com.docai.ai.entity.SourceDocumentEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 源文档抽取服务 - 阶段1&2
 * 负责源文档上传、结构化抽取、字段标准化
 */
public interface ExtractionService {

    /**
     * 上传源文档并异步抽取
     */
    SourceDocumentEntity uploadAndExtract(MultipartFile file, Long userId);

    /**
     * 查询文档已抽取字段（校验用户归属）
     */
    List<ExtractedFieldEntity> getFieldsByDocId(Long docId, Long userId);

    /**
     * 查询用户所有源文档
     */
    List<SourceDocumentEntity> getUserDocuments(Long userId);

    /**
     * 获取源文档详情（校验用户归属）
     */
    SourceDocumentEntity getDocument(Long docId, Long userId);

    /**
     * 删除源文档及其提取字段（校验用户归属）
     */
    boolean deleteDocument(Long docId, Long userId);

    /**
     * 批量删除源文档（校验用户归属）
     */
    boolean batchDeleteDocuments(List<Long> docIds, Long userId);
}
