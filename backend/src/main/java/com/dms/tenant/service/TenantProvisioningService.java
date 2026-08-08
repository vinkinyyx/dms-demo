/*
 * 骞冲彴鍚庡彴绉熸埛寮€閫氭湇鍔★細鍘傚/缁忛攢鍟嗙鎴峰垱寤恒€佸惎鍋溿€乨ealer 缁戝畾銆佺鎴风鐞嗗憳绠＄悊銆? * 寮€閫氳繃绋嬪湪鍗曚簨鍔″唴瀹屾垚锛氬缓绉熸埛 + 寤虹鎴风鐞嗗憳 + 鍐欑粦瀹氾紝骞跺啓骞冲彴瀹¤鏃ュ織銆? */
package com.dms.tenant.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.masterdata.entity.Dealer;
import com.dms.masterdata.repository.DealerRepository;
import com.dms.platform.audit.service.PlatformAuditService;
import com.dms.platform.rbac.service.TenantRoleProvisioner;
import com.dms.rbac.repository.UserRoleRepository;
import com.dms.rbac.entity.UserRole;
import com.dms.tenant.dto.admin.AdminTenantDTO;
import com.dms.tenant.dto.admin.DealerTenantCreateRequest;
import com.dms.tenant.dto.admin.ManufacturerTenantCreateRequest;
import com.dms.tenant.dto.admin.TenantAdminDTO;
import com.dms.tenant.entity.Tenant;
import com.dms.tenant.entity.TenantDealerBinding;
import com.dms.tenant.repository.TenantDealerBindingRepository;
import com.dms.tenant.repository.TenantRepository;
import com.dms.user.entity.User;
import com.dms.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantProvisioningService {

    public static final String TYPE_MANUFACTURER = "MANUFACTURER";
    public static final String TYPE_DEALER = "DEALER";
    public static final String DEPLOY_SHARED = "SHARED";
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_DISABLED = "disabled";
    public static final String TENANT_ADMIN_TYPE = "tenant_admin";

    private final TenantRepository tenantRepository;
    private final TenantDealerBindingRepository bindingRepository;
    private final DealerRepository dealerRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PlatformAuditService auditService;
    private final TenantRoleProvisioner roleProvisioner;
    private final UserRoleRepository userRoleRepository;

    // ==================== 鍘傚绉熸埛 ====================

    @Transactional(readOnly = true)
    public PageResult<AdminTenantDTO> listManufacturers(PageQuery pageQuery, String keyword) {
        Pageable pageable = pageQuery.toPageable();
        Page<Tenant> page = (keyword == null || keyword.isBlank())
                ? tenantRepository.findByTenantType(TYPE_MANUFACTURER, pageable)
                : tenantRepository.findAll((root, query, cb) -> cb.and(
                        cb.equal(root.get("tenantType"), TYPE_MANUFACTURER),
                        cb.or(
                                cb.like(root.get("code"), "%" + keyword + "%"),
                                cb.like(root.get("name"), "%" + keyword + "%"))),
                pageable);
        return PageResult.of(page.map(this::toDTO));
    }

    @Transactional
    public AdminTenantDTO createManufacturer(ManufacturerTenantCreateRequest request) {
        if (tenantRepository.existsByCode(request.getCode())) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "租户编码已存在");
        }
        OffsetDateTime now = OffsetDateTime.now();
        Tenant tenant = Tenant.builder()
                .id(UUID.randomUUID())
                .code(request.getCode())
                .name(request.getName())
                .industry("鍖荤枟鍣ㄦ")
                .timezone("Asia/Shanghai")
                .status(STATUS_ACTIVE)
                .tenantType(TYPE_MANUFACTURER)
                .deploymentMode(DEPLOY_SHARED)
                .modulesEnabled(new HashMap<>())
                .quota(new HashMap<>())
                .attrs(new HashMap<>())
                .contactName(request.getContactName())
                .contactPhone(request.getContactPhone())
                .contactEmail(request.getContactEmail())
                .enabledAt(now)
                .updatedAt(now)
                .build();
        tenant.ensureJsonFields();
        tenant = tenantRepository.save(tenant);

        User admin = createTenantAdmin(tenant, request.getAdminUsername(), request.getAdminPassword(),
                request.getAdminName(), null, now);
        Map<String, Long> roleIds = roleProvisioner.provision(tenant.getId(), TYPE_MANUFACTURER);
        bindAdminRole(admin.getId(), roleIds.get("MANUFACTURER_ADMIN"));

        log.info("骞冲彴鍒涘缓鍘傚绉熸埛: code={}, id={}", tenant.getCode(), tenant.getId());
        auditService.log("TENANT_CREATE_MANUFACTURER", "tenant", tenant.getId().toString(),
                Map.of("code", tenant.getCode(), "name", tenant.getName(), "tenantType", TYPE_MANUFACTURER));

        return toDTO(tenant);
    }

    // ==================== 缁忛攢鍟嗙鎴?====================

    @Transactional(readOnly = true)
    public PageResult<AdminTenantDTO> listDealers(PageQuery pageQuery, String keyword, UUID manufacturerTenantId) {
        Pageable pageable = pageQuery.toPageable();
        Page<Tenant> page;
        if (manufacturerTenantId != null) {
            page = tenantRepository.findByOwnerManufacturerId(manufacturerTenantId, pageable);
        } else if (keyword == null || keyword.isBlank()) {
            page = tenantRepository.findByTenantType(TYPE_DEALER, pageable);
        } else {
            page = tenantRepository.findAll((root, query, cb) -> cb.and(
                    cb.equal(root.get("tenantType"), TYPE_DEALER),
                    cb.or(
                            cb.like(root.get("code"), "%" + keyword + "%"),
                            cb.like(root.get("name"), "%" + keyword + "%"))),
            pageable);
        }
        return PageResult.of(page.map(this::toDTO));
    }

    @Transactional
    public AdminTenantDTO createDealer(DealerTenantCreateRequest request) {
        Tenant manufacturer = tenantRepository.findById(request.getManufacturerTenantId())
                .orElseThrow(() -> new BusinessException(ErrorCode.TENANT_NOT_FOUND, "所属厂商租户不存在"));
        if (!TYPE_MANUFACTURER.equals(manufacturer.getTenantType())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "所属租户不是厂商租户");
        }
        if (tenantRepository.existsByCode(request.getCode())) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "租户编码已存在");
        }

        Dealer dealer = dealerRepository.findById(request.getDealerId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "经销商不存在"));
        if (!manufacturer.getId().equals(dealer.getTenantId())) {
            throw new BusinessException(ErrorCode.INVALID_MANUFACTURER_SCOPE, "dealer 涓嶅睘浜庤鍘傚绉熸埛");
        }
        if (bindingRepository.existsByManufacturerTenantIdAndDealerIdAndStatus(
                manufacturer.getId(), dealer.getId(), STATUS_ACTIVE)) {
            throw new BusinessException(ErrorCode.DEALER_ALREADY_BOUND);
        }

        OffsetDateTime now = OffsetDateTime.now();
        Tenant tenant = Tenant.builder()
                .id(UUID.randomUUID())
                .code(request.getCode())
                .name(request.getName())
                .industry("鍖荤枟鍣ㄦ")
                .timezone("Asia/Shanghai")
                .status(STATUS_ACTIVE)
                .tenantType(TYPE_DEALER)
                .deploymentMode(DEPLOY_SHARED)
                .ownerManufacturerId(manufacturer.getId())
                .modulesEnabled(new HashMap<>())
                .quota(new HashMap<>())
                .attrs(new HashMap<>())
                .contactName(request.getContactName())
                .contactPhone(request.getContactPhone())
                .contactEmail(request.getContactEmail())
                .enabledAt(now)
                .updatedAt(now)
                .build();
        tenant.ensureJsonFields();
        tenant = tenantRepository.save(tenant);

        TenantDealerBinding binding = TenantDealerBinding.builder()
                .dealerTenantId(tenant.getId())
                .manufacturerTenantId(manufacturer.getId())
                .dealerId(dealer.getId())
                .status(STATUS_ACTIVE)
                .boundAt(now)
                .boundBy(currentAdminId())
                .remark(request.getRemark())
                .updatedAt(now)
                .build();
        bindingRepository.save(binding);

        User admin = createTenantAdmin(tenant, request.getAdminUsername(), request.getAdminPassword(),
                request.getAdminName(), dealer.getId(), now);
        Map<String, Long> roleIds = roleProvisioner.provision(tenant.getId(), TYPE_DEALER);
        bindAdminRole(admin.getId(), roleIds.get("DEALER_ADMIN"));

        log.info("骞冲彴鍒涘缓缁忛攢鍟嗙鎴? code={}, id={}, manufacturer={}, dealerId={}",
                tenant.getCode(), tenant.getId(), manufacturer.getId(), dealer.getId());
        auditService.log("TENANT_CREATE_DEALER", "tenant", tenant.getId().toString(),
                Map.of("code", tenant.getCode(), "name", tenant.getName(),
                        "tenantType", TYPE_DEALER,
                        "ownerManufacturerId", manufacturer.getId().toString(),
                        "dealerId", dealer.getId()));

        return toDTO(tenant);
    }

    @Transactional(readOnly = true)
    public AdminTenantDTO get(UUID id) {
        return toDTO(loadTenant(id));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getBinding(UUID id) {
        loadTenant(id);
        TenantDealerBinding binding = bindingRepository.findByDealerTenantId(id).orElse(null);
        Map<String, Object> result = new HashMap<>();
        if (binding != null) {
            result.put("dealerTenantId", binding.getDealerTenantId());
            result.put("manufacturerTenantId", binding.getManufacturerTenantId());
            result.put("dealerId", binding.getDealerId());
            result.put("status", binding.getStatus());
            result.put("boundAt", binding.getBoundAt());
        }
        return result;
    }

    // ==================== 鍚敤 / 鍋滅敤 ====================

    @Transactional
    public AdminTenantDTO enable(UUID id) {
        Tenant tenant = loadTenant(id);
        if (!STATUS_ACTIVE.equals(tenant.getStatus())) {
            tenant.setStatus(STATUS_ACTIVE);
            tenant.setEnabledAt(OffsetDateTime.now());
            tenant.setDisabledAt(null);
            tenant.setDisabledBy(null);
            tenant.setDisableReason(null);
            tenant.setUpdatedAt(OffsetDateTime.now());
            tenant = tenantRepository.save(tenant);
            auditService.log("TENANT_ENABLE", "tenant", id.toString(),
                    Map.of("status", STATUS_ACTIVE));
        }
        return toDTO(tenant);
    }

    @Transactional
    public AdminTenantDTO disable(UUID id, String reason) {
        Tenant tenant = loadTenant(id);
        tenant.setStatus(STATUS_DISABLED);
        tenant.setDisabledAt(OffsetDateTime.now());
        tenant.setDisabledBy(currentAdminId());
        tenant.setDisableReason(reason);
        tenant.setUpdatedAt(OffsetDateTime.now());
        tenant = tenantRepository.save(tenant);
        auditService.log("TENANT_DISABLE", "tenant", id.toString(),
                Map.of("status", STATUS_DISABLED, "reason", reason == null ? "" : reason));
        return toDTO(tenant);
    }

    // ==================== 绉熸埛绠＄悊鍛?====================

    @Transactional(readOnly = true)
    public PageResult<TenantAdminDTO> listTenantAdmins(PageQuery pageQuery, UUID tenantId, String keyword) {
        Pageable customPageable = pageQuery.toPageable().getSort().isSorted()
                ? pageQuery.toPageable()
                : org.springframework.data.domain.PageRequest.of(pageQuery.getPage() - 1, pageQuery.getSize(),
                    org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt", "id"));
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        Page<User> page = userRepository.findAll((root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
            predicates.add(cb.equal(root.get("userType"), TENANT_ADMIN_TYPE));
            if (tenantId != null) {
                predicates.add(cb.equal(root.get("tenantId"), tenantId));
            }
            if (normalizedKeyword != null) {
                String like = "%" + normalizedKeyword + "%";
                predicates.add(cb.or(
                        cb.like(root.get("username"), like),
                        cb.like(root.get("name"), like)));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        }, customPageable);
        List<UUID> tenantIds = page.getContent().stream().map(User::getTenantId).distinct().toList();
        Map<UUID, Tenant> tenants = tenantRepository.findAllById(tenantIds).stream()
                .collect(java.util.stream.Collectors.toMap(Tenant::getId, t -> t));
        return PageResult.of(page.map(user -> toTenantAdminDTO(user, tenants.get(user.getTenantId()))));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> tenantStats() {
        long total = tenantRepository.count();
        long manufacturers = tenantRepository.count((root, query, cb) ->
                cb.equal(root.get("tenantType"), TYPE_MANUFACTURER));
        long dealers = tenantRepository.count((root, query, cb) ->
                cb.equal(root.get("tenantType"), TYPE_DEALER));
        long active = tenantRepository.count((root, query, cb) ->
                cb.equal(root.get("status"), STATUS_ACTIVE));
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalTenants", total);
        stats.put("manufacturerTenants", manufacturers);
        stats.put("dealerTenants", dealers);
        stats.put("activeTenants", active);
        return stats;
    }

    @Transactional
    public User createTenantAdmin(UUID tenantId, String username, String password, String name, Long dealerId) {
        Tenant tenant = loadTenant(tenantId);
        if (userRepository.countByTenantIdAndUserTypeAndStatus(
                tenant.getId(), TENANT_ADMIN_TYPE, STATUS_ACTIVE) > 0) {
            throw new BusinessException(ErrorCode.TENANT_ADMIN_EXISTS);
        }
        if (userRepository.existsByTenantIdAndUsername(tenant.getId(), username)) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "鐢ㄦ埛鍚嶅湪绉熸埛鍐呭凡瀛樺湪");
        }
        OffsetDateTime now = OffsetDateTime.now();
        User user = User.builder()
                .tenantId(tenant.getId())
                .username(username)
                .name(name)
                .userType(TENANT_ADMIN_TYPE)
                .role(TENANT_ADMIN_TYPE)
                .passwordHash(passwordEncoder.encode(password))
                .mustChangePassword(true)
                .passwordUpdatedAt(now)
                .dealerId(dealerId)
                .status(STATUS_ACTIVE)
                .loginFailCount(0)
                .attrs(new HashMap<>())
                .updatedAt(now)
                .build();
        user.ensureAttrs();
        user = userRepository.save(user);
        auditService.log("TENANT_ADMIN_CREATE", "user", user.getId().toString(),
                Map.of("tenantId", tenant.getId().toString(), "username", username));
        return user;
    }

    @Transactional
    public void disableTenantAdmin(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
        if (!TENANT_ADMIN_TYPE.equals(user.getUserType())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "目标用户不是租户管理员");
        }
        user.setStatus(STATUS_DISABLED);
        user.setUpdatedAt(OffsetDateTime.now());
        userRepository.save(user);
        auditService.log("TENANT_ADMIN_DISABLE", "user", userId.toString(),
                Map.of("status", STATUS_DISABLED));
    }

    @Transactional
    public void resetTenantAdminPassword(Long userId, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
        if (!TENANT_ADMIN_TYPE.equals(user.getUserType())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "目标用户不是租户管理员");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(true);
        user.setPasswordUpdatedAt(OffsetDateTime.now());
        user.setLoginFailCount(0);
        user.setLockedUntil(null);
        user.setUpdatedAt(OffsetDateTime.now());
        userRepository.save(user);
        auditService.log("TENANT_ADMIN_RESET_PASSWORD", "user", userId.toString(), null);
    }

    // ==================== helpers ====================

    private void bindAdminRole(Long userId, Long roleId) {
        if (roleId == null) {
            log.warn("绉熸埛绠＄悊鍛樿鑹叉湭鎵惧埌锛岃烦杩囩敤鎴疯鑹茬粦瀹?userId={}", userId);
            return;
        }
        userRoleRepository.save(UserRole.builder()
                .userId(userId)
                .roleId(roleId)
                .grantedBy(currentAdminId())
                .build());
    }

    private User createTenantAdmin(Tenant tenant, String username, String password, String name,
                                   Long dealerId, OffsetDateTime now) {
        if (userRepository.countByTenantIdAndUserTypeAndStatus(
                tenant.getId(), TENANT_ADMIN_TYPE, STATUS_ACTIVE) > 0) {
            throw new BusinessException(ErrorCode.TENANT_ADMIN_EXISTS);
        }
        if (userRepository.existsByTenantIdAndUsername(tenant.getId(), username)) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "鐢ㄦ埛鍚嶅湪绉熸埛鍐呭凡瀛樺湪");
        }
        User user = User.builder()
                .tenantId(tenant.getId())
                .username(username)
                .name(name)
                .userType(TENANT_ADMIN_TYPE)
                .role(TENANT_ADMIN_TYPE)
                .passwordHash(passwordEncoder.encode(password))
                .mustChangePassword(true)
                .passwordUpdatedAt(now)
                .dealerId(dealerId)
                .status(STATUS_ACTIVE)
                .loginFailCount(0)
                .attrs(new HashMap<>())
                .updatedAt(now)
                .build();
        user.ensureAttrs();
        return userRepository.save(user);
    }

    private Tenant loadTenant(UUID id) {
        return tenantRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.TENANT_NOT_FOUND));
    }

    private Long currentAdminId() {
        return com.dms.common.util.TenantContext.getUserId();
    }

    private TenantAdminDTO toTenantAdminDTO(User user, Tenant tenant) {
        return TenantAdminDTO.builder()
                .id(user.getId())
                .tenantId(user.getTenantId())
                .tenantCode(tenant == null ? null : tenant.getCode())
                .tenantName(tenant == null ? null : tenant.getName())
                .username(user.getUsername())
                .name(user.getName())
                .status(user.getStatus())
                .mustChangePassword(Boolean.TRUE.equals(user.getMustChangePassword()))
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .build();
    }

    private AdminTenantDTO toDTO(Tenant t) {
        Long boundDealerId = null;
        UUID boundManufacturerId = null;
        if (TYPE_DEALER.equals(t.getTenantType())) {
            boundManufacturerId = t.getOwnerManufacturerId();
            boundDealerId = bindingRepository.findByDealerTenantId(t.getId())
                    .map(TenantDealerBinding::getDealerId).orElse(null);
        }
        return AdminTenantDTO.builder()
                .id(t.getId())
                .code(t.getCode())
                .name(t.getName())
                .status(t.getStatus())
                .tenantType(t.getTenantType())
                .deploymentMode(t.getDeploymentMode())
                .ownerManufacturerId(t.getOwnerManufacturerId())
                .contactName(t.getContactName())
                .contactPhone(t.getContactPhone())
                .contactEmail(t.getContactEmail())
                .boundDealerId(boundDealerId)
                .boundManufacturerTenantId(boundManufacturerId)
                .disableReason(t.getDisableReason())
                .disabledAt(t.getDisabledAt())
                .enabledAt(t.getEnabledAt())
                .createdAt(t.getCreatedAt())
                .build();
    }
}
