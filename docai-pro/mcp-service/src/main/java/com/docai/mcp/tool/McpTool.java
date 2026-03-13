package com.docai.mcp.tool;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

// MCP工具类
@Data
@Builder
public class McpTool {

    // 工具名称
    private String name;

    // 工具描述
    private String description;

    // 工具输入参数定义
    private Map<String, Object> inputSchema;

    // 工具参数列表
    private List<McpParameter> parameters;


    // 工具参数定义
    @Data
    @Builder
    public static class McpParameter {

        // 参数名称
        private String name;

        // 参数类型
        private String type;

        // 参数的描述
        private  String description;

        // 是否必填
        private Boolean required;
    }
}
