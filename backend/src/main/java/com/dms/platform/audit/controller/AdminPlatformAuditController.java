/*
 * 平台后台审计日志查询接口。
 */
package com.dms.platform.audit.controller;

import com.dms.common.ApiResponse;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.platform.audit.dto.PlatformAuditLogDTO;
import com.dms.platform.audit.entity.PlatformAuditLog;
import com.dms.platform.audit.repository.PlatformAuditLogRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/admin/logs")
@RequiredArgsConstructor
public class AdminPlatformAuditController {

    private final PlatformAuditLogRepository repository;

    @GetMapping("/platform-audits")
    public ApiResponse<PageResult<PlatformAuditLogDTO>> list(@org.springdoc.core.annotations.ParameterObject PageQuery pageQuery,
                                                             @RequestParam(required = false) Long adminUserId,
                                                             @RequestParam(required = false) String action,
                                                             @RequestParam(required = false) String targetType,
                                                             @RequestParam(required = false) String targetId,
                                                             @RequestParam(required = false) Boolean success,
                                                             @RequestParam(required = false) OffsetDateTime startTime,
                                                             @RequestParam(required = false) OffsetDateTime endTime) {
        Specification<PlatformAuditLog> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (adminUserId != null) ps.add(cb.equal(root.get("adminUserId"), adminUserId));
            if (action != null && !action.isBlank()) ps.add(cb.equal(root.get("action"), action));
            if (targetType != null && !targetType.isBlank()) ps.add(cb.equal(root.get("targetType"), targetType));
            if (targetId != null && !targetId.isBlank()) ps.add(cb.equal(root.get("targetId"), targetId));
            if (success != null) ps.add(cb.equal(root.get("success"), success));
            if (startTime != null) ps.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startTime));
            if (endTime != null) ps.add(cb.lessThanOrEqualTo(root.get("createdAt"), endTime));
            return cb.and(ps.toArray(new Predicate[0]));
        };
        Page<PlatformAuditLog> page = repository.findAll(spec, pageQuery.toPageable());
        return ApiResponse.ok(PageResult.of(page.map(this::toDTO)));
    }

    private PlatformAuditLogDTO toDTO(PlatformAuditLog e) {
        return PlatformAuditLogDTO.builder()
                .id(e.getId())
                .adminUserId(e.getAdminUserId())
                .adminUsername(e.getAdminUsername())
                .action(e.getAction())
                .targetType(e.getTargetType())
                .targetId(e.getTargetId())
                .beforeJson(e.getBeforeJson())
                .afterJson(e.getAfterJson())
                .ip(e.getIp())
                .userAgent(e.getUserAgent())
                .success(e.getSuccess())
                .errorMessage(e.getErrorMessage())
                .createdAt(e.getCreatedAt())
                .build();
    }
}