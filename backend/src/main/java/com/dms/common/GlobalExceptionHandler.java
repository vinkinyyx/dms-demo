/*
 * 全局异常处理器，捕获业务异常、参数校验异常与未知异常并转换为统一响应结构。
 */
package com.dms.common;

import com.dms.common.util.TenantContext;
import com.dms.operationlog.entity.OpLogEntry;
import com.dms.operationlog.service.OperationLogRecordService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.OffsetDateTime;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @Autowired
    private OperationLogRecordService opLogRecordService;

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        log.warn("业务异常 uri={} code={} msg={}", request.getRequestURI(), ex.getErrorCode().getCode(), ex.getMessage());
        recordException(request, ex, ex.getErrorCode().getCode(), "BUSINESS-EXCEPTION");
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.fail(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", msg);
        recordException(request, ex, 400, "PARAM-INVALID");
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(ErrorCode.PARAM_INVALID, msg));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBind(BindException ex, HttpServletRequest request) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        recordException(request, ex, 400, "BIND-EXCEPTION");
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(ErrorCode.PARAM_INVALID, msg));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest request) {
        recordException(request, ex, 400, "PARAM-MISSING");
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(ErrorCode.PARAM_MISSING, "缺少参数: " + ex.getParameterName()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuth(AuthenticationException ex, HttpServletRequest request) {
        log.warn("认证失败: {}", ex.getMessage());
        recordException(request, ex, 401, "AUTH-EXCEPTION");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.fail(ErrorCode.UNAUTHORIZED, ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("权限不足: {}", ex.getMessage());
        recordException(request, ex, 403, "ACCESS-DENIED");
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.fail(ErrorCode.FORBIDDEN, ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception ex, HttpServletRequest request) {
        // 路由不存在的请求不应该升级成 500 系统异常，应当返回 404
        if (ex instanceof org.springframework.web.servlet.resource.NoResourceFoundException) {
            log.warn("接口不存在 uri={} : {}", request.getRequestURI(), ex.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.fail(40400, "接口不存在: " + request.getRequestURI()));
        }
        log.error("系统异常 uri={} : {}", request.getRequestURI(), ex.getMessage(), ex);
        recordException(request, ex, 500, "SYSTEM-EXCEPTION");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(ErrorCode.INTERNAL_ERROR, "系统内部错误"));
    }

    private void recordException(HttpServletRequest request, Throwable ex, int status, String remark) {
        try {
            OpLogEntry entry = new OpLogEntry();
            entry.setLayer("EXCEPTION");
            entry.setHttpMethod(request.getMethod());
            entry.setPath(request.getRequestURI());
            entry.setStatus(status);
            entry.setIp(request.getRemoteAddr());
            entry.setUserAgent(request.getHeader("User-Agent"));
            entry.setUserId(TenantContext.getUserId());
            entry.setUsername(TenantContext.getUsername());
            entry.setTenantId(TenantContext.getTenantId());
            entry.setRemark(remark);
            entry.setStack(stackToString(ex));
            entry.setCreatedAt(OffsetDateTime.now());
            opLogRecordService.record(entry);
        } catch (Exception e) {
            log.warn("record exception op_log failed: {}", e.getMessage());
        }
    }

    private String stackToString(Throwable ex) {
        if (ex == null) return null;
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        String s = sw.toString();
        return s.length() > 4096 ? s.substring(0, 4096) + "..." : s;
    }

    private String formatFieldError(FieldError err) {
        return err.getField() + ": " + err.getDefaultMessage();
    }
}
