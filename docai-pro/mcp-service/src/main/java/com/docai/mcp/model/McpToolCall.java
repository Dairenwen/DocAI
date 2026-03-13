package com.docai.mcp.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

// MCP工具调用类
@Data
public class McpToolCall {

    // 工具名称
    @NotBlank(message = "工具名称不能为空")
    private String name;

    // 调用参数
    @NotNull(message = "调用参数不能为空")
    private Map<String, Object> arguments;
}
