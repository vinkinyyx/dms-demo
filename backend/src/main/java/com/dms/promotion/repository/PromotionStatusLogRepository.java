/*
 * 促销状态日志仓储接口。
 */
package com.dms.promotion.repository;

import com.dms.promotion.entity.PromotionStatusLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PromotionStatusLogRepository extends JpaRepository<PromotionStatusLog, Long> {
}
