package com.docai.mcp.tool.impl;

import com.docai.mcp.model.McpToolResult;
import com.docai.mcp.tool.McpToolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

// 用户认证工具
@Service
@Slf4j
public class UserAuthTool implements McpToolExecutor {


    @Autowired
    private WebClient.Builder webClientBuilder;

    @Value("${mcp.services.gateway-url}")
    private String gatewayUrl;

    @Override
    public String getToolName() {
        return "user_auth";
    }

    @Override
    public String getToolDescription() {
        return "用户认证工具";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "username", Map.of(
                        "type", "string",
                        "description", "用户名"
                ),
                "password", Map.of(
                        "type", "string",
                        "description", "密码"
                )
        ));
        schema.put("required", Arrays.asList("username", "password"));
        return schema;
    }

    @Override
    public McpToolResult execute(Map<String, Object> arguments) {
        // 1. 提取参数
        String username = (String) arguments.get("username");
        String password = (String) arguments.get("password");

        // 2. 账号+密码
        Map<String, Object> requestBody = new HashMap<>();
        if (username != null && password != null) {
            requestBody.put("username", username);
            requestBody.put("password", password);
        } else {
            return McpToolResult.builder()
                    .toolName(getToolName())
                    .success(false)
                    .error("请求参数不全")
                    .build();
        }
        // 3. 访问接口 /api/v1/users/auth
        WebClient webClient = webClientBuilder != null ? webClientBuilder.build() : WebClient.builder().build();

        // 4. 判断接口响应
        try {
            String response = webClient.post()
                    .uri(gatewayUrl + "/api/v1/users/auth")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            // 5. 封装结果
            return McpToolResult.builder()
                    .toolName(getToolName())
                    .content(response)
                    .success(true)
                    .build();
        } catch (Exception e) {
            return McpToolResult.builder()
                    .toolName(getToolName())
                    .content("")
                    .success(false)
                    .error("调用工具失败：" + e.getMessage())
                    .build();
        }
    }
}
