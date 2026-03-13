package com.docai.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志的注解   非侵入式打印日志
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LogOperation {

    /**
     * 操作描述 用于标识业务的含义   接口名称
     */
    String value() default "";

    /**
     * 是否需要记录请求参数
     */
    boolean logRequest() default true;

    /**
     * 是否需要记录响应参数
     */
    boolean logResponse() default true;

    /**
     * 是否记录接口的调用耗时
     */
    boolean logExecutionTime() default true;

}
