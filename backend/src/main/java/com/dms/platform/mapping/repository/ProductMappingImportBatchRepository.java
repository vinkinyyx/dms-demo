package com.dms.platform.mapping.repository;

import com.dms.platform.mapping.entity.ProductMappingImportBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductMappingImportBatchRepository
        extends JpaRepository<ProductMappingImportBatch, Long> {
}