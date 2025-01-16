package com.wjp.wcloudatlasbackend.exception;

import com.wjp.wcloudatlasbackend.common.BaseResponse;
import com.wjp.wcloudatlasbackend.common.ResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 * @author wjp
 */
// 用于定义一个全局的异常处理类
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // 指定捕获自定义异常 BusinessException
    // 当 BusinessException 被抛出时，这个方法会被调用。
    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException e) {
        log.error("BusinessException", e);
        return ResultUtils.error(e.getCode(), e.getMessage());
    }

    // 捕获所有运行时异常（RuntimeException 及其子类）。
    // 它是一个比较通用的异常处理，适用于未被特定捕获的运行时错误。
    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> runtimeExceptionHandler(RuntimeException e) {
        log.error("RuntimeException", e);
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
    }

}
