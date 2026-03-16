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
import java.util.Map;

@RestController
@RequestMapping("/source")
public class ExtractionController {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ExtractionService extractionService;

    @PostMapping("/upload")
    public Result<SourceDocumentEntity> uploadAndExtract(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam("file") MultipartFile file
    ) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) return Result.badRequest("无效的令牌");
        try {
            SourceDocumentEntity doc = extractionService.uploadAndExtract(file, userId);
            return Result.success("文档上传并抽取成功", doc);
        } catch (RuntimeException e) {
            return Result.serverError("文档上传失败: " + e.getMessage());
        }
    }

    @GetMapping("/{docId}/fields")
    public Result<List<ExtractedFieldEntity>> getFields(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long docId
    ) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) return Result.badRequest("无效的令牌");
        return Result.success("查询成功", extractionService.getFieldsByDocId(docId, userId));
    }

    @GetMapping("/documents")
    public Result<List<SourceDocumentEntity>> getUserDocuments(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) return Result.badRequest("无效的令牌");
        return Result.success("查询成功", extractionService.getUserDocuments(userId));
    }

    /**
     * 轻量级状态轮询接口：只返回 parsing 状态文档的 id 和 uploadStatus，
     * 前端用于高效轮询而无需加载完整文档列表。
     */
    @GetMapping("/documents/status")
    public Result<List<Map<String, Object>>> getDocumentStatuses(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) return Result.badRequest("无效的令牌");
        List<SourceDocumentEntity> docs = extractionService.getUserDocuments(userId);
        List<Map<String, Object>> statusList = docs.stream().map(doc -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", doc.getId());
            m.put("uploadStatus", doc.getUploadStatus());
            m.put("docSummary", doc.getDocSummary());
            return m;
        }).toList();
        return Result.success("查询成功", statusList);
    }

    @GetMapping("/{docId}")
    public Result<SourceDocumentEntity> getDocument(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long docId
    ) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) return Result.badRequest("无效的令牌");
        return Result.success("查询成功", extractionService.getDocument(docId, userId));
    }

    @DeleteMapping("/{docId}")
    public Result<Boolean> deleteDocument(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long docId
    ) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) return Result.badRequest("无效的令牌");
        return Result.success("删除成功", extractionService.deleteDocument(docId, userId));
    }

    @GetMapping("/{docId}/download")
    public void downloadSourceDocument(
            @RequestHeader(value = "Authorization", required = false) String authorization,
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

    @PostMapping("/batch-delete")
    public Result<Boolean> batchDeleteDocuments(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody java.util.Map<String, Object> body
    ) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) return Result.badRequest("无效的令牌");
        Object raw = body.get("docIds");
        if (!(raw instanceof java.util.List<?> rawList) || rawList.isEmpty()) {
            return Result.badRequest("请提供要删除的文档ID");
        }
        java.util.List<Long> docIds = rawList.stream()
                .map(item -> Long.valueOf(item.toString()))
                .collect(java.util.stream.Collectors.toList());
        try {
            return Result.success("删除成功", extractionService.batchDeleteDocuments(docIds, userId));
        } catch (RuntimeException e) {
            return Result.serverError("批量删除失败: " + e.getMessage());
        }
    }
}
