/*
 * 资源仓储。
 */
package com.dms.rbac.repository;

import com.dms.rbac.entity.Resource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * 资源仓储。
 */
@Repository
public interface ResourceRepository extends JpaRepository<Resource, Long> {

    List<Resource> findByTenantId(UUID tenantId);

    List<Resource> findByIdIn(List<Long> ids);
}
