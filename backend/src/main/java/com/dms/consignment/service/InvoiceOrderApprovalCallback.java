package com.dms.consignment.service;

import com.dms.approval.entity.ApprovalInstance;
import com.dms.approval.service.ApprovalBusinessCallback;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * v4.4.0 开票订单（order_type=INVOICE）审批回调，业务类型 INVOICE_ORDER。
 *  - 提交：预占寄售库存（在 V4OrderService.submit 中调用 lockForInvoice）。
 *  - 审批通过：正式扣减 on_hand / locked（deductForInvoice）。
 *  - 驳回/退回/撤回：释放预占 locked（releaseForInvoice）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InvoiceOrderApprovalCallback implements ApprovalBusinessCallback {
    public static final String BUSINESS_TYPE = "INVOICE_ORDER";
    private final EntityManager em;
    @Lazy
    private final ConsignmentService consignmentService;

    @Override public boolean supports(String businessType) { return BUSINESS_TYPE.equals(businessType); }

    private Long dealerId(Long orderId) {
        List<Tuple> r = em.createNativeQuery("SELECT dealer_id FROM orders WHERE id=?1", Tuple.class)
                .setParameter(1, orderId).getResultList();
        if (r.isEmpty()) return null;
        Object v = r.get(0).get("dealer_id");
        return v == null ? null : ((Number) v).longValue();
    }

    private String orderCode(Long orderId) {
        List<?> r = em.createNativeQuery("SELECT code FROM orders WHERE id=?1").setParameter(1, orderId).getResultList();
        return r.isEmpty() ? ("#"+orderId) : String.valueOf(r.get(0));
    }

    private List<ConsignmentService.StdLine> lines(Long orderId) {
        List<Tuple> rows = em.createNativeQuery(
                "SELECT product_id, batch_no, serial_no, qty, consignment_stock_id FROM order_lines WHERE order_id=?1 ORDER BY seq,id",
                Tuple.class).setParameter(1, orderId).getResultList();
        List<ConsignmentService.StdLine> out = new ArrayList<>();
        for (Tuple t : rows) {
            Object pid = t.get("product_id"); Object q = t.get("qty");
            if (pid == null || q == null) continue;
            Object sid = t.get("consignment_stock_id");
            out.add(new ConsignmentService.StdLine(
                    ((Number) pid).longValue(),
                    t.get("batch_no") == null ? null : String.valueOf(t.get("batch_no")),
                    t.get("serial_no") == null ? null : String.valueOf(t.get("serial_no")),
                    null,
                    new BigDecimal(String.valueOf(q)),
                    sid == null ? null : ((Number) sid).longValue()));
        }
        return out;
    }

    @Override
    public void onApproved(ApprovalInstance in) {
        Long dealer = dealerId(in.getBusinessId());
        if (dealer == null) return;
        consignmentService.deductForInvoice(dealer, in.getBusinessId(), orderCode(in.getBusinessId()), lines(in.getBusinessId()));
    }

    @Override
    public void onRejected(ApprovalInstance in) {
        Long dealer = dealerId(in.getBusinessId());
        if (dealer == null) return;
        consignmentService.releaseForInvoice(dealer, in.getBusinessId(), orderCode(in.getBusinessId()), lines(in.getBusinessId()));
    }

    @Override
    public void onReturned(ApprovalInstance in) { onRejected(in); }

    @Override
    public void onWithdrawn(ApprovalInstance in) { onRejected(in); }

    @Override
    public void onTerminated(ApprovalInstance in, String result) {
        // 无论终止为草稿还是取消，都释放预占
        Long dealer = dealerId(in.getBusinessId());
        if (dealer != null) consignmentService.releaseForInvoice(dealer, in.getBusinessId(), orderCode(in.getBusinessId()), lines(in.getBusinessId()));
    }
}
