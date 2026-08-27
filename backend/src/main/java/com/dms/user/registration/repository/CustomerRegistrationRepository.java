/*
 * 客户自助注册申请仓储。
 */
package com.dms.user.registration.repository;

import com.dms.user.registration.entity.CustomerRegistration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerRegistrationRepository
        extends JpaRepository<CustomerRegistration, Long>, JpaSpecificationExecutor<CustomerRegistration> {

    Optional<CustomerRegistration> findByIdAndTenantId(Long id, UUID tenantId);

    Page<CustomerRegistration> findByTenantId(UUID tenantId, Pageable pageable);

    Page<CustomerRegistration> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);

    long countByPhoneAndStatus(String phone, String status);
}
