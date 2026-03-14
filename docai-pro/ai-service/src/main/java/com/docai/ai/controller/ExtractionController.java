package com.docai.ai.controller;

import com.docai.ai.entity.ExtractedFieldEntity;
import com.docai.ai.entity.SourceDocumentEntity;
import com.docai.ai.service.ExtractionService;
import com.docai.common.util.JwtUtil;
import com.docai.common.util.Result;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
        return Result.success("查询成功", extractionService.getFieldsByDocId(docId, userId));
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
        return Result.success("查询成功", extractionService.getDocument(docId, userId));
    }

    @DeleteMapping("/{docId}")
    public Result<Boolean> deleteDocument(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long docId
    ) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) return Result.badRequest("无效的令牌");
        return Result.success("删除成功", extractionService.deleteDocument(docId, userId));
    }

    @GetMapping("/{docId}/download")
    public void downloadSourceDocument(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long docId,
            HttpServletResponse response
    ) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        SourceDocumentEntity doc = extractionService.getDocument(docId, userId);
        if (doc == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        File file = new File(doc.getStoragePath());
        if (!file.exists()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        try {
            String encodedName = URLEncoder.encode(doc.getFileName(), StandardCharsets.UTF_8).replace("+", "%20");
            String ext = doc.getFileType() != null ? doc.getFileType().toLowerCase() : "";
            String contentType;
            switch (ext) {
                case "txt": case "md":
                    contentType = "text/plain; charset=utf-8";
                    break;
                case "docx":
                    contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                    break;
                case "xlsx":
                    contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                    break;
                default:
                    contentType = "application/octet-stream";
            }
            response.setContentType(contentType);
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedName);
            response.setContentLengthLong(file.length());
            try (InputStream is = new FileInputStream(file);
                 OutputStream os = response.getOutputStream()) {
                is.transferTo(os);
                os.flush();
            }
        } catch (IOException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @DeleteMapping("/batch")
    public Result<Boolean> batchDeleteDocuments(
            @RequestHeader("Authorization") String authorization,
            @RequestBody java.util.Map<String, java.util.List<Long>> body
    ) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) return Result.badRequest("无效的令牌");
        java.util.List<Long> docIds = body.get("docIds");
        if (docIds == null || docIds.isEmpty()) return Result.badRequest("请提供要删除的文档ID");
        return Result.success("删除成功", extractionService.batchDeleteDocuments(docIds, userId));
    }
}
