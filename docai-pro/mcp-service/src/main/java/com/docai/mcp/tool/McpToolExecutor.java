package com.docai.mcp.tool;

import com.docai.mcp.model.McpToolResult;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

// MCP工具的执行接口
public interface McpToolExecutor {

    // 获取工具名称
    String getToolName();

    // 获取工具的描述
    String getToolDescription();

    // 获取工具的参数定义
    Map<String, Object> getInputSchema();

    // 调用工具执行
    McpToolResult execute(Map<String, Object> arguments);
}
