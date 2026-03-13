package com.docai.common.aspect;

import com.docai.common.annotation.LogOperation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

/**
 * 操作日志切面    围绕的是LogOperation这个自定义注解
 */
@Aspect
@Component
@Slf4j
public class LogOperationAspect {

    /**
     * 用于序列化请求/响应的Jackson工具
     */
    private final ObjectMapper objectMapper;

    public LogOperationAspect(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }

    /**
     * 环绕通知
     */
    @Around("@annotation(com.docai.common.annotation.LogOperation)")
    public Object logOperation(ProceedingJoinPoint joinPoint) {
        // 1. 访问的是哪个方法或者接口
        Long startTime = System.currentTimeMillis();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method =  signature.getMethod();
        LogOperation logOperation = method.getAnnotation(LogOperation.class);

        String methodName =  method.getName();
        String operation = logOperation.value().isEmpty() ?methodName : logOperation.value();

        // 2. 获取请求信息
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes != null ? attributes.getRequest() : null;

        // 3. 记录请求参数
        if (logOperation.logRequest()) {
            logRequest(operation, joinPoint, request);
        }


        // 4. 记录响应参数
        try {
            Object result = joinPoint.proceed();
            if (logOperation.logResponse()) {
                logResponse(operation, result, startTime, logOperation.logExecutionTime());
            }
            // 5. 响应的结果需要返回去
            return result;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }

    }


    /**
     * 记录请求参数
     * @param operation
     * @param joinPoint
     * @param request
     */
    private void logRequest(String operation, ProceedingJoinPoint joinPoint, HttpServletRequest request) {
        StringBuilder logBuilder = new StringBuilder();
        // 1. 加上了请求描述
        logBuilder.append("[").append(operation).append("] 请求参数：");

        // 2. 加上访问路由和者访问方法
        if (request != null) {
            logBuilder.append("methond=").append(request.getMethod())
                    .append(", url=").append(request.getRequestURI());

            // 3. 请求参数加上
            String queryString = request.getQueryString();
            if (queryString != null && !queryString.isEmpty()) {
                logBuilder.append(", query=").append(maskSensitiveData(queryString));
            }

            String authorization = request.getHeader("Authorization");
            if (authorization != null) {
                logBuilder.append(", authorization=").append(maskToken(authorization));
            }

        }

        // 5 记录方法参数
        Object[] args = joinPoint.getArgs();
        if (args != null && args.length > 0) {
            logBuilder.append(", args=[");
            // 遍历参数数组
            for (int i = 0; i <args.length; i++) {
                if (i>0) logBuilder.append(", ");
                Object arg = args[i];
                if (arg == null) {
                    logBuilder.append("null");
                } else if (isFile(arg)) {
                    // 处理文件
                    logBuilder.append("name=").append(getFileName(arg));
                } else if (isHttpServletResponse(arg)) {
                    logBuilder.append("下载内容，无法打印");
                } else {
                    try {
                        String argStr = maskSensitiveData(objectMapper.writeValueAsString(arg));
                        logBuilder.append(argStr);
                    } catch (JsonProcessingException e) {
                        logBuilder.append("获取参数异常");
                    }
                }
                logBuilder.append("]");
            }
            log.info(logBuilder.toString());
        }

    }

    /**
     * 记录响应参数的日志
     * @param operation 操作描述
     * @param result    执行结果
     * @param startTime 起始时间戳
     * @param logTime 是否记录耗时
     */
    private void logResponse(String operation, Object result, long startTime, boolean logTime) {
        StringBuilder logBuilder = new StringBuilder();
        logBuilder.append("[").append(operation).append("] 响应结果：");

        if (result == null) {
            logBuilder.append("null");
        } else {
            // 序列化响应结果
            try {
                String resultString = maskSensitiveData(objectMapper.writeValueAsString(result));
                logBuilder.append(resultString);
            } catch (JsonProcessingException e) {
                logBuilder.append("响应有异常");
            }
        }

        // 加上访问耗时
        if (logTime) {
            logBuilder.append("， 访问耗时=").append(System.currentTimeMillis() - startTime).append("ms");
        }
        log.info(logBuilder.toString());
    }


    /**
     * 关键字段脱敏操作
     * @param data 原始字符串
     * @return 脱敏后的字符串
     */
    private String maskSensitiveData(String data) {
        // 脱敏密码字段
        data = data.replaceAll("\"password\"\\s*:\\s*\"[^\"]*\"", "\"password\":\"***\"");
        data = data.replaceAll("\"passwordHash\"\\s*:\\s*\"[^\"]*\"", "\"passwordHash\":\"***\"");

        // 脱敏token字段
        data = data.replaceAll("\"token\"\\s*:\\s*\"[^\"]*\"", "\"token\":\"***\"");
        data = data.replaceAll("\"authorization\"\\s*:\\s*\"[^\"]*\"", "\"authorization\":\"***\"");

        // 脱敏邮箱
        data = data.replaceAll("\"email\"\\s*:\\s*\"([^\"]*@[^\"]*)\"", "\"email\":\"***\"");

        return data;
    }

    /**
     * 令牌脱敏
     * @param token 原始令牌
     * @return 脱敏后的Token
     */
    private String maskToken(String token) {
        return "****";
    }

    /**
     * 根据反射判断对象是否是文件
     * @param object 文件对象
     * @return 是或者否
     */
    private boolean isFile(Object object) {
        return object != null && object.getClass().getName().contains("MultipartFile");
    }

    /**
     * 根据反射靠对象来复原文件名
     * @param object 文件对象
     * @return 文件名
     */
    private String getFileName(Object object) {
        try {
            Method method =  object.getClass().getMethod("getOriginalFilename");
            return (String) method.invoke(object);
        } catch (Exception e) {
            return "文件未知!";
        }
    }

    /**
     * 根据反射来判断是否需要打印对象参数
     * @param object 入参
     * @return 是或者否
     */
    private boolean isHttpServletResponse(Object object) {
        return object != null && object.getClass().getName().contains("HttpServletResponse");
    }
}
