/*
 * 用户业务服务，覆盖用户管理、锁定、密码重置、微信绑定等能力。
 */
package com.dms.user.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.util.TenantContext;
import com.dms.user.dto.ResetPasswordRequest;
import com.dms.user.dto.UserCreateRequest;
import com.dms.user.dto.UserDTO;
import com.dms.user.dto.UserUpdateRequest;
import com.dms.user.entity.User;
import com.dms.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.UUID;

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

    @Transactional(readOnly = true)
    public PageResult<UserDTO> list(UUID tenantId, PageQuery pageQuery) {
        UUID effectiveTenant = resolveTenantId(tenantId);
        Page<User> page = effectiveTenant == null
                ? userRepository.findAll(pageQuery.toPageable())
                : userRepository.findByTenantId(effectiveTenant, pageQuery.toPageable());
        return PageResult.of(page.map(this::toDTO));
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
        User user = User.builder()
                .tenantId(tenantId)
                .username(request.getUsername())
                .name(request.getName())
                .userType(request.getUserType())
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
        return toDTO(userRepository.save(user));
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
        return toDTO(userRepository.save(user));
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
        User user = loadUser(userId);
        int next = (user.getLoginFailCount() == null ? 0 : user.getLoginFailCount()) + 1;
        user.setLoginFailCount(next);
        if (next >= MAX_FAIL_COUNT) {
            user.setLockedUntil(OffsetDateTime.now().plusMinutes(LOCK_MINUTES));
            log.warn("用户 {} 连续登录失败 {} 次，锁定至 {}", user.getUsername(), next, user.getLockedUntil());
        }
        user.setUpdatedAt(OffsetDateTime.now());
        userRepository.save(user);
    }

    @Transactional
    public void resetFailCount(Long userId, String ip) {
        User user = loadUser(userId);
        user.setLoginFailCount(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(OffsetDateTime.now());
        user.setLastLoginIp(ip);
        user.setUpdatedAt(OffsetDateTime.now());
        userRepository.save(user);
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
}
