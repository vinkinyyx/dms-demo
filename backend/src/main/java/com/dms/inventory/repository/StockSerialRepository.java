/*
 * stock_serials 仓储。
 */
package com.dms.inventory.repository;

import com.dms.inventory.entity.StockSerial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StockSerialRepository extends JpaRepository<StockSerial, Long> {

    /**
     * 查询某产品在某仓库、某批次下、且未出库（在库）的序列号清单。
     * shipped_at IS NULL 视为仍在库。
     */
    @Query("SELECT s FROM StockSerial s " +
           " WHERE s.tenantId = :tenantId " +
           "   AND s.productId = :productId " +
           "   AND s.warehouseId = :warehouseId " +
           "   AND s.batchNo = :batchNo " +
           "   AND s.shippedAt IS NULL " +
           " ORDER BY s.id ASC")
    List<StockSerial> findAvailable(@Param("tenantId") UUID tenantId,
                                    @Param("productId") Long productId,
                                    @Param("warehouseId") Long warehouseId,
                                    @Param("batchNo") String batchNo);

    /**
     * 按序列号定位在库记录。
     */
    java.util.Optional<StockSerial> findByTenantIdAndSerialNoAndShippedAtIsNull(UUID tenantId, String serialNo);
}