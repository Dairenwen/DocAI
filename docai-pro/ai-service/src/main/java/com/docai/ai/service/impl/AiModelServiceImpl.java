package com.docai.ai.service.impl;

import com.docai.ai.service.AiModelService;
import com.docai.ai.service.SQLGenerationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI大模型服务的实现类
 */
@Service
@Slf4j
public class AiModelServiceImpl implements AiModelService {

    @Autowired
    private SQLGenerationService sqlGenerationService;


    @Override
    public String generateAiResponse(String prompt, List<Map<String, Object>> resultData) {
        // 1. 构建一下响应上下文
        StringBuilder context = new StringBuilder();
        context.append("用户请求: ").append(prompt).append("\n");
        if (resultData != null) {
            context.append("查询结果数量: ").append(resultData.size()).append("\n");
            context.append("查询结果示例：").append(resultData.get(0)).append("\n");
        }

        // 2. 使用AI生成响应
        String result = "基于以下信息，生成友好的AI响应" + context;

        return sqlGenerationService.get(result);
    }

    @Override
    public List<String> getFieldsFromUserInput(String userInput) {
        // 1. 构建提示词
        String prompt = String.format("请从用户的问题中提取出来关键的字段（列名）\n"
                +"要求：\n"
                +"  1.只提取用户问题中的内容，不要延伸\n"
                +"  2.假如提取到多个关键字段，请用逗号分隔开来\n"
                +"  3.取不到关键字段，直接返回个无\n"
                +"  4.不要添加任何解释\n"
                +"示例：\n"
                +"用户问题：请问苹果手机的价格是多少？\n"
                +"提取结果：价格,苹果手机\n"
                +"实际用户输入的问题：%s\n"
        , userInput);

        // 2. 调用大模型获取AI响应
        String response = sqlGenerationService.get(prompt);
        String[] parts = response.split(",.\n");
        List<String> results = new ArrayList<>();
        for (String part :parts) {
            results.add(part.trim());
        }

        return results;
    }

    @Override
    public String getSql(String userInput, String tableName, List<Map<String, Object>> tableStructure) {
        // 1.提取表字段名
        List<String> headers = tableStructure.stream()
                .map(row -> (String) row.get("Field"))
                .filter(field -> field != null && !field.isEmpty())
                .toList();
        // 2.构建提示词
        String prompt = String.format("" +
                "你是一个SQL专家。能够根据用户的需求生成mysql的查询语句， 特别注意需要使用模糊查询。\n" +
                "表名：%s \n"+
                "表结构：%s \n"+
                "用户需求：%s \n"+
                "要求：\n" +
                " 1. 无论用户输入什么内容，你都需要生成SQL查询语句\n"+
                " 2. 忽略掉绘制等词汇的影响，只提取数据查询需求，并生成SQL\n"+
                " 3. 生成的SQL必须是可以执行的，不能有语法错误\n"+
                " 4. 只生成可以执行的sql语句，不要携带任何额外的内容\n"
                , tableName, headers, userInput
        );
        String response = sqlGenerationService.get(prompt);
        log.info("最终生成的SQL:{}", response.trim());
        return response.trim();
    }

    @Override
    public String getUpdateSql(String userInput, String tableName, List<Map<String, Object>> tableStructure) {
        // 1.提取表字段名
        List<String> headers = tableStructure.stream()
                .map(row -> (String) row.get("Field"))
                .filter(field -> field != null && !field.isEmpty())
                .toList();
        // 2.构建提示词
        String prompt = String.format("" +
                        "你是一个SQL专家。能够根据用户的需求生成mysql的修改语句。\n" +
                        "表名：%s \n"+
                        "表结构：%s \n"+
                        "用户需求：%s \n"+
                        "要求：\n" +
                        " 1. 无论用户输入什么内容，你都需要生成SQL修改语句\n"+
                        " 2. 忽略掉绘制等词汇的影响，只提取数据修改需求，并生成SQL\n"+
                        " 3. 生成的SQL必须是可以执行的，不能有语法错误\n"+
                        " 4. 只生成可以执行的sql语句，不要携带任何额外的内容\n"
                , tableName, headers, userInput
        );
        String response = sqlGenerationService.get(prompt);
        log.info("最终生成的SQL:{}", response.trim());
        return response.trim();
    }
}
