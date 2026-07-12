package com.drafire.exception;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.reactive.resource.NoResourceFoundException;

import java.util.Map;

/**
 * 全局异常处理器。
 * 统一拦截所有 Controller 抛出的异常，返回结构化的错误响应，
 * 避免将技术细节暴露给前端。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ──────────────── 业务异常 ────────────────

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessException(BusinessException e) {
        logger.warn("业务异常: code={}, message={}", e.getErrorCode(), e.getMessage());
        return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage(),
                "code", e.getErrorCode()
        ));
    }

    // ──────────────── JWT 操作令牌异常 ────────────────

    @ExceptionHandler(ExpiredJwtException.class)
    public ResponseEntity<Map<String, Object>> handleExpiredJwt(ExpiredJwtException e) {
        logger.warn("操作令牌已过期");
        return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "操作链接已过期（有效期 10 分钟），请重新发起操作"
        ));
    }

    @ExceptionHandler(SignatureException.class)
    public ResponseEntity<Map<String, Object>> handleSignatureException(SignatureException e) {
        logger.warn("操作令牌签名无效");
        return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "操作链接签名无效，请使用有效的确认链接"
        ));
    }

    @ExceptionHandler(MalformedJwtException.class)
    public ResponseEntity<Map<String, Object>> handleMalformedJwt(MalformedJwtException e) {
        logger.warn("操作令牌格式无效");
        return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "操作链接格式无效"
        ));
    }

    // ──────────────── 参数校验异常 ────────────────

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        logger.warn("参数异常: {}", e.getMessage());
        return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
        ));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(WebExchangeBindException e) {
        String message = e.getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数校验失败");
        logger.warn("参数校验失败: {}", message);
        return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", message
        ));
    }

    // ──────────────── 静态资源缺失 ────────────────

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResourceFound(NoResourceFoundException e) {
        logger.debug("静态资源未找到: {}", e.getMessage());
        return ResponseEntity.notFound().build();
    }

    // ──────────────── 兜底 ────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception e) {
        logger.error("未预期的异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "error", "服务器内部错误，请稍后重试"
        ));
    }
}