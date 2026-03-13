package com.docai.ai.controller;

import com.docai.ai.entity.FillAuditLogEntity;
import com.docai.ai.entity.FillDecisionEntity;
import com.docai.ai.entity.TemplateFileEntity;
import com.docai.ai.entity.TemplateSlotEntity;
import com.docai.ai.service.TemplateFillService;
import com.docai.common.util.JwtUtil;
import com.docai.common.util.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/template")
public class TemplateFillController {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private TemplateFillService templateFillService;

    @PostMapping("/upload")
    public Result<TemplateFileEntity> uploadTemplate(
            @RequestHeader("Authorization") String authorization,
            @RequestParam("file") MultipartFile file
    ) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) return Result.badRequest("无效的令牌");
        return Result.success("模板上传成功", templateFillService.uploadTemplate(file, userId));
    }

    @PostMapping("/{templateId}/parse")
    public Result<List<TemplateSlotEntity>> parseSlots(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long templateId
    ) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) return Result.badRequest("无效的令牌");
        return Result.success("模板解析成功", templateFillService.parseSlots(templateId));
    }

    @PostMapping("/{templateId}/fill")
    public Result<Map<String, Object>> autoFill(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long templateId,
            @RequestBody Map<String, List<Long>> body
    ) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) return Result.badRequest("无效的令牌");
        List<Long> docIds = body.get("docIds");
        return Result.success("自动填表完成", templateFillService.autoFill(templateId, docIds, userId));
    }

    @GetMapping("/{templateId}/audit")
    public Result<List<FillAuditLogEntity>> getAuditLog(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long templateId
    ) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) return Result.badRequest("无效的令牌");
        return Result.success("查询成功", templateFillService.getAuditLog(templateId));
    }

    @GetMapping("/{templateId}/decisions")
    public Result<List<FillDecisionEntity>> getDecisions(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long templateId
    ) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) return Result.badRequest("无效的令牌");
        return Result.success("查询成功", templateFillService.getDecisions(templateId));
    }

    @GetMapping("/list")
    public Result<List<TemplateFileEntity>> getUserTemplates(
            @RequestHeader("Authorization") String authorization
    ) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) return Result.badRequest("无效的令牌");
        return Result.success("查询成功", templateFillService.getUserTemplates(userId));
    }

    @GetMapping("/{templateId}/download")
    public ResponseEntity<Resource> downloadResult(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long templateId
    ) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) return ResponseEntity.status(401).build();

        String filePath = templateFillService.downloadResult(templateId);
        File file = new File(filePath);
        if (!file.exists()) return ResponseEntity.notFound().build();

        FileSystemResource resource = new FileSystemResource(file);
        String encodedFileName = URLEncoder.encode(file.getName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }
}
