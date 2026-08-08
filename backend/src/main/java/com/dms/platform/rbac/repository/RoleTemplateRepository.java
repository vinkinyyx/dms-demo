package com.dms.platform.rbac.repository;

import com.dms.platform.rbac.entity.RoleTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoleTemplateRepository extends JpaRepository<RoleTemplate, Long> {

    List<RoleTemplate> findByStatus(String status);

    List<RoleTemplate> findByTenantTypeAndStatus(String tenantType, String status);
}