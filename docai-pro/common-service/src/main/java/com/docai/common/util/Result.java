package com.docai.common.util;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通用响应封装类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    /**
     * 响应码
     */
    private Integer code;

    /**
     * 响应文案
     */
    private String message;

    /**
     * 响应数据
     */
    private T data;


    // 成功、客户端报错、服务端报错

    /**
     * 状态码枚举
     */
    public enum ResultCode {
        // 请求成功
        SUCCESS(200, "操作成功"),

        // 客户端报错
        BAD_REQUEST(400, "请求参数错误"),
        UNAUTHORIZED(401, "未授权"),
        FORBIDDEN(403, "禁止访问"),
        NOT_FOUND(404, "资源不存在"),

        // 服务端报错
        SERVER_ERROR(500, "服务报错"),
        GATEWAY_ERROR(502, "网关报错"),
        SERVER_UNAVAILABLE(503, "服务不可用"),


        ;

        ResultCode(Integer code, String message) {
            this.code = code;
            this.message = message;
        }

        public Integer getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }

        private final Integer code;

        private final String message;
    }

    /**
     * 自定义成功响应
     * @param message 响应文案
     * @param data 响应数据
     * @return  通用响应对象
     * @param <T>  泛型
     */
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), message, data);
    }

    /**
     * 自定义失败响应
     * @param resultCode   错误枚举
     * @param message   响应文案
     * @return 通用响应对象
     * @param <T> 泛型
     */
    public static <T> Result<T> error(ResultCode resultCode, String message) {
        return new Result<>(resultCode.getCode(), message, null);
    }

    /**
     * 请求参数报错
     * @param message 响应文案
     * @return 通用响应对象
     * @param <T> 泛型
     */
    public static <T> Result<T> badRequest(String message) {
        return error(ResultCode.BAD_REQUEST, message);
    }

    /**
     * 服务端报错
     * @param message 响应文案
     * @return 通用响应对象
     * @param <T> 泛型
     */
    public static <T> Result<T> serverError(String message) {
        return error(ResultCode.SERVER_ERROR, message);
    }
}
