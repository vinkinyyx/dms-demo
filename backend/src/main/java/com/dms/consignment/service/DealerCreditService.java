package com.dms.consignment.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.util.TenantContext;
import com.dms.common.PageResult;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * v4.4.0 经销商资信与账期。
 * 字段维护在 dealers（credit_limit/payment_days/settlement_method/credit_grade/consignment_enabled/consignment_limit），
 * dealer_credit_profiles 保存运行期占用（credit_used=应收/在途、consignment_used=寄售金额按标准价汇总）。
 * 超额/超账期不硬拦截，触发审批（由订单审批流承接）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DealerCreditService {

    private final EntityManager em;

    private UUID tid() {
        UUID t = TenantContext.getTenantId();
        if (t == null) throw new BusinessException(ErrorCode.UNAUTHORIZED, "缺少租户上下文");
        return t;
    }

    @Transactional(readOnly = true)
    public PageResult<Map<String, Object>> page(int page, int size, String keyword) {
        UUID tid = tid();
        StringBuilder where = new StringBuilder(" WHERE d.tenant_id=?1 AND d.deleted_at IS NULL ");
        List<Object> p = new ArrayList<>(); p.add(tid);
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (d.name ILIKE ?2 OR d.code ILIKE ?2) ");
            p.add("%" + keyword + "%");
        }
        jakarta.persistence.Query cq = em.createNativeQuery(
            "SELECT COUNT(*) FROM dealers d " + where);
        for (int i=0;i<p.size();i++) cq.setParameter(i+1, p.get(i));
        long total = ((Number) cq.getSingleResult()).longValue();

        String sql =
            "SELECT d.id, d.code, d.name, d.level, " +
            "COALESCE(d.credit_limit,0) AS credit_limit, COALESCE(cp.credit_used,0) AS credit_used, " +
            "COALESCE(d.payment_days,0) AS payment_days, d.settlement_method, d.credit_grade, " +
            "COALESCE(d.consignment_enabled,false) AS consignment_enabled, " +
            "COALESCE(d.consignment_limit,0) AS consignment_limit, COALESCE(cp.consignment_used,0) AS consignment_used, " +
            "COALESCE(cp.status,'ACTIVE') AS status " +
            "FROM dealers d LEFT JOIN dealer_credit_profiles cp ON cp.dealer_id=d.id AND cp.deleted_at IS NULL AND cp.tenant_id=d.tenant_id " +
            where +
            " ORDER BY d.id DESC LIMIT ?" + (p.size()+1) + " OFFSET ?" + (p.size()+2);
        jakarta.persistence.Query lq = em.createNativeQuery(sql, Tuple.class);
        for (int i=0;i<p.size();i++) lq.setParameter(i+1, p.get(i));
        lq.setParameter(p.size()+1, size);
        lq.setParameter(p.size()+2, (page-1)*size);
        @SuppressWarnings("unchecked")
        List<Tuple> rows = lq.getResultList();
        List<Map<String,Object>> list = new ArrayList<>();
        for (Tuple t : rows) {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("dealerId", t.get("id"));
            m.put("code", t.get("code"));
            m.put("name", t.get("name"));
            m.put("level", t.get("level"));
            m.put("creditLimit", t.get("credit_limit"));
            m.put("creditUsed", t.get("credit_used"));
            BigDecimal cl = new BigDecimal(String.valueOf(t.get("credit_limit")));
            BigDecimal cu = new BigDecimal(String.valueOf(t.get("credit_used")));
            m.put("creditAvailable", cl.subtract(cu).max(BigDecimal.ZERO));
            m.put("creditOver", cu.compareTo(cl) > 0 && cl.signum() > 0);
            m.put("paymentDays", t.get("payment_days"));
            m.put("settlementMethod", t.get("settlement_method"));
            m.put("creditGrade", t.get("credit_grade"));
            m.put("consignmentEnabled", t.get("consignment_enabled"));
            m.put("consignmentLimit", t.get("consignment_limit"));
            m.put("consignmentUsed", t.get("consignment_used"));
            BigDecimal sl = new BigDecimal(String.valueOf(t.get("consignment_limit")));
            BigDecimal su = new BigDecimal(String.valueOf(t.get("consignment_used")));
            m.put("consignmentOver", su.compareTo(sl) > 0 && sl.signum() > 0);
            m.put("status", t.get("status"));
            list.add(m);
        }
        return new PageResult<>(total, page, size, list);
    }
}
