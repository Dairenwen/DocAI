package com.docai.ai.controller;

import com.docai.ai.entity.ExtractedFieldEntity;
import com.docai.ai.entity.SourceDocumentEntity;
import com.docai.ai.service.ExtractionService;
import com.docai.common.util.JwtUtil;
import com.docai.common.util.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/source")
public class ExtractionController {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ExtractionService extractionService;

    @PostMapping("/upload")
    public Result<SourceDocumentEntity> uploadAndExtract(
            @RequestHeader("Authorization") String authorization,
            @RequestParam("file") MultipartFile file
    ) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) return Result.badRequest("无效的令牌");
        SourceDocumentEntity doc = extractionService.uploadAndExtract(file, userId);
        return Result.success("文档上传并抽取成功", doc);
    }

    @GetMapping("/{docId}/fields")
    public Result<List<ExtractedFieldEntity>> getFields(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long docId
    ) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) return Result.badRequest("无效的令牌");
        return Result.success("查询成功", extractionService.getFieldsByDocId(docId));
    }

    @GetMapping("/documents")
    public Result<List<SourceDocumentEntity>> getUserDocuments(
            @RequestHeader("Authorization") String authorization
    ) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) return Result.badRequest("无效的令牌");
        return Result.success("查询成功", extractionService.getUserDocuments(userId));
    }

    @GetMapping("/{docId}")
    public Result<SourceDocumentEntity> getDocument(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long docId
    ) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) return Result.badRequest("无效的令牌");
        return Result.success("查询成功", extractionService.getDocument(docId));
    }
}
