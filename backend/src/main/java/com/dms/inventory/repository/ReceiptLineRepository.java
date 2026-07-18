/*
 * 收货明细行仓储。
 */
package com.dms.inventory.repository;

import com.dms.inventory.entity.ReceiptLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReceiptLineRepository extends JpaRepository<ReceiptLine, Long> {
    List<ReceiptLine> findByReceiptId(Long receiptId);
}
