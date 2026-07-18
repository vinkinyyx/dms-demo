/*
 * 库存仓储：支持按批次/序列号唯一定位与 SELECT FOR UPDATE。
 */
package com.dms.inventory.repository;

import com.dms.inventory.entity.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    /**
     * 库存主键定位，用于 upsert（配合序列号/批次为空的场景，需在 Service 里判空）。
     * 使用 native SQL 以支持 NULL = NULL 比较。
     */
    @Query(value = "SELECT * FROM inventory " +
            " WHERE tenant_id = :tenantId " +
            "   AND warehouse_id " + " IS NOT DISTINCT FROM :warehouseId " +
            "   AND product_id " + " IS NOT DISTINCT FROM :productId " +
            "   AND batch_no " + " IS NOT DISTINCT FROM :batchNo " +
            "   AND serial_no " + " IS NOT DISTINCT FROM :serialNo " +
            " LIMIT 1", nativeQuery = true)
    Optional<Inventory> findKeyed(@Param("tenantId") UUID tenantId,
                                  @Param("warehouseId") Long warehouseId,
                                  @Param("productId") Long productId,
                                  @Param("batchNo") String batchNo,
                                  @Param("serialNo") String serialNo);

    /**
     * 加锁版本，用于扣减库存时防并发。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = "SELECT * FROM inventory " +
            " WHERE tenant_id = :tenantId " +
            "   AND warehouse_id IS NOT DISTINCT FROM :warehouseId " +
            "   AND product_id IS NOT DISTINCT FROM :productId " +
            "   AND batch_no IS NOT DISTINCT FROM :batchNo " +
            "   AND serial_no IS NOT DISTINCT FROM :serialNo " +
            " FOR UPDATE", nativeQuery = true)
    Optional<Inventory> lockKeyed(@Param("tenantId") UUID tenantId,
                                  @Param("warehouseId") Long warehouseId,
                                  @Param("productId") Long productId,
                                  @Param("batchNo") String batchNo,
                                  @Param("serialNo") String serialNo);

    @Query(value = "SELECT * FROM inventory " +
            " WHERE tenant_id = :tenantId " +
            "   AND (CAST(:dealerId AS bigint) IS NULL OR dealer_id = :dealerId) " +
            "   AND (CAST(:productId AS bigint) IS NULL OR product_id = :productId) " +
            "   AND (CAST(:batchNo AS text) IS NULL OR batch_no = :batchNo)",
           countQuery = "SELECT COUNT(*) FROM inventory " +
            " WHERE tenant_id = :tenantId " +
            "   AND (CAST(:dealerId AS bigint) IS NULL OR dealer_id = :dealerId) " +
            "   AND (CAST(:productId AS bigint) IS NULL OR product_id = :productId) " +
            "   AND (CAST(:batchNo AS text) IS NULL OR batch_no = :batchNo)",
           nativeQuery = true)
    Page<Inventory> query(@Param("tenantId") UUID tenantId,
                          @Param("dealerId") Long dealerId,
                          @Param("productId") Long productId,
                          @Param("batchNo") String batchNo,
                          Pageable pageable);

    List<Inventory> findByTenantIdAndDealerId(UUID tenantId, Long dealerId);
}
