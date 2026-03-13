package com.docai.ai.service;

import com.docai.ai.entity.ExtractedFieldEntity;
import com.docai.ai.entity.SourceDocumentEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

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
     * 查询文档已抽取字段
     */
    List<ExtractedFieldEntity> getFieldsByDocId(Long docId);

    /**
     * 查询用户所有源文档
     */
    List<SourceDocumentEntity> getUserDocuments(Long userId);

    /**
     * 获取源文档详情
     */
    SourceDocumentEntity getDocument(Long docId);
}
