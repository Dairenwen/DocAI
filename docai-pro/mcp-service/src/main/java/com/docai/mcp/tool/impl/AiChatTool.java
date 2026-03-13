package com.docai.mcp.tool.impl;

import com.docai.mcp.model.McpToolResult;
import com.docai.mcp.tool.McpToolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

// AI对话工具
@Service
@Slf4j
public class AiChatTool  implements McpToolExecutor {

    @Autowired
    private WebClient.Builder webClientBuilder;

    @Value("${mcp.services.gateway-url}")
    private String gatewayUrl;

    @Override
    public String getToolName() {
        return "ai_chat";
    }

    @Override
    public String getToolDescription() {
        return "AI智能对话工具";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "message", Map.of(
                        "type", "string",
                        "description", "用户消息内容"
                ),
                "fileId", Map.of(
                        "type", "integer",
                        "description", "要分析的excel文件ID"
                ),
                "token", Map.of(
                        "type", "string",
                        "description", "用户的个人认证令牌"
                )
        ));
        schema.put("required", Arrays.asList("message", "fileId", "token"));
        return schema;
    }

    @Override
    public McpToolResult execute(Map<String, Object> arguments) {
        // 1. 提取参数
        String token = (String) arguments.get("token");
        Long fileId = Long.valueOf(arguments.get("fileId").toString());
        String userInput = (String) arguments.get("message") ;
        // 2. 发起请求
        WebClient webClient = webClientBuilder != null ?
                webClientBuilder.build() : WebClient.builder().build();
        String bearer = token.startsWith("Bearer ") ?token : "Bearer " +token;
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("userInput", userInput);
        requestBody.put("fileId", fileId );
        // 3. 判断响应结果
        try {
            String response = webClient.post()
                    .uri(gatewayUrl + "/api/v1/ai/chat/stream")
                    .headers(h -> {
                        h.add("Authorization", bearer);
                        h.add("Accept", "text/event-stream");
                    })
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(120))
                    .block();
            // 4. 封装返回对象
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
