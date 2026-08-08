/*
 * 按钮配置仓库。
 * 关键查询：按 (tenant_id, page_key, scope) 取所有可见按钮，按 sortOrder 升序。
 */
package com.dms.platform.config.repository;

import com.dms.platform.config.entity.PlatformButtonConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PlatformButtonConfigRepository extends JpaRepository<PlatformButtonConfig, Long> {

    /**
     * 取平台默认（tenant_id IS NULL）
     */
    List<PlatformButtonConfig> findByTenantIdIsNullAndPageKeyAndScopeAndStatusOrderBySortOrderAsc(
            String pageKey, String scope, String status);

    /**
     * 取租户覆盖
     */
    List<PlatformButtonConfig> findByTenantIdAndPageKeyAndScopeAndStatusOrderBySortOrderAsc(
            UUID tenantId, String pageKey, String scope, String status);

    PlatformButtonConfig findByTenantIdAndPageKeyAndScopeAndButtonKeyAndStatus(
            UUID tenantId, String pageKey, String scope, String buttonKey, String status);

    /**
     * 管理后台：列出某 pageKey + tenantType 下所有记录（默认 + 所有租户覆盖）
     */
    @Query("SELECT b FROM PlatformButtonConfig b WHERE b.pageKey = :pageKey AND b.tenantType = :tenantType AND b.status = :status ORDER BY b.scope, b.sortOrder")
    List<PlatformButtonConfig> adminList(@Param("pageKey") String pageKey,
                                         @Param("tenantType") String tenantType,
                                         @Param("status") String status);
}