package com.dms.platform.rbac.repository;

import com.dms.platform.rbac.entity.RoleTemplateResource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoleTemplateResourceRepository
        extends JpaRepository<RoleTemplateResource, RoleTemplateResource.RoleTemplateResourceId> {

    List<RoleTemplateResource> findByTemplateId(Long templateId);
}