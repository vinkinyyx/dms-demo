/*
 * 经销商画像服务：basic/kpi/achievement/rebate/contracts/inventory 六 tab。
 */
package com.dms.report.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.util.TenantContext;
import com.dms.masterdata.entity.Dealer;
import com.dms.masterdata.repository.DealerRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DealerProfileService {

    private final DealerRepository dealerRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public Map<String, Object> getBasic(Long dealerId) {
        Dealer d = dealerRepository.findById(dealerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "经销商不存在"));
        Map<String, Object> m = new HashMap<>();
        m.put("id", d.getId());
        m.put("code", d.getCode());
        m.put("name", d.getName());
        m.put("level", d.getLevel());
        m.put("legalPerson", d.getLegalPerson());
        m.put("uscNo", d.getUscNo());
        m.put("regionId", d.getRegionId());
        m.put("gspStatus", d.getGspStatus());
        m.put("gspExpire", d.getGspExpire());
        m.put("status", d.getStatus());
        m.put("contactName", d.getContactName());
        m.put("contactPhone", d.getContactPhone());
        m.put("contactEmail", d.getContactEmail());
        return m;
    }

    /** 关键 KPI：订单数、销售金额、库存量、退货金额。 */
    @Transactional(readOnly = true)
    public Map<String, Object> getKpi(Long dealerId) {
        UUID tenantId = TenantContext.getTenantId();
        Map<String, Object> res = new HashMap<>();
        res.put("orderCount", scalar(
                "SELECT COUNT(*) FROM orders WHERE deleted_at IS NULL AND tenant_id=:t AND dealer_id=:d",
                tenantId, dealerId));
        res.put("salesAmount", scalar(
                "SELECT COALESCE(SUM(amount),0) FROM sales_out_facts WHERE tenant_id=:t AND dealer_id=:d",
                tenantId, dealerId));
        res.put("inventoryQty", scalar(
                "SELECT COALESCE(SUM(qty),0) FROM inventory WHERE tenant_id=:t AND dealer_id=:d",
                tenantId, dealerId));
        res.put("returnAmount", scalar(
                "SELECT COALESCE(SUM(amount),0) FROM rma_orders WHERE deleted_at IS NULL AND tenant_id=:t AND dealer_id=:d",
                tenantId, dealerId));
        return res;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAchievement(Long dealerId) {
        UUID tenantId = TenantContext.getTenantId();
        return list("SELECT period_yyyymm AS period, target_amount, actual_amount, achievement_rate " +
                        "  FROM rebate_previews WHERE tenant_id=:t AND dealer_id=:d " +
                        "  ORDER BY period_yyyymm",
                tenantId, dealerId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getRebate(Long dealerId) {
        UUID tenantId = TenantContext.getTenantId();
        return list("SELECT period_yyyymm AS period, gross_rebate, net_rebate, deductions " +
                        "  FROM rebate_previews WHERE tenant_id=:t AND dealer_id=:d " +
                        "  ORDER BY period_yyyymm",
                tenantId, dealerId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getContracts(Long dealerId) {
        UUID tenantId = TenantContext.getTenantId();
        return list("SELECT id, code, category, valid_from, valid_to, status " +
                        "  FROM contracts WHERE deleted_at IS NULL AND tenant_id=:t AND dealer_id=:d " +
                        "  ORDER BY valid_from DESC",
                tenantId, dealerId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getInventory(Long dealerId) {
        UUID tenantId = TenantContext.getTenantId();
        return list("SELECT product_id, batch_no, SUM(qty) AS qty " +
                        "  FROM inventory WHERE tenant_id=:t AND dealer_id=:d " +
                        "  GROUP BY product_id, batch_no ORDER BY product_id",
                tenantId, dealerId);
    }

    private Object scalar(String sql, UUID tenantId, Long dealerId) {
        Query q = entityManager.createNativeQuery(sql);
        q.setParameter("t", tenantId);
        q.setParameter("d", dealerId);
        return q.getSingleResult();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> list(String sql, UUID tenantId, Long dealerId) {
        Query q = entityManager.createNativeQuery(sql, Tuple.class);
        q.setParameter("t", tenantId);
        q.setParameter("d", dealerId);
        List<Tuple> tuples = q.getResultList();
        List<Map<String, Object>> rows = new ArrayList<>(tuples.size());
        for (Tuple t : tuples) {
            Map<String, Object> row = new HashMap<>();
            t.getElements().forEach(e -> row.put(e.getAlias(), t.get(e.getAlias())));
            rows.add(row);
        }
        return rows;
    }
}
