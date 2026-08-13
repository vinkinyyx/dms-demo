/*
 * 用户仓储接口，提供租户维度的用户查询能力。
 */
package com.dms.user.repository;

import com.dms.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByTenantIdAndUsername(UUID tenantId, String username);

    Optional<User> findByWechatOpenid(String wechatOpenid);

    Page<User> findByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByTenantIdAndUsername(UUID tenantId, String username);

    boolean existsByTenantIdAndPhone(UUID tenantId, String phone);

    long countByTenantIdAndUserTypeAndStatus(UUID tenantId, String userType, String status);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update User u
               set u.loginFailCount = coalesce(u.loginFailCount, 0) + 1,
                   u.lockedUntil = case when coalesce(u.loginFailCount, 0) + 1 >= :maxFailCount
                                        then :lockedUntil else u.lockedUntil end,
                   u.updatedAt = :now
             where u.id = :userId
            """)
    int incrementLoginFailCount(@Param("userId") Long userId,
                                @Param("maxFailCount") int maxFailCount,
                                @Param("lockedUntil") OffsetDateTime lockedUntil,
                                @Param("now") OffsetDateTime now);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update User u
               set u.loginFailCount = 0,
                   u.lockedUntil = null,
                   u.lastLoginAt = :now,
                   u.lastLoginIp = :ip,
                   u.updatedAt = :now
             where u.id = :userId
            """)
    int resetLoginState(@Param("userId") Long userId,
                        @Param("ip") String ip,
                        @Param("now") OffsetDateTime now);
}
