package com.docai.mcp.model;

import lombok.Builder;
import lombok.Data;

// MCP工具调用结果类
@Data
@Builder
public class McpToolResult {

    // 工具名称
    private String toolName;

    // 调用结果
    private Object content;

    // 调用是否成功
    private Boolean success;

    // 调用错误信息
    private String error;
}
