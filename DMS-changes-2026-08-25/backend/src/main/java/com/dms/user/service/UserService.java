/*
 * 用户业务服务，覆盖用户管理、锁定、密码重置、微信绑定等能力。
 */
package com.dms.user.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.util.SpecUtil;
import com.dms.common.util.TenantContext;
import com.dms.user.dto.ResetPasswordRequest;
import com.dms.user.dto.UserCreateRequest;
import com.dms.user.dto.UserDTO;
import com.dms.user.dto.UserUpdateRequest;
import com.dms.user.entity.User;
import com.dms.rbac.entity.Role;
import com.dms.rbac.entity.UserRole;
import com.dms.rbac.repository.RoleRepository;
import com.dms.rbac.repository.UserRoleRepository;
import com.dms.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 用户业务服务：包含 CRUD、解锁、重置密码、微信绑定解绑、登录失败计数等能力。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    /** 连续错误达到该次数后锁定账号 */
    private static final int MAX_FAIL_COUNT = 9;
    /** 锁定时长（分钟） */
    private static final long LOCK_MINUTES = 30L;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager em;

    @Transactional(readOnly = true)
    public PageResult<UserDTO> list(UUID tenantId, PageQuery pageQuery,
                                    Long id, String username, String name, String userType,
                                    String email, String status,
                                    String createdAtFrom, String createdAtTo,
                                    String updatedAtFrom, String updatedAtTo) {
        UUID effectiveTenant = resolveTenantId(tenantId);
        int pageNumber = Math.max(0, pageQuery.getPage() == null ? 0 : pageQuery.getPage() - 1);
        int pageSize = Math.min(1000, Math.max(1, pageQuery.getSize() == null ? 20 : pageQuery.getSize()));
        StringBuilder where = new StringBuilder("WHERE u.deleted_at IS NULL");
        List<Object> params = new ArrayList<>();
        int idx = 1;
        if (effectiveTenant != null) {
            where.append(" AND u.tenant_id = ?").append(idx++);
            params.add(effectiveTenant);
        }
        if (id != null) { where.append(" AND u.id = ?").append(idx++); params.add(id); }
        if (username != null && !username.isBlank()) { where.append(" AND u.username ILIKE ?").append(idx++); params.add("%" + username.trim() + "%"); }
        if (name != null && !name.isBlank()) { where.append(" AND u.name ILIKE ?").append(idx++); params.add("%" + name.trim() + "%"); }
        if (userType != null && !userType.isBlank()) { where.append(" AND u.user_type = ?").append(idx++); params.add(userType); }
        if (email != null && !email.isBlank()) { where.append(" AND u.email ILIKE ?").append(idx++); params.add("%" + email.trim() + "%"); }
        if (status != null && !status.isBlank()) { where.append(" AND u.status = ?").append(idx++); params.add(status); }
        if (createdAtFrom != null && !createdAtFrom.isBlank()) {
            java.sql.Timestamp t = SpecUtil.rangeBound(createdAtFrom, true);
            if (t != null) { where.append(" AND u.created_at >= ?").append(idx++); params.add(t); }
        }
        if (createdAtTo != null && !createdAtTo.isBlank()) {
            java.sql.Timestamp t = SpecUtil.rangeBound(createdAtTo, false);
            if (t != null) { where.append(SpecUtil.hasTime(createdAtTo) ? " AND u.created_at <= ?" : " AND u.created_at < ?").append(idx++); params.add(t); }
        }
        if (updatedAtFrom != null && !updatedAtFrom.isBlank()) {
            java.sql.Timestamp t = SpecUtil.rangeBound(updatedAtFrom, true);
            if (t != null) { where.append(" AND u.updated_at >= ?").append(idx++); params.add(t); }
        }
        if (updatedAtTo != null && !updatedAtTo.isBlank()) {
            java.sql.Timestamp t = SpecUtil.rangeBound(updatedAtTo, false);
            if (t != null) { where.append(SpecUtil.hasTime(updatedAtTo) ? " AND u.updated_at <= ?" : " AND u.updated_at < ?").append(idx++); params.add(t); }
        }

        var cnt = em.createNativeQuery("SELECT COUNT(*) FROM users u " + where);
        for (int i = 0; i < params.size(); i++) cnt.setParameter(i + 1, params.get(i));
        long total = ((Number) cnt.getSingleResult()).longValue();

        String sortExpr = buildSortExpr(pageQuery.getSort());
        var q = em.createNativeQuery("SELECT u.* FROM users u " + where + " ORDER BY " + sortExpr + " LIMIT ?" + idx + " OFFSET ?" + (idx + 1), User.class);
        for (int i = 0; i < params.size(); i++) q.setParameter(i + 1, params.get(i));
        q.setParameter(idx, pageSize);
        q.setParameter(idx + 1, pageNumber * pageSize);
        @SuppressWarnings("unchecked")
        List<User> rows = q.getResultList();
        List<UserDTO> dtoList = rows.stream().map(this::toDTO).collect(Collectors.toList());
        return new PageResult<>(total, pageNumber + 1, pageSize, dtoList);
    }

    private String buildSortExpr(String sort) {
        String defaultSort = "u.updated_at DESC, u.id DESC";
        if (sort == null || sort.isBlank()) return defaultSort;
        List<String> orders = new ArrayList<>();
        for (String seg : sort.split(";")) {
            String[] parts = seg.split(",");
            if (parts.length == 0 || parts[0].isBlank()) continue;
            String field = parts[0].trim();
            String dir = parts.length > 1 && parts[1].trim().equalsIgnoreCase("asc") ? "ASC" : "DESC";
            switch (field) {
                case "id" -> orders.add("u.id " + dir);
                case "username" -> orders.add("u.username " + dir);
                case "name" -> orders.add("u.name " + dir);
                case "userType" -> orders.add("u.user_type " + dir);
                case "email" -> orders.add("u.email " + dir);
                case "status" -> orders.add("u.status " + dir);
                case "createdAt" -> orders.add("u.created_at " + dir);
                case "updatedAt" -> orders.add("u.updated_at " + dir);
                default -> {}
            }
        }
        return orders.isEmpty() ? defaultSort : String.join(",", orders);
    }

    @Transactional(readOnly = true)
    public UserDTO get(Long id) {
        return toDTO(loadUser(id));
    }

    @Transactional
    public UserDTO create(UserCreateRequest request) {
        UUID tenantId = request.getTenantId() != null ? request.getTenantId() : TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");
        }
        if (userRepository.existsByTenantIdAndUsername(tenantId, request.getUsername())) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "用户名在租户内已存在");
        }
        if (request.getEmail() == null || !request.getEmail().matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "邮箱格式不正确");
        }
        if (request.getPhone() == null || !request.getPhone().matches("^1[3-9]\\d{9}$")) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "手机号格式不正确");
        }
        User user = User.builder()
                .tenantId(tenantId)
                .username(request.getUsername())
                .name(request.getName())
                .userType(request.getUserType())
                .role(request.getUserType())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .mustChangePassword(true)
                .passwordUpdatedAt(OffsetDateTime.now())
                .email(request.getEmail())
                .phone(request.getPhone())
                .orgId(request.getOrgId())
                .dealerId(request.getDealerId())
                .status("active")
                .loginFailCount(0)
                .attrs(request.getAttrs() == null ? new HashMap<>() : request.getAttrs())
                .updatedAt(OffsetDateTime.now())
                .build();
        user.ensureAttrs();
        User saved = userRepository.save(user);
        if (request.getRoleId() != null) {
            assignRole(saved, request.getRoleId());
        }
        return toDTO(saved);
    }

    @Transactional
    public UserDTO updateProfile(Long id, UserUpdateRequest request) {
        User user = loadUser(id);
        if (request.getName() != null) user.setName(request.getName());
        if (request.getEmail() != null) user.setEmail(request.getEmail());
        if (request.getPhone() != null) user.setPhone(request.getPhone());
        if (request.getOrgId() != null) user.setOrgId(request.getOrgId());
        if (request.getDealerId() != null) user.setDealerId(request.getDealerId());
        if (request.getStatus() != null) user.setStatus(request.getStatus());
        if (request.getAttrs() != null) user.setAttrs(request.getAttrs());
        user.setUpdatedAt(OffsetDateTime.now());
        user.ensureAttrs();
        User saved = userRepository.save(user);
        if (request.getRoleId() != null) {
            assignRole(saved, request.getRoleId());
        }
        return toDTO(saved);
    }

    private void assignRole(User user, Long roleId) {
        UUID tenantId = user.getTenantId();
        List<Long> owned = roleRepository.findByTenantId(tenantId).stream().map(Role::getId).collect(Collectors.toList());
        if (!owned.contains(roleId)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "角色不属于当前租户: " + roleId);
        }
        userRoleRepository.deleteByUserId(user.getId());
        Long operator = TenantContext.getUserId();
        userRoleRepository.save(UserRole.builder().userId(user.getId()).roleId(roleId).grantedBy(operator).build());
        log.info("用户 {} 分配角色 {}", user.getId(), roleId);
    }


    private Long primaryRoleId(Long userId) {
        return userRoleRepository.findByUserId(userId).stream().findFirst().map(UserRole::getRoleId).orElse(null);
    }

    private String primaryRoleName(Long userId) {
        Long rid = primaryRoleId(userId);
        if (rid == null) return null;
        return roleRepository.findById(rid).map(Role::getName).orElse(null);
    }

    @Transactional
    public void unlock(Long id) {
        User user = loadUser(id);
        user.setLockedUntil(null);
        user.setLoginFailCount(0);
        user.setUpdatedAt(OffsetDateTime.now());
        userRepository.save(user);
        log.info("用户 {} 已被解锁", user.getUsername());
    }

    @Transactional
    public void resetPassword(Long id, ResetPasswordRequest request) {
        User user = loadUser(id);
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setMustChangePassword(true);
        user.setPasswordUpdatedAt(OffsetDateTime.now());
        user.setLoginFailCount(0);
        user.setLockedUntil(null);
        user.setUpdatedAt(OffsetDateTime.now());
        userRepository.save(user);
        log.info("用户 {} 密码已被管理员重置", user.getUsername());
    }

    @Transactional
    public void bindWechat(Long userId, String openid, String unionid) {
        User user = loadUser(userId);
        userRepository.findByWechatOpenid(openid).ifPresent(u -> {
            if (!u.getId().equals(userId)) {
                throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "该微信已绑定其他账号");
            }
        });
        user.setWechatOpenid(openid);
        user.setWechatUnionid(unionid);
        user.setWechatBoundAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        userRepository.save(user);
    }

    @Transactional
    public void unbindWechat(Long userId) {
        User user = loadUser(userId);
        user.setWechatOpenid(null);
        user.setWechatUnionid(null);
        user.setWechatBoundAt(null);
        user.setUpdatedAt(OffsetDateTime.now());
        userRepository.save(user);
    }

    @Transactional
    public void incrementFailCount(Long userId) {
        OffsetDateTime now = OffsetDateTime.now();
        int updated = userRepository.incrementLoginFailCount(
                userId, MAX_FAIL_COUNT, now.plusMinutes(LOCK_MINUTES), now);
        if (updated > 0) {
            userRepository.findById(userId).ifPresent(user -> {
                Integer failCount = user.getLoginFailCount();
                if (failCount != null && failCount >= MAX_FAIL_COUNT) {
                    log.warn("User {} failed login {} times, locked until {}", user.getUsername(), failCount, user.getLockedUntil());
                }
            });
        }
    }

    @Transactional
    public void resetFailCount(Long userId, String ip) {
        userRepository.resetLoginState(userId, ip, OffsetDateTime.now());
    }

    /**
     * 内部使用：加载原始 User 实体（不做敏感字段过滤）。
     */
    public User loadUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
    }

    /**
     * 内部使用：按租户名 + 用户名定位用户。
     */
    public User loadByTenantAndUsername(UUID tenantId, String username) {
        return userRepository.findByTenantIdAndUsername(tenantId, username)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
    }

    public UserDTO toDTO(User u) {
        return UserDTO.builder()
                .id(u.getId())
                .tenantId(u.getTenantId())
                .username(u.getUsername())
                .name(u.getName())
                .userType(u.getUserType())
                .mustChangePassword(u.getMustChangePassword())
                .email(u.getEmail())
                .phone(u.getPhone())
                .orgId(u.getOrgId())
                .dealerId(u.getDealerId())
                .roleId(primaryRoleId(u.getId()))
                .roleName(primaryRoleName(u.getId()))
                .roleIds(userRoleRepository.findByUserId(u.getId()).stream().map(UserRole::getRoleId).collect(Collectors.toList()))
                .roleNames(roleRepository.findAllById(userRoleRepository.findByUserId(u.getId()).stream().map(UserRole::getRoleId).collect(Collectors.toList())).stream().map(Role::getName).collect(Collectors.toList()))
                .status(u.getStatus())
                .loginFailCount(u.getLoginFailCount())
                .lockedUntil(u.getLockedUntil())
                .lastLoginAt(u.getLastLoginAt())
                .lastLoginIp(u.getLastLoginIp())
                .attrs(u.getAttrs())
                .wechatBound(u.getWechatOpenid() != null)
                .wechatBoundAt(u.getWechatBoundAt())
                .createdAt(u.getCreatedAt())
                .updatedAt(u.getUpdatedAt())
                .build();
    }

    private UUID resolveTenantId(UUID candidate) {
        if (candidate != null) return candidate;
        return TenantContext.getTenantId();
    }

    @Transactional
    public void delete(Long id) {
        User u = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
        u.setDeletedAt(OffsetDateTime.now());
        u.setStatus("deleted");
        userRepository.save(u);
    }
}
