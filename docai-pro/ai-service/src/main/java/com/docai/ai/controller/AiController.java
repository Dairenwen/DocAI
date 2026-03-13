package com.docai.ai.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.docai.ai.dto.request.AiChatRequest;
import com.docai.ai.dto.request.AiRequestHistoryRequest;
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
            @RequestHeader("Authorization") String authorization,
            @RequestBody AiChatRequest aiChatRequest
    ) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        SseEmitter sseEmitter = new SseEmitter();
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
            aiService.streamUnifiedChat(aiChatRequest, userId, sseEmitter);
        }).start();

        return sseEmitter;
    }


    // 请求分页查询记录
    @GetMapping("/requests")
    public Result<IPage<AiRequestHistoryResponse>> getRequestHistory(
            @RequestHeader("Authorization") String authorization,
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
            @RequestHeader("Authorization") String authorization,
            @RequestBody @Validated SendEmailRequest sendEmailRequest
    ) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) {
            return Result.badRequest("无效的令牌");
        }
        return Result.success("邮件发送成功", aiService.sendEmailWithExcel(sendEmailRequest));
    }

}
