package com.docai.mcp.service;

import com.docai.mcp.model.McpToolCall;
import com.docai.mcp.model.McpToolResult;
import com.docai.mcp.tool.McpTool;

import java.util.List;

// mcp服务层接口
public interface McpServerService {

    List<McpTool> listTools();

    McpToolResult callTool(McpToolCall mcpToolCall);
}
