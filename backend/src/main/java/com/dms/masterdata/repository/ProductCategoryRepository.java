/*
 * 商品分类仓储接口。
 */
package com.dms.masterdata.repository;

import com.dms.masterdata.entity.ProductCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {

    Page<ProductCategory> findByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByTenantIdAndCode(UUID tenantId, String code);
}
