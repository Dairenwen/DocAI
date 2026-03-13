package com.docai.mcp.service;

import com.docai.mcp.model.McpToolCall;
import com.docai.mcp.model.McpToolResult;
import com.docai.mcp.tool.McpTool;
import com.docai.mcp.tool.McpToolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// mcp服务层接口的实现类
@Service
@Slf4j
public class McpServerServiceImpl implements McpServerService {

    @Autowired
    private List<McpToolExecutor> toolExecutors;


    @Override
    public List<McpTool> listTools() {
        // 1. 构建工具的基本信息
        return toolExecutors.stream()
                .map(executor -> {
                    McpTool.McpToolBuilder builder = McpTool.builder()
                            .name(executor.getToolName())
                            .description(executor.getToolDescription())
                            .inputSchema(executor.getInputSchema());

                    Map<String, Object> schema = executor.getInputSchema();
                    if (schema != null) {
                        // 提取属性的定义
                        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");

                        // 提取必填字段
                        List<String> required = (List<String>) schema.getOrDefault("required", Collections.emptyList());

                        // 每个属性转换
                        List<McpTool.McpParameter> parameters = properties.entrySet().stream()
                                .map(entry -> {
                                    // 3. 将参数的每个值转换为McpParameter
                                    Map<String, Object> prop = (Map<String, Object>) entry.getValue();
                                    return McpTool.McpParameter.builder()
                                            .name(entry.getKey())
                                            .type((String) prop.get("type"))
                                            .description((String) prop.get("description"))
                                            .required(required.contains(entry.getKey()))
                                            .build();
                                })
                                .collect(Collectors.toList());
                        builder.parameters(parameters);
                    }
                    return builder.build();
                    // 4. 统一返回List<McpTool>
                }).collect(Collectors.toList());
    }

    @Override
    public McpToolResult callTool(McpToolCall mcpToolCall) {

        // 1. 根据toolName获取工具
        McpToolExecutor executor = toolExecutors.stream()
                .filter(e -> e.getToolName().equals(mcpToolCall.getName()))
                .findFirst()
                .orElse(null);
        if (executor == null) {
            return McpToolResult.builder()
                    .toolName(mcpToolCall.getName())
                    .success(false)
                    .error("工具不存在: "+ mcpToolCall.getName())
                    .build();
        }

        // 2. 考虑工具调用
        return executor.execute(mcpToolCall.getArguments());
    }
}
