package com.docai.ai.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.docai.ai.dto.request.AiChatRequest;
import com.docai.ai.dto.request.AiRequestHistoryRequest;
import com.docai.ai.dto.request.SendContentEmailRequest;
import com.docai.ai.dto.request.SendEmailRequest;
import com.docai.ai.dto.response.AiRequestHistoryResponse;
import com.docai.ai.service.AiService;
import com.docai.common.util.JwtUtil;
import com.docai.common.util.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

/**
 * AI服务控制器
 */
@RestController
@RequestMapping("/ai")
public class AiController {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private AiService aiService;

    // AI流式对话
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamUnifiedChat(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody AiChatRequest aiChatRequest
    ) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        SseEmitter sseEmitter = new SseEmitter(300_000L);
        sseEmitter.onTimeout(sseEmitter::complete);
        sseEmitter.onError(e -> sseEmitter.complete());
        if (userId == null) {
            try {
                sseEmitter.send(SseEmitter
                        .event()
                        .name("error")
                        .data("{\"error\":\"无效的令牌\"}")
                );
                sseEmitter.complete();
                return sseEmitter;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        new Thread(() -> {
            try {
                aiService.streamUnifiedChat(aiChatRequest, userId, sseEmitter);
            } catch (Exception e) {
                try {
                    sseEmitter.send(SseEmitter.event().name("error")
                            .data("{\"error\":\"服务处理异常，请稍后重试\"}"));
                    sseEmitter.complete();
                } catch (Exception ignored) {
                    sseEmitter.completeWithError(e);
                }
            }
        }).start();

        return sseEmitter;
    }


    // 请求分页查询记录
    @GetMapping("/requests")
    public Result<IPage<AiRequestHistoryResponse>> getRequestHistory(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Validated AiRequestHistoryRequest aiRequestHistoryRequest
    ) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) {
            return Result.badRequest("无效的令牌");
        }
        IPage<AiRequestHistoryResponse> response = aiService.getRequestHistory(aiRequestHistoryRequest, userId);
        return Result.success("查询成功", response);
    }

    // 发送修改后的excel文件到邮箱
    @PostMapping("/send-email")
    public Result<Boolean> sendEmailWithExcel(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody @Validated SendEmailRequest sendEmailRequest
    ) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) {
            return Result.badRequest("无效的令牌");
        }
        return Result.success("邮件发送成功", aiService.sendEmailWithExcel(sendEmailRequest));
    }

    // 发送AI内容到邮箱（纯文本/HTML）
    @PostMapping("/send-content-email")
    public Result<Boolean> sendContentEmail(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody @Validated SendContentEmailRequest request
    ) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) {
            return Result.badRequest("无效的令牌");
        }
        try {
            return Result.success("邮件发送成功", aiService.sendContentEmail(request));
        } catch (RuntimeException e) {
            return Result.serverError(e.getMessage());
        }
    }

    // 将AI修改后的内容保存为新文档版本，返回下载URL
    @PostMapping("/documents/{docId}/apply-edit")
    public Result<Map<String, String>> applyDocumentEdit(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long docId,
            @RequestBody Map<String, String> body
    ) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) {
            return Result.badRequest("无效的令牌");
        }
        String content = body.get("content");
        if (content == null || content.isBlank()) {
            return Result.badRequest("修改内容不能为空");
        }
        try {
            Map<String, String> result = aiService.applyDocumentEdit(docId, userId, content);
            return Result.success("文档保存成功", result);
        } catch (RuntimeException e) {
            return Result.serverError(e.getMessage());
        }
    }

    // 下载AI修改后的文档
    @GetMapping("/documents/edited/{fileName}")
    public void downloadEditedDocument(
            @PathVariable String fileName,
            jakarta.servlet.http.HttpServletResponse response
    ) {
        // 安全校验：只允许字母、数字、下划线、点、中文、连字符
        if (!fileName.matches("[\\w.\\-\\u4e00-\\u9fff]+")) {
            response.setStatus(400);
            return;
        }
        try {
            java.io.File file = new java.io.File(System.getProperty("java.io.tmpdir"), "docai_edited/" + fileName);
            if (!file.exists() || !file.getCanonicalPath().startsWith(
                    new java.io.File(System.getProperty("java.io.tmpdir"), "docai_edited").getCanonicalPath())) {
                response.setStatus(404);
                return;
            }
            response.setContentType("application/octet-stream");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + 
                java.net.URLEncoder.encode(fileName, "UTF-8") + "\"");
            try (var fis = new java.io.FileInputStream(file);
                 var os = response.getOutputStream()) {
                fis.transferTo(os);
            }
        } catch (Exception e) {
            response.setStatus(500);
        }
    }

}
