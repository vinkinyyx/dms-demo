/*
 * 租户业务服务，提供列表、详情、创建与基础信息更新能力。
 */
package com.dms.tenant.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.tenant.dto.TenantCreateRequest;
import com.dms.tenant.dto.TenantDTO;
import com.dms.tenant.dto.TenantUpdateRequest;
import com.dms.tenant.entity.Tenant;
import com.dms.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.UUID;

/**
 * 租户业务服务：负责租户 CRUD 及基础校验。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;

    @Transactional(readOnly = true)
    public PageResult<TenantDTO> list(PageQuery pageQuery, String keyword) {
        Page<Tenant> page = tenantRepository.findAll(pageQuery.toPageable());
        return PageResult.of(page.map(this::toDTO));
    }

    @Transactional(readOnly = true)
    public TenantDTO get(UUID id) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "租户不存在"));
        return toDTO(tenant);
    }

    @Transactional
    public TenantDTO create(TenantCreateRequest request) {
        if (tenantRepository.existsByCode(request.getCode())) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "租户编码已存在");
        }
        Tenant tenant = Tenant.builder()
                .id(UUID.randomUUID())
                .code(request.getCode())
                .name(request.getName())
                .industry(request.getIndustry())
                .timezone(request.getTimezone() == null ? "Asia/Shanghai" : request.getTimezone())
                .logoUrl(request.getLogoUrl())
                .status("active")
                .modulesEnabled(request.getModulesEnabled() == null ? new HashMap<>() : request.getModulesEnabled())
                .quota(request.getQuota() == null ? new HashMap<>() : request.getQuota())
                .attrs(request.getAttrs() == null ? new HashMap<>() : request.getAttrs())
                .contactName(request.getContactName())
                .contactEmail(request.getContactEmail())
                .contactPhone(request.getContactPhone())
                .effectiveFrom(request.getEffectiveFrom())
                .effectiveTo(request.getEffectiveTo())
                .updatedAt(OffsetDateTime.now())
                .build();
        tenant.ensureJsonFields();
        Tenant saved = tenantRepository.save(tenant);
        log.info("创建租户成功: code={}, id={}", saved.getCode(), saved.getId());
        return toDTO(saved);
    }

    @Transactional
    public TenantDTO updateBasic(UUID id, TenantUpdateRequest request) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "租户不存在"));
        if (request.getName() != null) tenant.setName(request.getName());
        if (request.getIndustry() != null) tenant.setIndustry(request.getIndustry());
        if (request.getTimezone() != null) tenant.setTimezone(request.getTimezone());
        if (request.getLogoUrl() != null) tenant.setLogoUrl(request.getLogoUrl());
        if (request.getStatus() != null) tenant.setStatus(request.getStatus());
        if (request.getModulesEnabled() != null) tenant.setModulesEnabled(request.getModulesEnabled());
        if (request.getQuota() != null) tenant.setQuota(request.getQuota());
        if (request.getAttrs() != null) tenant.setAttrs(request.getAttrs());
        if (request.getContactName() != null) tenant.setContactName(request.getContactName());
        if (request.getContactEmail() != null) tenant.setContactEmail(request.getContactEmail());
        if (request.getContactPhone() != null) tenant.setContactPhone(request.getContactPhone());
        if (request.getEffectiveFrom() != null) tenant.setEffectiveFrom(request.getEffectiveFrom());
        if (request.getEffectiveTo() != null) tenant.setEffectiveTo(request.getEffectiveTo());
        tenant.setUpdatedAt(OffsetDateTime.now());
        tenant.ensureJsonFields();
        return toDTO(tenantRepository.save(tenant));
    }

    private TenantDTO toDTO(Tenant t) {
        return TenantDTO.builder()
                .id(t.getId())
                .code(t.getCode())
                .name(t.getName())
                .industry(t.getIndustry())
                .timezone(t.getTimezone())
                .logoUrl(t.getLogoUrl())
                .status(t.getStatus())
                .modulesEnabled(t.getModulesEnabled())
                .quota(t.getQuota())
                .attrs(t.getAttrs())
                .contactName(t.getContactName())
                .contactEmail(t.getContactEmail())
                .contactPhone(t.getContactPhone())
                .effectiveFrom(t.getEffectiveFrom())
                .effectiveTo(t.getEffectiveTo())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build();
    }
}
