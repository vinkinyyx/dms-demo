/*
 * 客户自助注册与审核服务。
 * 公开注册提交 PENDING 申请；审核通过在单事务内创建经销商主数据、联系人/地址与 CUSTOMER 账号。
 */
package com.dms.user.registration.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.util.TenantContext;
import com.dms.masterdata.entity.Dealer;
import com.dms.masterdata.entity.DealerAddress;
import com.dms.masterdata.entity.DealerContact;
import com.dms.masterdata.repository.DealerAddressRepository;
import com.dms.masterdata.repository.DealerContactRepository;
import com.dms.masterdata.repository.DealerRepository;
import com.dms.rbac.entity.Role;
import com.dms.rbac.entity.UserRole;
import com.dms.rbac.repository.RoleRepository;
import com.dms.rbac.repository.UserRoleRepository;
import com.dms.tenant.entity.Tenant;
import com.dms.tenant.repository.TenantRepository;
import com.dms.user.entity.User;
import com.dms.user.registration.dto.CustomerRegisterRequest;
import com.dms.user.registration.dto.RegistrationDTO;
import com.dms.user.registration.entity.CustomerRegistration;
import com.dms.user.registration.repository.CustomerRegistrationRepository;
import com.dms.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerRegistrationService {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";

    public static final String USER_TYPE_CUSTOMER = "customer";
    public static final String ROLE_CUSTOMER = "CUSTOMER";
    public static final String REGISTER_SOURCE_SELF = "SELF";

    private final CustomerRegistrationRepository registrationRepository;
    private final DealerRepository dealerRepository;
    private final DealerAddressRepository dealerAddressRepository;
    private final DealerContactRepository dealerContactRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager em;

    // ==================== 公开注册 ====================

    @Transactional
    public RegistrationDTO register(CustomerRegisterRequest req) {
        UUID tenantId = resolveManufacturerTenant(req.getTenantCode());

        if (registrationRepository.countByPhoneAndStatus(req.getPhone(), STATUS_PENDING) > 0) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT,
                    "该手机号已有待审核的注册申请，请勿重复提交");
        }

        OffsetDateTime now = OffsetDateTime.now();
        CustomerRegistration reg = CustomerRegistration.builder()
                .tenantId(tenantId)
                .registerName(req.getRegisterName().trim())
                .phone(req.getPhone().trim())
                .email(req.getEmail())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .companyName(req.getCompanyName().trim())
                .uscNo(req.getUscNo())
                .legalPerson(req.getLegalPerson())
                .contactName(StringUtils.hasText(req.getContactName()) ? req.getContactName() : req.getRegisterName())
                .contactPhone(StringUtils.hasText(req.getContactPhone()) ? req.getContactPhone() : req.getPhone())
                .regAddress(req.getRegAddress())
                .addresses(req.getAddresses() == null ? new ArrayList<>() : req.getAddresses())
                .attachments(req.getAttachments() == null ? new ArrayList<>() : req.getAttachments())
                .status(STATUS_PENDING)
                .updatedAt(now)
                .build();
        reg.ensureJsonLists();
        reg = registrationRepository.save(reg);
        log.info("客户自助注册提交 id={} phone={} company={}", reg.getId(), reg.getPhone(), reg.getCompanyName());
        return RegistrationDTO.of(reg);
    }

    /** 公开接口中定位厂家租户：优先按 tenantCode，否则取唯一/默认启用厂家。 */
    private UUID resolveManufacturerTenant(String tenantCode) {
        if (StringUtils.hasText(tenantCode)) {
            return tenantRepository.findByCode(tenantCode.trim())
                    .filter(t -> "active".equalsIgnoreCase(t.getStatus()))
                    .map(Tenant::getId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.TENANT_NOT_FOUND, "厂家租户不存在或已停用"));
        }
        // 演示/生产主厂家租户固定为 code='default'（id 11111111-...），避免在大量测试租户中误取 SYSTEM/自动创建租户。
        Tenant def = tenantRepository.findByCode("default").orElse(null);
        if (def != null && "active".equalsIgnoreCase(def.getStatus())
                && "MANUFACTURER".equalsIgnoreCase(def.getTenantType())) {
            return def.getId();
        }
        Page<Tenant> manufacturers = tenantRepository.findByTenantTypeAndStatus(
                "MANUFACTURER", "active", PageRequest.of(0, 20));
        List<Tenant> list = manufacturers.getContent();
        if (list.isEmpty()) {
            throw new BusinessException(ErrorCode.TENANT_NOT_FOUND, "未找到可用厂家租户，请联系管理员");
        }
        // 排除 SYSTEM 与自动创建的测试租户
        List<Tenant> real = list.stream()
                .filter(t -> !"SYSTEM".equalsIgnoreCase(t.getCode()))
                .filter(t -> t.getCode() != null && !t.getCode().startsWith("TENANT-") && !t.getCode().startsWith("TM-"))
                .toList();
        List<Tenant> pick = real.isEmpty() ? list : real;
        if (pick.size() > 1) {
            log.warn("存在多个启用厂家租户，公开注册未指定 tenantCode，默认取第一个[{}]，建议在注册链接中携带 tenantCode", pick.get(0).getCode());
        }
        return pick.get(0).getId();
    }

    // ==================== 审核（厂家管理员） ====================

    @Transactional
    public RegistrationDTO approve(Long id) {
        UUID tid = currentTenant();
        CustomerRegistration reg = registrationRepository.findByIdAndTenantId(id, tid)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "注册申请不存在"));
        if (!STATUS_PENDING.equals(reg.getStatus()) && !STATUS_REJECTED.equals(reg.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "申请状态为 " + reg.getStatus() + "，不可审核通过");
        }
        if (reg.getCreatedDealerId() != null) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "该申请已创建过客户主数据");
        }

        OffsetDateTime now = OffsetDateTime.now();

        Dealer dealer = Dealer.builder()
                .tenantId(tid)
                .code(generateDealerCode(tid))
                .name(reg.getCompanyName().trim())
                .legalPerson(reg.getLegalPerson())
                .uscNo(reg.getUscNo())
                .regAddress(reg.getRegAddress())
                .contactName(firstNonBlank(reg.getContactName(), reg.getRegisterName()))
                .contactPhone(firstNonBlank(reg.getContactPhone(), reg.getPhone()))
                .contactEmail(reg.getEmail())
                .status("active")
                .attrs(new java.util.HashMap<>())
                .updatedAt(now)
                .build();
        dealer.ensureAttrs();
        dealer.getAttrs().put("registerSource", REGISTER_SOURCE_SELF);
        dealer.getAttrs().put("registrationId", reg.getId());
        dealer = dealerRepository.save(dealer);

        // 注册来源标记写入 dealers 列（V129）
        em.createNativeQuery("UPDATE dealers SET register_source = ?1, registration_id = ?2 WHERE id = ?3")
                .setParameter(1, REGISTER_SOURCE_SELF)
                .setParameter(2, reg.getId())
                .setParameter(3, dealer.getId())
                .executeUpdate();

        // 收货地址
        createAddresses(tid, dealer.getId(), reg, now);

        // 联系人
        DealerContact contact = DealerContact.builder()
                .tenantId(tid)
                .dealerId(dealer.getId())
                .contactName(firstNonBlank(reg.getContactName(), reg.getRegisterName()))
                .phone(firstNonBlank(reg.getContactPhone(), reg.getPhone()))
                .email(reg.getEmail())
                .position("注册联系人")
                .isDefault(true)
                .status("active")
                .updatedAt(now)
                .build();
        dealerContactRepository.save(contact);

        // 客户账号
        String username = generateUsername(tid, reg.getPhone(), reg.getCompanyName());
        User user = User.builder()
                .tenantId(tid)
                .username(username)
                .name(firstNonBlank(reg.getRegisterName(), reg.getCompanyName()))
                .userType(USER_TYPE_CUSTOMER)
                .role(ROLE_CUSTOMER)
                .passwordHash(reg.getPasswordHash() != null ? reg.getPasswordHash()
                        : passwordEncoder.encode(reg.getPhone()))
                .email(reg.getEmail())
                .phone(reg.getPhone())
                .dealerId(dealer.getId())
                .status("active")
                .mustChangePassword(false)
                .passwordUpdatedAt(now)
                .loginFailCount(0)
                .attrs(new java.util.HashMap<>())
                .updatedAt(now)
                .build();
        user.ensureAttrs();
        user = userRepository.save(user);

        bindCustomerRole(tid, user.getId());

        reg.setStatus(STATUS_APPROVED);
        reg.setReviewerId(TenantContext.getUserId());
        reg.setReviewedAt(now);
        reg.setRejectReason(null);
        reg.setCreatedUserId(user.getId());
        reg.setCreatedDealerId(dealer.getId());
        reg.setUpdatedAt(now);
        registrationRepository.save(reg);

        log.info("客户注册审核通过 regId={} dealerId={} userId={} username={}",
                reg.getId(), dealer.getId(), user.getId(), username);
        return RegistrationDTO.of(reg);
    }

    @Transactional
    public RegistrationDTO reject(Long id, String reason) {
        UUID tid = currentTenant();
        CustomerRegistration reg = registrationRepository.findByIdAndTenantId(id, tid)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "注册申请不存在"));
        if (STATUS_APPROVED.equals(reg.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "已通过的申请不可驳回");
        }
        OffsetDateTime now = OffsetDateTime.now();
        reg.setStatus(STATUS_REJECTED);
        reg.setRejectReason(reason);
        reg.setReviewerId(TenantContext.getUserId());
        reg.setReviewedAt(now);
        reg.setUpdatedAt(now);
        registrationRepository.save(reg);
        log.info("客户注册驳回 regId={} reason={}", id, reason);
        return RegistrationDTO.of(reg);
    }

    // ==================== 查询 ====================

    @Transactional(readOnly = true)
    public PageResult<RegistrationDTO> list(PageQuery pageQuery, String status, String keyword) {
        UUID tid = currentTenant();
        int pageNumber = Math.max(0, pageQuery.getPage() == null ? 0 : pageQuery.getPage() - 1);
        int pageSize = Math.min(200, Math.max(1, pageQuery.getSize() == null ? 20 : pageQuery.getSize()));

        StringBuilder where = new StringBuilder("WHERE tenant_id = :tid AND deleted_at IS NULL");
        if (StringUtils.hasText(status)) where.append(" AND status = :status");
        if (StringUtils.hasText(keyword)) {
            where.append(" AND (company_name ILIKE :kw OR phone ILIKE :kw OR register_name ILIKE :kw OR usc_no ILIKE :kw)");
        }
        var countQ = em.createNativeQuery("SELECT COUNT(1) FROM customer_registrations " + where);
        countQ.setParameter("tid", tid);
        var listQ = em.createNativeQuery(
                "SELECT id FROM customer_registrations " + where + " ORDER BY id DESC");
        listQ.setParameter("tid", tid);
        if (StringUtils.hasText(status)) {
            countQ.setParameter("status", status.trim());
            listQ.setParameter("status", status.trim());
        }
        if (StringUtils.hasText(keyword)) {
            countQ.setParameter("kw", "%" + keyword.trim() + "%");
            listQ.setParameter("kw", "%" + keyword.trim() + "%");
        }
        long total = ((Number) countQ.getSingleResult()).longValue();
        listQ.setFirstResult(pageNumber * pageSize);
        listQ.setMaxResults(pageSize);
        @SuppressWarnings("unchecked")
        List<Number> ids = listQ.getResultList();
        List<RegistrationDTO> rows = new ArrayList<>();
        for (Number n : ids) {
            registrationRepository.findByIdAndTenantId(n.longValue(), tid)
                    .ifPresent(r -> rows.add(RegistrationDTO.of(r)));
        }
        return new PageResult<>(total, pageNumber + 1, pageSize, rows);
    }

    @Transactional(readOnly = true)
    public RegistrationDTO detail(Long id) {
        return registrationRepository.findByIdAndTenantId(id, currentTenant())
                .map(RegistrationDTO::of)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "注册申请不存在"));
    }

    // ==================== helpers ====================

    private UUID currentTenant() {
        UUID tid = TenantContext.getTenantId();
        if (tid == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未识别租户上下文");
        }
        return tid;
    }

    private void createAddresses(UUID tid, Long dealerId, CustomerRegistration reg, OffsetDateTime now) {
        List<Map<String, Object>> addresses = reg.getAddresses();
        boolean hasDefault = false;
        if (addresses != null) {
            for (int i = 0; i < addresses.size(); i++) {
                Map<String, Object> a = addresses.get(i);
                boolean isDefault = Boolean.TRUE.equals(a.get("isDefault")) || (!hasDefault && i == 0);
                if (isDefault) hasDefault = true;
                DealerAddress addr = DealerAddress.builder()
                        .tenantId(tid)
                        .dealerId(dealerId)
                        .addressName(asString(firstNonNull(a.get("addressName"), a.get("name"))))
                        .isDefault(isDefault)
                        .contactName(asString(a.get("contactName")))
                        .phone(asString(a.get("phone")))
                        .province(asString(a.get("province")))
                        .city(asString(a.get("city")))
                        .district(asString(a.get("district")))
                        .address(asString(firstNonNull(a.get("address"), a.get("detailAddress"))))
                        .postalCode(asString(a.get("postalCode")))
                        .status("active")
                        .updatedAt(now)
                        .build();
                dealerAddressRepository.save(addr);
            }
        }
        if (!hasDefault && StringUtils.hasText(reg.getRegAddress())) {
            dealerAddressRepository.save(DealerAddress.builder()
                    .tenantId(tid)
                    .dealerId(dealerId)
                    .addressName("默认收货地址")
                    .isDefault(true)
                    .contactName(firstNonBlank(reg.getContactName(), reg.getRegisterName()))
                    .phone(firstNonBlank(reg.getContactPhone(), reg.getPhone()))
                    .address(reg.getRegAddress())
                    .status("active")
                    .updatedAt(now)
                    .build());
        }
    }

    private void bindCustomerRole(UUID tid, Long userId) {
        Optional<Role> role = roleRepository.findByTenantIdAndCode(tid, ROLE_CUSTOMER);
        if (role.isEmpty()) {
            log.warn("CUSTOMER 角色未在租户 {} 初始化，用户 {} 暂未绑定角色", tid, userId);
            return;
        }
        Long roleId = role.get().getId();
        boolean exists = userRoleRepository.findByUserId(userId).stream()
                .anyMatch(ur -> roleId.equals(ur.getRoleId()));
        if (!exists) {
            userRoleRepository.save(UserRole.builder()
                    .userId(userId)
                    .roleId(roleId)
                    .grantedBy(TenantContext.getUserId())
                    .build());
        }
    }

    private String generateDealerCode(UUID tid) {
        for (int i = 0; i < 30; i++) {
            String code = ("CUS" + Long.toString(Math.abs(UUID.randomUUID().getMostSignificantBits()), 36))
                    .toUpperCase();
            if (code.length() > 16) code = code.substring(0, 16);
            if (!dealerRepository.existsByTenantIdAndCode(tid, code)) {
                return code;
            }
        }
        throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "无法生成唯一客户编码");
    }

    private String generateUsername(UUID tid, String phone, String companyName) {
        // 客户直接用注册手机号登录（PC / H5 / 小程序体验一致）；占用时才回退到 cust_ 前缀
        if (!userRepository.existsByTenantIdAndUsername(tid, phone)) {
            return phone;
        }
        String base = "cust_" + phone;
        for (int i = 1; i < 100; i++) {
            String candidate = i == 1 ? base : base + i;
            if (!userRepository.existsByTenantIdAndUsername(tid, candidate)) {
                return candidate;
            }
        }
        return "cust_" + System.nanoTime();
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (StringUtils.hasText(v)) return v.trim();
        }
        return null;
    }

    private Object firstNonNull(Object... values) {
        for (Object v : values) {
            if (v != null && StringUtils.hasText(String.valueOf(v))) return v;
        }
        return null;
    }

    private String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}

