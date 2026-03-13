package com.docai.common.config;

import com.docai.common.util.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 全局异常处理器
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {


    /**
     * 请求体不可读异常
     * @param e 异常内容
     * @return  客户端400报错
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleJsonException(HttpMessageNotReadableException e) {
        return Result.badRequest("请求格式错误");
    }

    /**
     * 请求参数缺失
     * @param e 异常内容
     * @return  客户端400报错
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgsNotValid(MethodArgumentNotValidException e) {
        return Result.badRequest("请求参数缺失");
    }

    /**
     * 上传文件超大异常
     * @param e 异常内容
     * @return  客户端400报错
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> illegalArgumentException(IllegalArgumentException e) {
        return Result.badRequest(e.getMessage());
    }


    /**
     * 上传文件超大异常
     * @param e 异常内容
     * @return  客户端400报错
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> uploadSizeException(MaxUploadSizeExceededException e) {
        log.warn("文件有点大：{}", e.getMessage());
        return Result.badRequest("文件大小超过限制");
    }

    /**
     * Java堆内存溢出
     * @param e 错误
     * @return 服务端500报错
     */
    @ExceptionHandler(OutOfMemoryError.class)
    public Result<Void> oomException(OutOfMemoryError e) {
        log.error("Java堆内存溢出{}", e.getMessage());
        return Result.serverError("系统内存不足");
    }

    /**
     * 兜底异常
     * @param e 执行异常
     * @return 服务端500报错
     */
    @ExceptionHandler(RuntimeException.class)
    public Result<Void> handleRuntimeException(RuntimeException e) {
        log.error("业务运行异常:{}", e.getMessage());
        return Result.serverError(e.getMessage());
    }
}
