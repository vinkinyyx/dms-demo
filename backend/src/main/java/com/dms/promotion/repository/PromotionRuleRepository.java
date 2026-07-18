/*
 * 促销规则仓储接口。
 */
package com.dms.promotion.repository;

import com.dms.promotion.entity.PromotionRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PromotionRuleRepository extends JpaRepository<PromotionRule, Long> {
    List<PromotionRule> findByPromotionIdOrderBySeqAsc(Long promotionId);
}
