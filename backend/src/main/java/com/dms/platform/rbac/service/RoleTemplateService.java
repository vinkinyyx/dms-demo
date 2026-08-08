/*
 * 平台默认角色模板管理：列表、新增、更新、权限点维护。
 */
package com.dms.platform.rbac.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.platform.audit.service.PlatformAuditService;
import com.dms.platform.rbac.dto.RoleTemplateDTO;
import com.dms.platform.rbac.dto.RoleTemplatePermissionsRequest;
import com.dms.platform.rbac.dto.RoleTemplateSaveRequest;
import com.dms.platform.rbac.entity.RoleTemplate;
import com.dms.platform.rbac.repository.RoleTemplateRepository;
import com.dms.platform.rbac.repository.RoleTemplateResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RoleTemplateService {

    private final RoleTemplateRepository templateRepository;
    private final RoleTemplateResourceRepository resourceRepository;
    private final JdbcTemplate jdbcTemplate;
    private final PlatformAuditService auditService;

    @Transactional(readOnly = true)
    public List<RoleTemplateDTO> list(String tenantType) {
        List<RoleTemplate> templates = (tenantType == null || tenantType.isBlank())
                ? templateRepository.findByStatus("active")
                : templateRepository.findByTenantTypeAndStatus(tenantType, "active");
        return templates.stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public RoleTemplateDTO get(Long id) {
        return toDTO(load(id));
    }

    @Transactional
    public RoleTemplateDTO create(RoleTemplateSaveRequest request) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM role_templates WHERE code = ?", Long.class, request.getCode());
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "模板编码已存在");
        }
        OffsetDateTime now = OffsetDateTime.now();
        Long id = jdbcTemplate.queryForObject(
                "INSERT INTO role_templates (code, name, tenant_type, data_scope, description, status, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, 'active', ?, ?) RETURNING id",
                Long.class, request.getCode(), request.getName(), request.getTenantType(),
                request.getDataScope(), request.getDescription(),
                Timestamp.from(now.toInstant()), Timestamp.from(now.toInstant()));
        auditService.log("ROLE_TEMPLATE_CREATE", "role_template", String.valueOf(id),
                Map.of("code", request.getCode(), "name", request.getName()));
        return get(id);
    }

    @Transactional
    public RoleTemplateDTO update(Long id, RoleTemplateSaveRequest request) {
        load(id);
        jdbcTemplate.update(
                "UPDATE role_templates SET name = ?, tenant_type = ?, data_scope = ?, description = ?, updated_at = ? WHERE id = ?",
                request.getName(), request.getTenantType(), request.getDataScope(), request.getDescription(),
                Timestamp.from(OffsetDateTime.now().toInstant()), id);
        auditService.log("ROLE_TEMPLATE_UPDATE", "role_template", String.valueOf(id),
                Map.of("name", request.getName()));
        return get(id);
    }

    @Transactional(readOnly = true)
    public List<String> getPermissions(Long id) {
        load(id);
        return jdbcTemplate.queryForList(
                "SELECT resource_code FROM role_template_resources WHERE template_id = ? ORDER BY resource_code",
                String.class, id);
    }

    @Transactional
    public void setPermissions(Long id, RoleTemplatePermissionsRequest request) {
        load(id);
        jdbcTemplate.update("DELETE FROM role_template_resources WHERE template_id = ?", id);
        Set<String> codes = new LinkedHashSet<>();
        if (request.getResourceCodes() != null) {
            for (String code : request.getResourceCodes()) {
                if (code != null && !code.isBlank()) {
                    codes.add(code.trim());
                }
            }
        }
        for (String code : codes) {
            jdbcTemplate.update(
                    "INSERT INTO role_template_resources (template_id, resource_code) VALUES (?, ?)",
                    id, code);
        }
        auditService.log("ROLE_TEMPLATE_SET_PERMISSIONS", "role_template", String.valueOf(id),
                Map.of("count", codes.size()));
    }

    private RoleTemplate load(Long id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "角色模板不存在"));
    }

    private RoleTemplateDTO toDTO(RoleTemplate t) {
        List<String> codes = new ArrayList<>(jdbcTemplate.queryForList(
                "SELECT resource_code FROM role_template_resources WHERE template_id = ? ORDER BY resource_code",
                String.class, t.getId()));
        return RoleTemplateDTO.builder()
                .id(t.getId())
                .code(t.getCode())
                .name(t.getName())
                .tenantType(t.getTenantType())
                .dataScope(t.getDataScope())
                .description(t.getDescription())
                .status(t.getStatus())
                .resourceCodes(codes)
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build();
    }
}