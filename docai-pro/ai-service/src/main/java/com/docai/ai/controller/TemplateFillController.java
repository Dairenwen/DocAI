package com.docai.ai.controller;

import com.docai.ai.entity.FillAuditLogEntity;
import com.docai.ai.entity.FillDecisionEntity;
import com.docai.ai.entity.TemplateFileEntity;
import com.docai.ai.entity.TemplateSlotEntity;
import com.docai.ai.service.TemplateFillService;
import com.docai.common.service.EmailService;
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

    @Autowired
    private EmailService emailService;

    @PostMapping("/upload")
    public Result<TemplateFileEntity> uploadTemplate(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam("file") MultipartFile file
    ) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) return Result.badRequest("无效的令牌");
        try {
            return Result.success("模板上传成功", templateFillService.uploadTemplate(file, userId));
        } catch (RuntimeException e) {
            return Result.badRequest(e.getMessage());
        }
    }

    @PostMapping("/{templateId}/parse")
    public Result<List<TemplateSlotEntity>> parseSlots(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long templateId
    ) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) return Result.badRequest("无效的令牌");
        try {
            return Result.success("模板解析成功", templateFillService.parseSlots(templateId));
        } catch (RuntimeException e) {
            return Result.badRequest("模板解析失败: " + e.getMessage());
        }
    }

    @PostMapping("/{templateId}/fill")
    public Result<Map<String, Object>> autoFill(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long templateId,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) return Result.badRequest("无效的令牌");
        List<Long> docIds = null;
        String userRequirement = null;
        if (body != null) {
            Object rawIds = body.get("docIds");
            if (rawIds instanceof List<?> list) {
                docIds = list.stream().map(item -> Long.valueOf(item.toString())).collect(java.util.stream.Collectors.toList());
            }
            Object rawReq = body.get("userRequirement");
            if (rawReq instanceof String s && !s.isBlank()) {
                userRequirement = s;
            }
        }
        try {
            return Result.success("自动填表完成", templateFillService.autoFill(templateId, docIds, userId, userRequirement));
        } catch (RuntimeException e) {
            return Result.badRequest(e.getMessage());
        }
    }

    @GetMapping("/{templateId}/audit")
    public Result<List<FillAuditLogEntity>> getAuditLog(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long templateId
    ) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) return Result.badRequest("无效的令牌");
        return Result.success("查询成功", templateFillService.getAuditLog(templateId));
    }

    @GetMapping("/{templateId}/decisions")
    public Result<List<FillDecisionEntity>> getDecisions(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long templateId
    ) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) return Result.badRequest("无效的令牌");
        return Result.success("查询成功", templateFillService.getDecisions(templateId));
    }

    @GetMapping("/list")
    public Result<List<TemplateFileEntity>> getUserTemplates(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) return Result.badRequest("无效的令牌");
        return Result.success("查询成功", templateFillService.getUserTemplates(userId));
    }

    @GetMapping("/{templateId}/download")
    public ResponseEntity<Resource> downloadResult(
            @RequestHeader(value = "Authorization", required = false) String authorization,
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

    @PostMapping("/{templateId}/send-email")
    public Result<String> sendResultEmail(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long templateId,
            @RequestBody Map<String, String> body
    ) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) return Result.badRequest("无效的令牌");

        String email = body != null ? body.get("email") : null;
        if (email == null || email.isBlank() || !email.matches("^[\\w.+-]+@[\\w.-]+\\.[a-zA-Z]{2,}$")) {
            return Result.badRequest("请输入有效的邮箱地址");
        }

        try {
            String filePath = templateFillService.downloadResult(templateId);
            File file = new File(filePath);
            if (!file.exists()) return Result.badRequest("填充结果文件不存在，请重新填表");

            byte[] fileBytes = java.nio.file.Files.readAllBytes(file.toPath());
            String contentType = file.getName().endsWith(".xlsx")
                    ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    : "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

            emailService.sendEmailWithAttachment(
                    email,
                    "DocAI智能填表结果 - " + file.getName(),
                    "<h3>DocAI 智能填表结果</h3><p>您的填表结果已完成，请查收附件。</p><p style='color:#888;font-size:12px;'>此邮件由DocAI系统自动发送</p>",
                    file.getName(),
                    fileBytes,
                    contentType
            );
            return Result.success("邮件发送成功", "ok");
        } catch (RuntimeException e) {
            return Result.badRequest(e.getMessage());
        } catch (Exception e) {
            return Result.badRequest("邮件发送失败: " + e.getMessage());
        }
    }
}
