package com.example.mystore.common.exception;

import com.example.mystore.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.servlet.http.HttpServletRequest;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.warn("业务异常: {} at {}", e.getMessage(), request.getRequestURI());
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .findFirst()
                .orElse("参数验证失败");
        return Result.error(400, message);
    }

    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        String message = e.getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .findFirst()
                .orElse("参数绑定失败");
        return Result.error(400, message);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        String uri = request.getRequestURI();
        String paramName = e.getName();
        String requiredType = e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "unknown";
        log.warn("参数类型不匹配 at {} | 参数: {}={} | 期望类型: {}", uri, paramName, e.getValue(), requiredType);
        return Result.error(400, "参数格式错误: " + paramName);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicateKey(DuplicateKeyException e, HttpServletRequest request) {
        log.warn("数据唯一键冲突 at {}", request.getRequestURI());
        return Result.error("数据重复，请稍后刷新重试");
    }

    @ExceptionHandler(RuntimeException.class)
    public Result<Void> handleRuntimeException(RuntimeException e, HttpServletRequest request) {
        String uri = request.getRequestURI();
        log.warn("业务异常 at {} | 类型: {} | 消息: {}", uri, e.getClass().getSimpleName(), e.getMessage());
        return Result.error("系统繁忙，请稍后重试");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e, HttpServletRequest request) {
        String uri = request.getRequestURI();
        log.error("系统异常 at " + uri, e);
        return Result.error("系统繁忙，请稍后重试");
    }
}
