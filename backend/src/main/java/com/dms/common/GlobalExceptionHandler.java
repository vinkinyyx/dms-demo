package com.dms.common;

import com.dms.common.util.TenantContext;
import com.dms.operationlog.entity.OpLogEntry;
import com.dms.operationlog.service.OperationLogRecordService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
        log.warn("Business exception uri={} code={} msg={}", request.getRequestURI(), ex.getErrorCode().getCode(), ex.getMessage());
        recordException(request, ex, httpStatusForCode(ex.getErrorCode().getCode()).value(), "BUSINESS-EXCEPTION");
        return ResponseEntity.status(httpStatusForCode(ex.getErrorCode().getCode()))
                .body(ApiResponse.fail(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        log.warn("Validation failed: {}", msg);
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

    @ExceptionHandler({ConstraintViolationException.class, HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class, MissingPathVariableException.class,
            MethodArgumentTypeMismatchException.class, TypeMismatchException.class,
            IllegalArgumentException.class, MultipartException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception ex, HttpServletRequest request) {
        String msg = buildBadRequestMessage(ex);
        log.warn("Bad request uri={}: {}", request.getRequestURI(), msg);
        recordException(request, ex, 400, "PARAM-INVALID");
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(ErrorCode.PARAM_INVALID, msg));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuth(AuthenticationException ex, HttpServletRequest request) {
        log.warn("Authentication failed: {}", ex.getMessage());
        recordException(request, ex, 401, "AUTH-EXCEPTION");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.fail(ErrorCode.UNAUTHORIZED, ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied: {}", ex.getMessage());
        recordException(request, ex, 403, "ACCESS-DENIED");
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.fail(ErrorCode.FORBIDDEN, ex.getMessage()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        log.warn("Method not allowed uri={}: {}", request.getRequestURI(), ex.getMessage());
        recordException(request, ex, 405, "METHOD-NOT-ALLOWED");
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.fail(40500, "请求方法不支持: " + request.getMethod()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        log.warn("Resource not found uri={}: {}", request.getRequestURI(), ex.getMessage());
        recordException(request, ex, 404, "NOT-FOUND");
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(40400, "接口不存在: " + request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception ex, HttpServletRequest request) {
        log.error("System exception uri={} : {}", request.getRequestURI(), ex.getMessage(), ex);
        recordException(request, ex, 500, "SYSTEM-EXCEPTION");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(ErrorCode.INTERNAL_ERROR, "系统内部错误"));
    }

    private HttpStatus httpStatusForCode(Integer code) {
        if (code == null) return HttpStatus.BAD_REQUEST;
        int value = code;
        if (value == 40001 || value == 40002 || value == 40003 || value == 40004 || value == 40006) {
            return HttpStatus.BAD_REQUEST;
        }
        if (value == 40101 || value == 40102 || value == 40103 || value == 40104) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (value == 40301 || value == 40302 || value == 40303) {
            return HttpStatus.FORBIDDEN;
        }
        if (value == 40401 || value == 40402) {
            return HttpStatus.NOT_FOUND;
        }
        if (value == 40901 || value == 40902 || value == 40903 || value == 40904 || value == 40905 || value == 40906 || value == 40907 || value == 40908) {
            return HttpStatus.CONFLICT;
        }
        if (value == 42901) {
            return HttpStatus.TOO_MANY_REQUESTS;
        }
        if (value >= 50000 && value < 60000) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return HttpStatus.BAD_REQUEST;
    }

    private String buildBadRequestMessage(Exception ex) {
        if (ex instanceof MissingServletRequestParameterException missing) {
            return "缺少参数: " + missing.getParameterName();
        }
        if (ex instanceof MissingPathVariableException missingPath) {
            return "缺少路径参数: " + missingPath.getVariableName();
        }
        if (ex instanceof MethodArgumentTypeMismatchException mismatch) {
            return "参数类型错误: " + mismatch.getName();
        }
        if (ex instanceof ConstraintViolationException constraint) {
            return constraint.getConstraintViolations().stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .collect(Collectors.joining("; "));
        }
        if (ex instanceof HttpMessageNotReadableException) {
            return "请求体格式错误或为空";
        }
        if (ex instanceof MultipartException) {
            return "文件上传请求无效或缺少文件";
        }
        String message = ex.getMessage();
        return message == null || message.isBlank() ? "请求参数错误" : message;
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
