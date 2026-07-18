/*
 * 用户仓储接口，提供租户维度的用户查询能力。
 */
package com.dms.user.repository;

import com.dms.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * 用户仓储：按租户 + 用户名、微信 OpenID 检索，及分页查询。
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByTenantIdAndUsername(UUID tenantId, String username);

    Optional<User> findByWechatOpenid(String wechatOpenid);

    Page<User> findByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByTenantIdAndUsername(UUID tenantId, String username);
}
