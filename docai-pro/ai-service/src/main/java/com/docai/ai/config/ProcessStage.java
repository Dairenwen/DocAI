package com.docai.ai.config;

/**
 * 流式处理过程的枚举项
 */
public enum ProcessStage {

    /**
     * 初始化阶段
     */
    INIT("INIT", "初始化", 0),

    /**
     * 校验文件权限
     */
    VALIDATE_FILE("VALIDATE_FILE", "验证文件", 15),

    /**
     * 获取表名
     */
    GET_TABLE_NAMES("GET_TABLE_NAMES", "获取表名", 18),

    /**
     * 获取表结构
     */
    GET_TABLE_STRUCTURE("GET_TABLE_STRUCTURE", "获取表结构", 20),

    /**
     * 分析用户输入
     */
    ANALYZE_INPUT("ANALYZE_INPUT", "分析用户输入", 35),

    /**
     * 处理对话
     */
    PROCESS_CHAT("PROCESS_CHAT", "处理对话", 40),

    /**
     * 生成SQL 查询
     */
    QUERY_SQL("QUERY_SQL", "生成查询SQL", 45),

    /**
     * 生成SQL 修改
     */
    UPDATE_SQL("UPDATE_SQL", "生成修改SQL", 45),

    /**
     * 执行SQL 查询
     */
    EXECUTE_QUERY_SQL("EXECUTE_QUERY_SQL", "执行查询SQL", 60),

    /**
     * 生执行SQL 修改
     */
    EXECUTE_UPDATE_SQL("EXECUTE_UPDATE_SQL", "执行修改SQL", 60),

    /**
     * 生成图表
     */
    CREATE_CHART("CREATE_CHART", "生成图表", 80),

    /**
     * 查询修改后数据阶段
     */
    QUERY_UPDATE_DATA("QUERY_UPDATE_DATA", "查询修改后的数据", 65),

    /**
     * 生成excel文件阶段
     */
    CREATE_EXCEL("CREATE_EXCEL", "生成EXCEL文件", 80),

    /**
     * AI响应阶段
     */
    AI_RESPONSE("AI_RESPONSE", "AI响应阶段", 80),

    /**
     * 完成阶段
     */
    COMPLETE("COMPLETE", "完成", 100),

    /**
     * 错误阶段
     */
    ERROR("ERROR", "错误", 0);




    // code、描述、百分比

    private final  String code;

    public String getDescription() {
        return description;
    }

    public int getProgress() {
        return progress;
    }

    public String getCode() {
        return code;
    }

    private final String description;

    private final int progress;

    ProcessStage(String code, String description, int progress) {
        this.code = code;
        this.description = description;
        this.progress = progress;
    }

}
