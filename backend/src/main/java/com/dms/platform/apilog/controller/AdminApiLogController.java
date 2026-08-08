/*
 * 平台后台接口日志查询与报文下载。
 */
package com.dms.platform.apilog.controller;

import com.dms.common.ApiResponse;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.platform.apilog.dto.ApiHttpLogDTO;
import com.dms.platform.apilog.entity.ApiHttpLog;
import com.dms.platform.apilog.repository.ApiHttpLogRepository;
import com.dms.platform.apilog.service.ApiHttpLogService;
import com.dms.platform.audit.service.PlatformAuditService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/logs")
@RequiredArgsConstructor
public class AdminApiLogController {

    private final ApiHttpLogRepository repository;
    private final ApiHttpLogService service;
    private final PlatformAuditService auditService;

    @GetMapping("/api")
    public ApiResponse<PageResult<ApiHttpLogDTO>> list(@org.springdoc.core.annotations.ParameterObject PageQuery pageQuery,
                                                       @RequestParam(required = false) UUID tenantId,
                                                       @RequestParam(required = false) UUID ownerManufacturerId,
                                                       @RequestParam(required = false) Long userId,
                                                       @RequestParam(required = false) String path,
                                                       @RequestParam(required = false) String method,
                                                       @RequestParam(required = false) Integer statusCode,
                                                       @RequestParam(required = false) Boolean success,
                                                       @RequestParam(required = false) String requestId,
                                                       @RequestParam(required = false) String traceId,
                                                       @RequestParam(required = false) Boolean slow,
                                                       @RequestParam(required = false) OffsetDateTime startTime,
                                                       @RequestParam(required = false) OffsetDateTime endTime) {
        Specification<ApiHttpLog> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (tenantId != null) ps.add(cb.equal(root.get("tenantId"), tenantId));
            if (ownerManufacturerId != null) ps.add(cb.equal(root.get("ownerManufacturerId"), ownerManufacturerId));
            if (userId != null) ps.add(cb.equal(root.get("userId"), userId));
            if (path != null && !path.isBlank()) ps.add(cb.like(root.get("path"), "%" + path + "%"));
            if (method != null && !method.isBlank()) ps.add(cb.equal(root.get("httpMethod"), method));
            if (statusCode != null) ps.add(cb.equal(root.get("statusCode"), statusCode));
            if (success != null) ps.add(cb.equal(root.get("success"), success));
            if (requestId != null && !requestId.isBlank()) ps.add(cb.equal(root.get("requestId"), requestId));
            if (traceId != null && !traceId.isBlank()) ps.add(cb.equal(root.get("traceId"), traceId));
            if (slow != null) ps.add(cb.equal(root.get("slow"), slow));
            if (startTime != null) ps.add(cb.greaterThanOrEqualTo(root.get("startedAt"), startTime));
            if (endTime != null) ps.add(cb.lessThanOrEqualTo(root.get("startedAt"), endTime));
            return cb.and(ps.toArray(new Predicate[0]));
        };
        Page<ApiHttpLog> page = repository.findAll(spec, pageQuery.toPageable());
        return ApiResponse.ok(PageResult.of(page.map(this::toDTO)));
    }

    @GetMapping("/api/{id}/request-file")
    public ResponseEntity<byte[]> requestFile(@PathVariable Long id) {
        byte[] data = service.downloadRequest(id);
        auditService.log("API_LOG_DOWNLOAD_REQUEST", "api_http_log", String.valueOf(id), Map.of("kind", "request"));
        return fileResponse(data, "api-log-" + id + "-request.json");
    }

    @GetMapping("/api/{id}/response-file")
    public ResponseEntity<byte[]> responseFile(@PathVariable Long id) {
        byte[] data = service.downloadResponse(id);
        auditService.log("API_LOG_DOWNLOAD_RESPONSE", "api_http_log", String.valueOf(id), Map.of("kind", "response"));
        return fileResponse(data, "api-log-" + id + "-response.json");
    }

    private ResponseEntity<byte[]> fileResponse(byte[] data, String filename) {
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.APPLICATION_JSON)
                .body(data == null ? new byte[0] : data);
    }

    private ApiHttpLogDTO toDTO(ApiHttpLog e) {
        return ApiHttpLogDTO.builder()
                .id(e.getId())
                .requestId(e.getRequestId())
                .traceId(e.getTraceId())
                .tenantId(e.getTenantId())
                .tenantType(e.getTenantType())
                .ownerManufacturerId(e.getOwnerManufacturerId())
                .userId(e.getUserId())
                .username(e.getUsername())
                .authSource(e.getAuthSource())
                .httpMethod(e.getHttpMethod())
                .path(e.getPath())
                .queryString(e.getQueryString())
                .statusCode(e.getStatusCode())
                .bizCode(e.getBizCode())
                .success(e.getSuccess())
                .slow(e.getSlow())
                .spentMs(e.getSpentMs())
                .clientIp(e.getClientIp())
                .errorMessage(e.getErrorMessage())
                .hasRequestFile(e.getRequestObjectKey() != null)
                .hasResponseFile(e.getResponseObjectKey() != null)
                .requestSize(e.getRequestSize())
                .responseSize(e.getResponseSize())
                .startedAt(e.getStartedAt())
                .finishedAt(e.getFinishedAt())
                .build();
    }
}