package com.dms.approval.service;

import com.dms.approval.entity.ApprovalInstance;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;

import java.math.BigDecimal;
import java.util.*;

public final class ApprovalSummaryBuilder {
    private ApprovalSummaryBuilder() {}

    public static Map<String, Object> build(EntityManager em, ApprovalInstance instance) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("businessType", instance.getBusinessType());
        result.put("businessId", instance.getBusinessId());
        result.put("businessCode", instance.getBusinessCode());
        Map<String, Object> snapshot = instance.getBusinessSnapshot() == null ? Map.of() : instance.getBusinessSnapshot();
        result.put("snapshot", snapshot);
        try {
            switch (instance.getBusinessType()) {
                case "SALES_ORDER" -> fillSalesOrder(em, result, instance);
                case "PURCHASE_ORDER" -> fillPurchaseOrder(em, result, instance);
                case "SALES_RETURN" -> fillSalesReturn(em, result, instance);
                case "PURCHASE_RETURN" -> fillPurchaseReturn(em, result, instance);
                case "CONTRACT" -> fillContract(em, result, instance);
                case "AUTHORIZATION" -> fillAuthorization(em, result, instance);
                case "RMA_ORDER" -> fillRmaOrder(em, result, instance);
                case "PRODUCT_CREATE" -> fillProductCreate(em, result, instance);
                case "DEALER_CREATE" -> fillDealerCreate(em, result, instance);
                case "SUPPLIER_CREATE" -> fillSupplierCreate(em, result, instance);
                default -> result.put("items", List.of());
            }
        } catch (Exception ex) {
            result.put("warning", "单据摘要加载失败: " + ex.getMessage());
            result.put("items", List.of());
        }
        return result;
    }

    private static void fillSalesOrder(EntityManager em, Map<String, Object> result, ApprovalInstance in) {
        List<Tuple> rs = em.createNativeQuery(
                "select o.*, d.name as party_name, w.name as warehouse_name from orders o " +
                "left join dealers d on d.id = o.dealer_id " +
                "left join warehouses w on w.id = o.warehouse_id " +
                "where o.id = ?1 and o.tenant_id = ?2", Tuple.class)
                .setParameter(1, in.getBusinessId()).setParameter(2, in.getTenantId()).getResultList();
        if (rs.isEmpty()) return;
        Tuple o = rs.get(0);
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("单号", val(o, "code"));
        header.put("经销商", val(o, "party_name"));
        header.put("仓库", val(o, "warehouse_name"));
        header.put("订单类型", val(o, "order_type"));
        header.put("金额", amount(o, "final_amount"));
        header.put("期望日期", val(o, "expected_date"));
        header.put("备注", val(o, "remark"));
        result.put("header", header);
        result.put("items", salesOrderItems(em, in));
    }

    private static List<Map<String, Object>> salesOrderItems(EntityManager em, ApprovalInstance in) {
        List<Tuple> rows = em.createNativeQuery(
                "select l.*, p.name_cn product_name, p.code product_code from order_lines l " +
                "left join products p on p.id = l.product_id " +
                "where l.order_id = ?1 order by l.seq, l.id", Tuple.class)
                .setParameter(1, in.getBusinessId()).getResultList();
        List<Map<String, Object>> items = new ArrayList<>();
        for (Tuple r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("productCode", val(r, "product_code"));
            m.put("productName", val(r, "product_name"));
            m.put("batchNo", val(r, "batch_no"));
            m.put("qty", val(r, "qty"));
            m.put("unitPrice", amount(r, "unit_price"));
            m.put("subtotal", amount(r, "sub_total"));
            items.add(m);
        }
        return items;
    }

    private static void fillPurchaseOrder(EntityManager em, Map<String, Object> result, ApprovalInstance in) {
        List<Tuple> rs = em.createNativeQuery(
                "select o.*, s.name as party_name, w.name as warehouse_name from purchase_orders o " +
                "left join lateral (select string_agg(name, ',') as name from suppliers where id = o.supplier_id) s on true " +
                "left join warehouses w on w.id = o.warehouse_id " +
                "where o.id = ?1 and o.tenant_id = ?2", Tuple.class)
                .setParameter(1, in.getBusinessId()).setParameter(2, in.getTenantId()).getResultList();
        if (rs.isEmpty()) return;
        Tuple o = rs.get(0);
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("单号", val(o, "code"));
        header.put("供应商", val(o, "party_name"));
        header.put("仓库", val(o, "warehouse_name"));
        header.put("订单类型", val(o, "order_type"));
        header.put("金额", amount(o, "final_amount"));
        header.put("期望日期", val(o, "expected_date"));
        header.put("备注", val(o, "remark"));
        result.put("header", header);
        result.put("items", purchaseOrderItems(em, in));
    }

    private static List<Map<String, Object>> purchaseOrderItems(EntityManager em, ApprovalInstance in) {
        List<Tuple> rows = em.createNativeQuery(
                "select l.*, p.name_cn product_name, p.code product_code from purchase_order_lines l " +
                "left join products p on p.id = l.product_id " +
                "where l.po_id = ?1 order by l.seq, l.id", Tuple.class)
                .setParameter(1, in.getBusinessId()).getResultList();
        List<Map<String, Object>> items = new ArrayList<>();
        for (Tuple r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("productCode", val(r, "product_code"));
            m.put("productName", val(r, "product_name"));
            m.put("qty", val(r, "qty"));
            m.put("unitPrice", amount(r, "unit_price"));
            m.put("subtotal", amount(r, "subtotal"));
            items.add(m);
        }
        return items;
    }

    private static void fillSalesReturn(EntityManager em, Map<String, Object> result, ApprovalInstance in) {
        List<Tuple> rs = em.createNativeQuery(
                "select o.*, d.name as party_name, w.name as warehouse_name from orders o " +
                "left join dealers d on d.id = o.dealer_id " +
                "left join warehouses w on w.id = o.warehouse_id " +
                "where o.id = ?1 and o.tenant_id = ?2 and coalesce(o.is_red, false) = true", Tuple.class)
                .setParameter(1, in.getBusinessId()).setParameter(2, in.getTenantId()).getResultList();
        if (rs.isEmpty()) {
            fallbackHeader(result, in);
            return;
        }
        Tuple o = rs.get(0);
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("单号", val(o, "code"));
        header.put("经销商", val(o, "party_name"));
        header.put("仓库", val(o, "warehouse_name"));
        header.put("金额", amount(o, "final_amount"));
        header.put("退货原因", val(o, "return_reason"));
        header.put("备注", val(o, "remark"));
        result.put("header", header);
        result.put("items", salesOrderItems(em, in));
    }

    private static void fillPurchaseReturn(EntityManager em, Map<String, Object> result, ApprovalInstance in) {
        List<Tuple> rs = em.createNativeQuery(
                "select o.*, s.name as party_name, w.name as warehouse_name from purchase_orders o " +
                "left join lateral (select string_agg(name, ',') as name from suppliers where id = o.supplier_id) s on true " +
                "left join warehouses w on w.id = o.warehouse_id " +
                "where o.id = ?1 and o.tenant_id = ?2 and coalesce(o.is_red, false) = true", Tuple.class)
                .setParameter(1, in.getBusinessId()).setParameter(2, in.getTenantId()).getResultList();
        if (rs.isEmpty()) {
            fallbackHeader(result, in);
            return;
        }
        Tuple o = rs.get(0);
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("单号", val(o, "code"));
        header.put("供应商", val(o, "party_name"));
        header.put("仓库", val(o, "warehouse_name"));
        header.put("金额", amount(o, "final_amount"));
        header.put("退货原因", val(o, "return_reason"));
        header.put("备注", val(o, "remark"));
        result.put("header", header);
        result.put("items", purchaseOrderItems(em, in));
    }

    private static void fallbackHeader(Map<String, Object> result, ApprovalInstance in) {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("业务编号", in.getBusinessCode());
        header.put("业务ID", in.getBusinessId());
        result.put("header", header);
        result.put("items", List.of());
    }

    private static void fillRmaOrder(EntityManager em, Map<String, Object> result, ApprovalInstance in) {
        List<Tuple> rs = em.createNativeQuery(
                "select r.*, d.name as dealer_name from rma_orders r " +
                "left join dealers d on d.id = r.dealer_id " +
                "where r.id = ?1 and r.tenant_id = ?2 and r.deleted_at is null", Tuple.class)
                .setParameter(1, in.getBusinessId()).setParameter(2, in.getTenantId()).getResultList();
        if (rs.isEmpty()) { fallbackHeader(result, in); return; }
        Tuple r = rs.get(0);
        String outCodes;
        try {
            @SuppressWarnings("unchecked")
            List<String> codes = em.createNativeQuery(
                    "select sales_out_code from rma_order_refs where rma_id = ?1 order by id")
                    .setParameter(1, in.getBusinessId()).getResultList();
            outCodes = codes.isEmpty() ? "-" : String.join(", ", codes);
        } catch (Exception e) {
            outCodes = "-";
        }
        String rmaType = val(r, "rma_type") == null ? "" : String.valueOf(val(r, "rma_type"));
        String typeLabel = switch (rmaType) {
            case "ZERO_RETURN" -> "0金额产品退货";
            case "RETURN" -> "有价产品退货";
            case "QUALITY" -> "质量退货";
            default -> rmaType.isBlank() ? "-" : rmaType;
        };
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("销退单号", val(r, "code"));
        header.put("经销商", val(r, "dealer_name"));
        header.put("退货类型", typeLabel);
        header.put("退货原因", val(r, "reason"));
        header.put("关联出库单", outCodes);
        header.put("退货总数量", val(r, "total_qty"));
        header.put("退货金额", amount(r, "amount"));
        result.put("header", header);

        List<Tuple> rows = em.createNativeQuery(
                "select l.* from rma_order_lines l where l.rma_id = ?1 order by l.seq, l.id", Tuple.class)
                .setParameter(1, in.getBusinessId()).getResultList();
        List<Map<String, Object>> items = new ArrayList<>();
        for (Tuple l : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("productCode", val(l, "product_code"));
            m.put("productName", val(l, "product_name"));
            String batch = val(l, "batch_no") == null ? "" : String.valueOf(val(l, "batch_no"));
            String serial = val(l, "serial_no") == null ? "" : String.valueOf(val(l, "serial_no"));
            String bs = (batch + (serial.isBlank() ? "" : (batch.isBlank() ? "" : "/") + serial)).trim();
            m.put("batchNo", bs.isBlank() ? "-" : bs);
            m.put("qty", val(l, "qty"));
            m.put("unitPrice", amount(l, "unit_price_incl_tax"));
            m.put("subtotal", amount(l, "sub_total"));
            items.add(m);
        }
        result.put("items", items);
    }

    private static void fillContract(EntityManager em, Map<String, Object> result, ApprovalInstance in) {
        List<Tuple> rs = em.createNativeQuery(
                "select c.*, d.name as dealer_name from contracts c " +
                "left join dealers d on d.id = c.dealer_id " +
                "where c.id = ?1 and c.tenant_id = ?2", Tuple.class)
                .setParameter(1, in.getBusinessId()).setParameter(2, in.getTenantId()).getResultList();
        if (rs.isEmpty()) return;
        Tuple c = rs.get(0);
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("合同编号", val(c, "code"));
        header.put("合同名称", val(c, "name"));
        header.put("经销商", val(c, "dealer_name"));
        header.put("甲方", val(c, "vendor_party"));
        header.put("乙方", val(c, "dealer_party"));
        header.put("金额", amount(c, "signed_amount"));
        Object vf = val(c, "valid_from");
        Object vt = val(c, "valid_to");
        header.put("有效期", (vf == null ? "" : vf) + " ~ " + (vt == null ? "" : vt));
        result.put("header", header);
        result.put("items", List.of());
    }

    private static void fillProductCreate(EntityManager em, Map<String, Object> result, ApprovalInstance in) {
        List<Tuple> rs = em.createNativeQuery(
                "select code, name_cn as name, status from products " +
                "where id = ?1 and tenant_id = ?2", Tuple.class)
                .setParameter(1, in.getBusinessId()).setParameter(2, in.getTenantId()).getResultList();
        if (rs.isEmpty()) { fallbackHeader(result, in); return; }
        Tuple p = rs.get(0);
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("编码", val(p, "code"));
        header.put("名称", val(p, "name"));
        header.put("状态", val(p, "status"));
        result.put("header", header);
        result.put("items", List.of());
    }

    private static void fillDealerCreate(EntityManager em, Map<String, Object> result, ApprovalInstance in) {
        List<Tuple> rs = em.createNativeQuery(
                "select code, name, status from dealers " +
                "where id = ?1 and tenant_id = ?2", Tuple.class)
                .setParameter(1, in.getBusinessId()).setParameter(2, in.getTenantId()).getResultList();
        if (rs.isEmpty()) { fallbackHeader(result, in); return; }
        Tuple d = rs.get(0);
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("编码", val(d, "code"));
        header.put("名称", val(d, "name"));
        header.put("状态", val(d, "status"));
        result.put("header", header);
        result.put("items", List.of());
    }

    private static void fillSupplierCreate(EntityManager em, Map<String, Object> result, ApprovalInstance in) {
        List<Tuple> rs = em.createNativeQuery(
                "select code, name, status from suppliers " +
                "where id = ?1 and tenant_id = ?2", Tuple.class)
                .setParameter(1, in.getBusinessId()).setParameter(2, in.getTenantId()).getResultList();
        if (rs.isEmpty()) { fallbackHeader(result, in); return; }
        Tuple s = rs.get(0);
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("编码", val(s, "code"));
        header.put("名称", val(s, "name"));
        header.put("状态", val(s, "status"));
        result.put("header", header);
        result.put("items", List.of());
    }

    private static void fillAuthorization(EntityManager em, Map<String, Object> result, ApprovalInstance in) {
        List<Tuple> rs = em.createNativeQuery(
                "select a.*, d.name as dealer_name, p.name_cn as product_name, " +
                "pl.name as product_line_name " +
                "from authorizations a " +
                "left join dealers d on d.id = a.dealer_id " +
                "left join products p on p.id = a.product_id " +
                "left join product_lines pl on pl.id = a.product_line_id " +
                "where a.id = ?1 and a.tenant_id = ?2", Tuple.class)
                .setParameter(1, in.getBusinessId()).setParameter(2, in.getTenantId()).getResultList();
        if (rs.isEmpty()) return;
        Tuple a = rs.get(0);
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("授权编号", in.getBusinessCode());
        header.put("授权类型", val(a, "auth_type"));
        header.put("经销商", val(a, "dealer_name"));
        header.put("产品线", val(a, "product_line_name"));
        header.put("产品", val(a, "product_name"));
        Object vf = val(a, "valid_from");
        Object vt = val(a, "valid_to");
        header.put("有效期", (vf == null ? "" : vf) + " ~ " + (vt == null ? "" : vt));
        header.put("状态", val(a, "status"));
        String terminalNames = terminalNames(em, in.getTenantId(), val(a, "terminal_ids"));
        if (terminalNames != null && !terminalNames.isBlank()) {
            header.put("授权医院", terminalNames);
        }
        result.put("header", header);
        result.put("items", List.of());
    }

    private static String terminalNames(EntityManager em, UUID tenantId, Object csv) {
        if (csv == null) return null;
        String s = String.valueOf(csv).trim();
        if (s.isEmpty()) return null;
        List<Long> ids = new ArrayList<>();
        for (String part : s.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) {
                try { ids.add(Long.parseLong(t)); } catch (NumberFormatException ignored) {}
            }
        }
        if (ids.isEmpty()) return null;
        try {
            @SuppressWarnings("unchecked")
            List<String> names = em.createNativeQuery(
                    "select name from hospitals where tenant_id = ?1 and id in (:ids)")
                    .setParameter(1, tenantId)
                    .setParameter("ids", ids)
                    .getResultList();
            return String.join(", ", names);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Object val(Tuple t, String key) {
        try {
            Object v = t.get(key);
            return v;
        } catch (Exception e) {
            return null;
        }
    }

    private static Object amount(Tuple t, String key) {
        Object v = val(t, key);
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal bd) return bd.stripTrailingZeros().toPlainString();
        return v;
    }
}
