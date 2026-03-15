package com.docai.ai.controller;

import com.docai.ai.dto.request.ProviderSwitchRequest;
import com.docai.ai.service.LlmService;
import com.docai.common.util.JwtUtil;
import com.docai.common.util.Result;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 大模型管理的控制器
 */
@RestController
@RequestMapping("/llm")
public class LlmController {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private LlmService llmService;


    // 获取所有可用的大模型列表
    @GetMapping("/providers/list")
    public Result<List<Map<String, Object>>> getProvidersList(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) {
            return  Result.badRequest("无效的令牌");
        }

        return Result.success("获取大模型提供商列表成功", llmService.getProvidersList());
    }

    // 切换大模型提供商
    @PostMapping("/providers/switch")
    public Result<Map<String, String>> switchProvider(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody @Valid ProviderSwitchRequest request
    ) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) {
            return  Result.badRequest("无效的令牌");
        }
        return Result.success("大模型提供商切换成功", llmService.switchProvider(request.getProviderName()));
    }

    // 获取当前正在使用的大模型提供商
    @GetMapping("/providers/current")
    public Result<Map<String, String>> getCurrentProvider(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) {
            return  Result.badRequest("无效的令牌");
        }
        return Result.success("获取当前大模型提供商成功", llmService.getCurrentProviderName());
    }


}
