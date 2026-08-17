/*
 * 平台后台管理员仓储。
 */
package com.dms.adminauth.repository;

import com.dms.adminauth.entity.PlatformAdminUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public interface PlatformAdminUserRepository extends JpaRepository<PlatformAdminUser, Long> {

    Optional<PlatformAdminUser> findByUsername(String username);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update PlatformAdminUser u
               set u.loginFailCount = coalesce(u.loginFailCount, 0) + 1,
                   u.lockedUntil = case when coalesce(u.loginFailCount, 0) + 1 >= :maxFailCount
                                        then :lockedUntil else u.lockedUntil end,
                   u.updatedAt = :now
             where u.id = :adminId
            """)
    int incrementLoginFailCount(@Param("adminId") Long adminId,
                                @Param("maxFailCount") int maxFailCount,
                                @Param("lockedUntil") OffsetDateTime lockedUntil,
                                @Param("now") OffsetDateTime now);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update PlatformAdminUser u
               set u.loginFailCount = 0,
                   u.lockedUntil = null,
                   u.lastLoginAt = :now,
                   u.lastLoginIp = :ip,
                   u.updatedAt = :now
             where u.id = :adminId
            """)
    int resetLoginState(@Param("adminId") Long adminId,
                        @Param("ip") String ip,
                        @Param("now") OffsetDateTime now);
}
