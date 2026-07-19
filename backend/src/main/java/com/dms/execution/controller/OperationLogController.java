/*
 * v3.4.11 单据操作日志查询：详情页时间轴
 * GET /api/operation-logs?resourceType=xxx&resourceId=nnn
 */
package com.dms.execution.controller;

import com.dms.common.ApiResponse;
import com.dms.common.util.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequiredArgsConstructor
public class OperationLogController {

    private final EntityManager em;

    private static final Map<String, String> ACTION_LABELS = new HashMap<>();
    static {
        ACTION_LABELS.put("CREATE", "创建单据");
        ACTION_LABELS.put("SUBMIT", "提交");
        ACTION_LABELS.put("PO_SUBMIT", "提交采购单");
        ACTION_LABELS.put("APPROVE", "审批通过");
        ACTION_LABELS.put("PO_APPROVE", "审批通过");
        ACTION_LABELS.put("REJECT", "驳回");
        ACTION_LABELS.put("PO_REJECT", "驳回");
        ACTION_LABELS.put("CANCEL", "取消");
        ACTION_LABELS.put("PO_CANCEL", "取消");
        ACTION_LABELS.put("EXECUTE", "执行");
        ACTION_LABELS.put("RECEIPT", "收货入库");
        ACTION_LABELS.put("RECEIPT_PARTIAL", "部分收货");
        ACTION_LABELS.put("SALES_OUT", "销售出库");
        ACTION_LABELS.put("SALES_OUT_PARTIAL", "部分发货");
    }

    @GetMapping("/api/operation-logs")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<List<Map<String, Object>>> list(
            @RequestParam String resourceType,
            @RequestParam Long resourceId) {
        UUID tid = TenantContext.getTenantId();
        var q = em.createNativeQuery(
                "SELECT a.id, a.action, a.resource_type, a.resource_id, a.user_id, a.after, a.at_time, " +
                "u.name AS user_name, u.username " +
                "FROM audit_logs a LEFT JOIN users u ON u.id = a.user_id " +
                "WHERE a.tenant_id = ?1 AND a.resource_type = ?2 AND a.resource_id = ?3 " +
                "ORDER BY a.at_time ASC, a.id ASC", Tuple.class);
        q.setParameter(1, tid).setParameter(2, resourceType).setParameter(3, String.valueOf(resourceId));
        @SuppressWarnings("unchecked")
        List<Tuple> rows = q.getResultList();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Tuple t : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            String action = String.valueOf(t.get("action"));
            m.put("id", t.get("id"));
            m.put("action", action);
            m.put("actionLabel", ACTION_LABELS.getOrDefault(action, action));
            m.put("userId", t.get("user_id"));
            m.put("userName", t.get("user_name") != null ? t.get("user_name") : t.get("username"));
            m.put("detail", t.get("after"));
            m.put("atTime", com.dms.common.util.DateFmt.fmt(t.get("at_time")));
            out.add(m);
        }
        return ApiResponse.ok(out);
    }
}
