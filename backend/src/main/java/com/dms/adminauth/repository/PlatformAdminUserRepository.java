/*
 * 平台后台管理员仓储。
 */
package com.dms.adminauth.repository;

import com.dms.adminauth.entity.PlatformAdminUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlatformAdminUserRepository extends JpaRepository<PlatformAdminUser, Long> {

    Optional<PlatformAdminUser> findByUsername(String username);
}
