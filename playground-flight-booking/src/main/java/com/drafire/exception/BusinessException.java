package com.drafire.exception;

/**
 * 业务异常，用于可预期的业务逻辑错误。
 * 由 GlobalExceptionHandler 统一拦截，返回友好提示。
 */
public class BusinessException extends RuntimeException {

    private final String errorCode;

    public BusinessException(String message) {
        this("BUSINESS_ERROR", message);
    }

    public BusinessException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}