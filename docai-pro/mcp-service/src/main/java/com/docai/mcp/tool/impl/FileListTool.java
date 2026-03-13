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

// 文件列表工具
@Service
@Slf4j
public class FileListTool implements McpToolExecutor {

    @Autowired
    private WebClient.Builder webClientBuilder;

    @Value("${mcp.services.gateway-url}")
    private String gatewayUrl;


    @Override
    public String getToolName() {
        return "file_list";
    }

    @Override
    public String getToolDescription() {
        return "文件列表查询工具";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "pageNum", Map.of(
                        "type", "integer",
                        "description", "页码"
                ),
                "pageSize", Map.of(
                        "type", "integer",
                        "description", "每页大小"
                ),
                "token", Map.of(
                        "type", "string",
                        "description", "用户的个人认证令牌"
                )
        ));
        schema.put("required", Arrays.asList("token"));
        return schema;
    }

    @Override
    public McpToolResult execute(Map<String, Object> arguments) {
        // 1. 提取参数
        String token = (String) arguments.get("token");
        Integer pageNum = arguments.get("pageNum") != null ? Integer.valueOf(arguments.get("pageNum").toString()): 1;
        Integer pageSize = arguments.get("pageSize") != null ? Integer.valueOf(arguments.get("pageSize").toString()): 10;
        // 2. 发起请求
        StringBuilder url = new StringBuilder(gatewayUrl + "/api/v1/files/list");
        url.append("?pageNum=").append(pageNum);
        url.append("&pageSize=").append(pageSize);

        WebClient webClient = webClientBuilder != null ?
                webClientBuilder.build() : WebClient.builder().build();
        String bearer = token.startsWith("Bearer ") ?token : "Bearer " +token;
        // 3. 判断响应结果
        try {
            String response = webClient.get()
                    .uri(url.toString())
                    .headers(h -> {
                        h.add("Authorization", bearer);
                    })
                    .retrieve()
                    .bodyToMono(String.class)
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
