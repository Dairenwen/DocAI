package com.docai.mcp;

import com.docai.mcp.model.McpToolCall;
import com.docai.mcp.model.McpToolResult;
import com.docai.mcp.service.McpServerService;
import com.docai.mcp.tool.McpTool;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

// 标准输入输出模式的MCP server
@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class
})
@Slf4j
public class McpStdioServer {

    private static ConfigurableApplicationContext applicationContext;

    private static McpServerService mcpServerService;

    private static final ObjectMapper objectMapper = new ObjectMapper();


    public static void main(String[] args)  {
        // 运行stdio模式
        System.setProperty("spring.main.web-application-type", "none");
        try {
            runStdioMode();
        } catch (Exception e) {
            log.error("执行stdio模式失败");
        }
    }

    // 运行stdio模式
    private static  void  runStdioMode() throws IOException {

        // 1. 启动spring上下文
        System.setProperty("spring.cloud.nacos.config.enabled", "false");
        System.setProperty("spring.cloud.nacos.discovery.enabled", "false");
        applicationContext = SpringApplication.run(McpStdioServer.class, new String[0]);
        mcpServerService = applicationContext.getBean(McpServerService.class);

        // 2. 设置响应的编码格式
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.err.println("MCP Stdio服务器已经启动");
        // 3. 给服务器发送消息
        if (mcpServerService != null) {
            List<McpTool> tools = mcpServerService.listTools();
            System.err.println("已经注册的工具数量:" + tools.size());
        }
        System.err.flush();
        // 4. 解析标准的消息，处理消息
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.trim().isEmpty()) {
                continue;
            }

            Map<String, Object> jsonRpcMessage = objectMapper.readValue(
                    line,
                    new TypeReference<Map<String, Object>>() {
                    }
            );

            // 5. 处理消息
            Map<String, Object> response = handleJsonRpcMessage(jsonRpcMessage);
            if (response != null) {
                String responseJson = objectMapper.writeValueAsString(response);
                System.out.println(responseJson);
                System.out.flush();
            }

        }
    }

    // 处理消息
    private static Map<String, Object> handleJsonRpcMessage(Map<String, Object> message) {
        // 1. 提取消息的内容
        Object id = message.get("id");
        String method = message.get("method").toString();
        Map<String, Object> params = (Map<String, Object>) message.get("params");

        // 2. 构建响应
        Map<String, Object> response = new HashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);

        switch (method) {
            case "initialize":
                Map<String, Object> result = new HashMap<>();
                result.put("prorocolVersion", "2026");
                Map<String, Object> capabilities = new HashMap<>();
                Map<String, Object> toolCapability = new HashMap<>();
                capabilities.put("tools", toolCapability);
                result.put("capabilities", capabilities);
                result.put("serverInfo", Map.of(
                        "name", "chat2ExcelMcp",
                        "version", "1.0.0"
                ));
                response.put("result", result);
                break;
            case "initialized":
                return null;
            case  "tools/list":
                List<McpTool> tools = mcpServerService.listTools();
                List<Map<String, Object>> toolsList = new ArrayList<>();
                for (McpTool tool : tools) {
                    Map<String, Object> toolMap = new HashMap<>();
                    toolMap.put("name", tool.getName());
                    toolMap.put("description", tool.getDescription());
                    toolMap.put("inputSchema", tool.getInputSchema());
                    toolsList.add(toolMap);
                }
                Map<String, Object> endResult = new HashMap<>();
                endResult.put("tools", toolsList);
                response.put("result", endResult);
                break;
            case "tools/call":
                McpToolCall toolCall = new McpToolCall();
                toolCall.setName(params.get("name").toString());
                toolCall.setArguments((Map<String, Object>) params.get("arguments"));
                McpToolResult mcpToolResult = mcpServerService.callTool(toolCall);
                Map<String, Object> resultMap = new HashMap<>();
                if (!mcpToolResult.getSuccess()) {
                    resultMap.put("isError", true);
                    resultMap.put("content", Arrays.asList(
                            Map.of(
                                    "type", "text",
                                    "text", mcpToolResult.getError()
                            )
                    ));
                } else {
                    resultMap.put("content", Arrays.asList(
                            Map.of(
                                    "type", "text",
                                    "text", mcpToolResult.getContent()
                            )
                    ));
                }
                response.put("result", resultMap);
                break;
            default:
                response.put("error", Map.of(
                        "code", -32601,
                        "message", "method not found " + method
                ));
                break;
        }
        return response;
    }

}
