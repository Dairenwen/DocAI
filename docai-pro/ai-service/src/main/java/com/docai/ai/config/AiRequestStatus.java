package com.docai.ai.config;

/**
 * AI请求状态枚举
 */
public enum AiRequestStatus {

    PROCESSING(0, "处理中"),

    SUCCESS(1, "成功"),

    FAILED(2, "失败"),

    PARTIAL_SUCCESS(3, "部分成功")
    ;

    public Integer getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    // 状态码
    private final Integer code;

    // 状态描述
    private final String description;

    AiRequestStatus(Integer code, String description) {
        this.code = code;
        this.description = description;
    }


}
