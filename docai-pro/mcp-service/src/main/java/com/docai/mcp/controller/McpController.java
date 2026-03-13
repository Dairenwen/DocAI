package com.docai.mcp.controller;

import com.docai.mcp.model.McpToolCall;
import com.docai.mcp.model.McpToolResult;
import com.docai.mcp.service.McpServerService;
import com.docai.mcp.tool.McpTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// mcp的控制器类
@RestController
@RequestMapping("/mcp")
@Slf4j
public class McpController {


    @Autowired
    private McpServerService mcpServerService;

    // 获取所有可用的工具列表
    @GetMapping("/tools")
    public ResponseEntity<Map<String, Object>> listTools() {
        // 1. 从服务层去获取所有已经注册的MCP工具
        List<McpTool> tools = mcpServerService.listTools();
        // 2. 构造响应
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("message", "success");
        response.put("data", tools);
        return ResponseEntity.ok(response);
    }


    // 执行工具调用
    @PostMapping("/tools/call")
    public ResponseEntity<Map<String, Object>> callTool(
            @RequestBody @Validated McpToolCall mcpToolCall
    ) {
        McpToolResult result = mcpServerService.callTool(mcpToolCall);

        // 构建响应
        Map<String, Object> response = new HashMap<>();
        response.put("code", result.getSuccess() ? 200 : 500);
        response.put("message", result.getSuccess() ? "success" : result.getError());
        response.put("data", result);
        return ResponseEntity.ok(response);
    }
}
