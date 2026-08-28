package com.dms.consignment.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.util.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * v4.4.0 寄售库存服务。
 * 补货订单(REPLENISHMENT)发货后：厂家库存已由销售出库扣减，此处把这批货计入经销商寄售台账(on_hand+)。
 * 开票订单(INVOICE)：提交即预占(locked+)，审批通过实扣(on_hand-/locked-)，拒绝/退回/撤回释放(locked-)。
 * 维度：经销商 + 产品SKU + 批号 + 序列号。寄售金额按产品标准价(std_unit_price)汇总。
 * v4.4.1：开票明细携带 consignment_stock_id(stockId) 后，锁定/扣减/释放按台账行精准定位。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConsignmentService {

    private final EntityManager em;

    private UUID tid() {
        UUID t = TenantContext.getTenantId();
        if (t == null) throw new BusinessException(ErrorCode.UNAUTHORIZED, "缺少租户上下文");
        return t;
    }

    public record StdLine(Long productId, String batchNo, String serialNo, Long warehouseId, BigDecimal qty, Long stockId) {
        public StdLine(Long productId, String batchNo, String serialNo, Long warehouseId, BigDecimal qty) {
            this(productId, batchNo, serialNo, warehouseId, qty, null);
        }
    }

    private String col(Long productId, String field) {
        try {
            Object v = em.createNativeQuery("SELECT " + field + " FROM products WHERE id=?1")
                    .setParameter(1, productId).getSingleResult();
            return v == null ? null : String.valueOf(v);
        } catch (Exception e) { return null; }
    }
    private String productCode(Long id){ return col(id, "code"); }
    private String productName(Long id){
        try { Object v = em.createNativeQuery("SELECT COALESCE(name_cn,'') FROM products WHERE id=?1")
                .setParameter(1,id).getSingleResult(); return v==null?null:String.valueOf(v);}catch(Exception e){return null;} }
    private String productSpec(Long id){ return col(id, "spec"); }

    private BigDecimal stdPrice(UUID tid, Long productId) {
        try {
            List<?> r = em.createNativeQuery(
                "SELECT sales_price FROM product_prices WHERE tenant_id=?1 AND product_id=?2 AND price_scope='SALE' " +
                "AND (partner_id IS NULL OR partner_id=0) AND (valid_from IS NULL OR valid_from<=now()) " +
                "AND (valid_to IS NULL OR valid_to>=now()) ORDER BY valid_from DESC NULLS LAST LIMIT 1")
                .setParameter(1, tid).setParameter(2, productId).getResultList();
            if (!r.isEmpty() && r.get(0) != null && new BigDecimal(r.get(0).toString()).signum() > 0)
                return new BigDecimal(r.get(0).toString());
        } catch (Exception ignored) {}
        for (String f : new String[]{"standard_price_incl_tax","standard_price","price","sale_price"}) {
            try {
                Object v = em.createNativeQuery("SELECT COALESCE(" + f + ",0) FROM products WHERE id=?1 AND tenant_id=?2")
                        .setParameter(1, productId).setParameter(2, tid).getSingleResult();
                if (v != null && new BigDecimal(v.toString()).signum() > 0) return new BigDecimal(v.toString());
            } catch (Exception ignored) {}
        }
        return BigDecimal.ZERO;
    }

    private String bn(String s){ return (s==null||s.isBlank())?null:s; }

    private void upsertStock(UUID tid, Long dealerId, Long productId, String batchNo, String serialNo,
                             Long warehouseId, int onHandDelta, int lockedDelta, BigDecimal stdUnitPrice, Long sourceOutId) {
        List<Tuple> exist = em.createNativeQuery(
            "SELECT id, on_hand_qty, locked_qty, COALESCE(std_unit_price,0) AS price FROM consignment_stock " +
            "WHERE tenant_id=?1 AND dealer_id=?2 AND product_id=?3 AND COALESCE(batch_no,'')=COALESCE(?4,'') AND COALESCE(serial_no,'')=COALESCE(?5,'')",
            Tuple.class)
            .setParameter(1,tid).setParameter(2,dealerId).setParameter(3,productId)
            .setParameter(4,bn(batchNo)).setParameter(5,bn(serialNo)).getResultList();
        if (exist.isEmpty()) {
            em.createNativeQuery(
                "INSERT INTO consignment_stock (tenant_id,dealer_id,product_id,product_code,product_name,product_spec,unit," +
                "batch_no,serial_no,warehouse_id,on_hand_qty,locked_qty,std_unit_price,source_sales_out_id,created_at,updated_at,version) " +
                "VALUES (?1,?2,?3,?4,?5,?6,'件',?7,?8,?9,GREATEST(?10,0),GREATEST(?11,0),?12,?13,now(),now(),0)")
                .setParameter(1,tid).setParameter(2,dealerId).setParameter(3,productId)
                .setParameter(4,productCode(productId)).setParameter(5,productName(productId)).setParameter(6,productSpec(productId))
                .setParameter(7,bn(batchNo)).setParameter(8,bn(serialNo)).setParameter(9,warehouseId)
                .setParameter(10,onHandDelta).setParameter(11,lockedDelta)
                .setParameter(12,stdUnitPrice==null?BigDecimal.ZERO:stdUnitPrice).setParameter(13,sourceOutId)
                .executeUpdate();
        } else {
            Tuple row = exist.get(0);
            Long id = ((Number) row.get("id")).longValue();
            int onHand = ((Number) row.get("on_hand_qty")).intValue() + onHandDelta;
            int locked = ((Number) row.get("locked_qty")).intValue() + lockedDelta;
            BigDecimal cur = new BigDecimal(String.valueOf(row.get("price")));
            BigDecimal use = (stdUnitPrice!=null && stdUnitPrice.signum()>0)?stdUnitPrice:cur;
            em.createNativeQuery(
                "UPDATE consignment_stock SET on_hand_qty=GREATEST(?2,0), locked_qty=GREATEST(?3,0), std_unit_price=?4, " +
                "warehouse_id=COALESCE(?5,warehouse_id), product_code=COALESCE(?6,product_code), product_name=COALESCE(?7,product_name), " +
                "updated_at=now(), version=version+1 WHERE id=?1")
                .setParameter(1,id).setParameter(2,onHand).setParameter(3,locked).setParameter(4,use)
                .setParameter(5,warehouseId).setParameter(6,productCode(productId)).setParameter(7,productName(productId))
                .executeUpdate();
        }
    }

    private void writeMovement(UUID tid, Long dealerId, Long productId, String batchNo, String serialNo,
                               String type, int qtyChange, String refType, Long refId, String refCode, String remark) {
        em.createNativeQuery(
            "INSERT INTO consignment_stock_movements (tenant_id,dealer_id,product_id,batch_no,serial_no,change_type," +
            "qty_change,ref_type,ref_id,ref_code,remark,created_at,created_by) VALUES (?1,?2,?3,?4,?5,?6,?7,?8,?9,?10,?11,now(),?12)")
            .setParameter(1,tid).setParameter(2,dealerId).setParameter(3,productId).setParameter(4,bn(batchNo)).setParameter(5,bn(serialNo))
            .setParameter(6,type).setParameter(7,qtyChange).setParameter(8,refType).setParameter(9,refId).setParameter(10,refCode)
            .setParameter(11,remark).setParameter(12,TenantContext.getUserId()).executeUpdate();
    }

    @Transactional
    public void onReplenishShipped(Long dealerId, Long salesOutId, List<StdLine> lines) {
        if (dealerId==null || lines==null || lines.isEmpty()) return;
        UUID tid = tid();
        for (StdLine l : lines) {
            if (l.productId()==null || l.qty()==null || l.qty().signum()<=0) continue;
            upsertStock(tid, dealerId, l.productId(), l.batchNo(), l.serialNo(), l.warehouseId(),
                    l.qty().intValue(), 0, stdPrice(tid, l.productId()), salesOutId);
            writeMovement(tid, dealerId, l.productId(), l.batchNo(), l.serialNo(),
                    "REPLENISH_IN", l.qty().intValue(), "SALES_OUT", salesOutId, null, "补货发货入库");
        }
        recomputeConsignmentUsed(tid, dealerId);
        log.info("补货发货计入寄售库存 dealer={} out={} lines={}", dealerId, salesOutId, lines.size());
    }

    private int availableQty(UUID tid, Long dealerId, Long productId, String batchNo, String serialNo) {
        List<Tuple> rs = em.createNativeQuery(
            "SELECT COALESCE(on_hand_qty,0) oh, COALESCE(locked_qty,0) lk FROM consignment_stock " +
            "WHERE tenant_id=?1 AND dealer_id=?2 AND product_id=?3 AND COALESCE(batch_no,'')=COALESCE(?4,'') AND COALESCE(serial_no,'')=COALESCE(?5,'')",
            Tuple.class)
            .setParameter(1,tid).setParameter(2,dealerId).setParameter(3,productId).setParameter(4,bn(batchNo)).setParameter(5,bn(serialNo))
            .getResultList();
        if (rs.isEmpty()) return 0;
        return ((Number)rs.get(0).get("oh")).intValue() - ((Number)rs.get(0).get("lk")).intValue();
    }

    /** v4.4.1 按台账行ID定位（校验租户/经销商/产品一致）；找不到返回 null。 */
    private Tuple locateStockRow(UUID tid, Long dealerId, Long stockId, Long productId) {
        if (stockId == null) return null;
        List<Tuple> rs = em.createNativeQuery(
            "SELECT id, dealer_id, product_id, COALESCE(batch_no,'') batch_no, COALESCE(serial_no,'') serial_no, " +
            "on_hand_qty, locked_qty, COALESCE(std_unit_price,0) std_unit_price FROM consignment_stock WHERE id=?1 AND tenant_id=?2",
            Tuple.class).setParameter(1, stockId).setParameter(2, tid).getResultList();
        if (rs.isEmpty()) return null;
        Tuple row = rs.get(0);
        if (dealerId != null && !dealerId.equals(toLong(row.get("dealer_id"))))
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "所选寄售库存不属于当前经销商");
        if (productId != null && !productId.equals(toLong(row.get("product_id"))))
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "所选寄售库存与开票产品不一致");
        return row;
    }

    private Long toLong(Object o){ return o==null?null:((Number)o).longValue(); }

    /** v4.4.1 按台账行ID直接增减（onHandDelta/lockedDelta 可正可负，GREATEST 兜底防负）。 */
    private void adjustStockRow(Long stockId, int onHandDelta, int lockedDelta) {
        em.createNativeQuery(
            "UPDATE consignment_stock SET on_hand_qty=GREATEST(on_hand_qty+?2,0), locked_qty=GREATEST(locked_qty+?3,0), " +
            "updated_at=now(), version=version+1 WHERE id=?1")
            .setParameter(1, stockId).setParameter(2, onHandDelta).setParameter(3, lockedDelta).executeUpdate();
    }

    @Transactional
    public void lockForInvoice(Long dealerId, Long invoiceOrderId, String invoiceCode, List<StdLine> lines) {
        UUID tid = tid();
        for (StdLine l : lines) {
            if (l.productId()==null||l.qty()==null||l.qty().signum()<=0) continue;
            int need = l.qty().intValue();
            Tuple row = locateStockRow(tid, dealerId, l.stockId(), l.productId());
            if (row != null) {
                int onHand = ((Number) row.get("on_hand_qty")).intValue();
                int locked = ((Number) row.get("locked_qty")).intValue();
                int avail = onHand - locked;
                if (avail < need)
                    throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "经销商寄售库存不足：产品 [" + nz(productCode(l.productId())) + " " + nz(productName(l.productId())) + "]"
                        + (l.batchNo()==null?"":" 批号 "+l.batchNo()) + " 可用 " + avail + "，开票需 " + need);
                adjustStockRow(((Number) row.get("id")).longValue(), 0, need);
                writeMovement(tid, dealerId, l.productId(),
                        row.get("batch_no")==null||String.valueOf(row.get("batch_no")).isBlank()?null:String.valueOf(row.get("batch_no")),
                        row.get("serial_no")==null||String.valueOf(row.get("serial_no")).isBlank()?null:String.valueOf(row.get("serial_no")),
                        "INVOICE_LOCK", -need, "INVOICE_ORDER", invoiceOrderId, invoiceCode, "开票提交预占");
            } else {
                int avail = availableQty(tid, dealerId, l.productId(), l.batchNo(), l.serialNo());
                if (avail < need)
                    throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "经销商寄售库存不足：产品 [" + nz(productCode(l.productId())) + " " + nz(productName(l.productId())) + "]"
                        + (l.batchNo()==null?"":" 批号 "+l.batchNo()) + " 可用 " + avail + "，开票需 " + need);
                upsertStock(tid, dealerId, l.productId(), l.batchNo(), l.serialNo(), l.warehouseId(), 0, need, stdPrice(tid,l.productId()), null);
                writeMovement(tid, dealerId, l.productId(), l.batchNo(), l.serialNo(), "INVOICE_LOCK", -need, "INVOICE_ORDER", invoiceOrderId, invoiceCode, "开票提交预占");
            }
        }
        recomputeConsignmentUsed(tid, dealerId);
    }

    @Transactional
    public void deductForInvoice(Long dealerId, Long invoiceOrderId, String invoiceCode, List<StdLine> lines) {
        UUID tid = tid();
        for (StdLine l : lines) {
            if (l.productId()==null||l.qty()==null||l.qty().signum()<=0) continue;
            int q = l.qty().intValue();
            Tuple row = locateStockRow(tid, dealerId, l.stockId(), l.productId());
            if (row != null) {
                adjustStockRow(((Number) row.get("id")).longValue(), -q, -q);
                writeMovement(tid, dealerId, l.productId(),
                        row.get("batch_no")==null||String.valueOf(row.get("batch_no")).isBlank()?null:String.valueOf(row.get("batch_no")),
                        row.get("serial_no")==null||String.valueOf(row.get("serial_no")).isBlank()?null:String.valueOf(row.get("serial_no")),
                        "INVOICE_DEDUCT", -q, "INVOICE_ORDER", invoiceOrderId, invoiceCode, "开票审批通过实扣");
            } else {
                upsertStock(tid, dealerId, l.productId(), l.batchNo(), l.serialNo(), l.warehouseId(), -q, -q, stdPrice(tid,l.productId()), null);
                writeMovement(tid, dealerId, l.productId(), l.batchNo(), l.serialNo(), "INVOICE_DEDUCT", -q, "INVOICE_ORDER", invoiceOrderId, invoiceCode, "开票审批通过实扣");
            }
        }
        recomputeConsignmentUsed(tid, dealerId);
    }

    @Transactional
    public void releaseForInvoice(Long dealerId, Long invoiceOrderId, String invoiceCode, List<StdLine> lines) {
        UUID tid = tid();
        for (StdLine l : lines) {
            if (l.productId()==null||l.qty()==null||l.qty().signum()<=0) continue;
            int q = l.qty().intValue();
            Tuple row = locateStockRow(tid, dealerId, l.stockId(), l.productId());
            if (row != null) {
                adjustStockRow(((Number) row.get("id")).longValue(), 0, -q);
                writeMovement(tid, dealerId, l.productId(),
                        row.get("batch_no")==null||String.valueOf(row.get("batch_no")).isBlank()?null:String.valueOf(row.get("batch_no")),
                        row.get("serial_no")==null||String.valueOf(row.get("serial_no")).isBlank()?null:String.valueOf(row.get("serial_no")),
                        "INVOICE_RELEASE", q, "INVOICE_ORDER", invoiceOrderId, invoiceCode, "开票释放预占");
            } else {
                upsertStock(tid, dealerId, l.productId(), l.batchNo(), l.serialNo(), l.warehouseId(), 0, -q, stdPrice(tid,l.productId()), null);
                writeMovement(tid, dealerId, l.productId(), l.batchNo(), l.serialNo(), "INVOICE_RELEASE", q, "INVOICE_ORDER", invoiceOrderId, invoiceCode, "开票释放预占");
            }
        }
        recomputeConsignmentUsed(tid, dealerId);
    }

    /**
     * v4.4.1 补货红字出库（销退/红冲回调）：把对应数量从经销商寄售台账冲回(on_hand-)。
     * 按 产品+批号+序列号 维度匹配台账行；无台账行时跳过（可能本就不是寄售补货入库的批次）。
     */
    @Transactional
    public void onReplenishReversed(Long dealerId, Long salesOutId, List<StdLine> lines) {
        if (dealerId==null || lines==null || lines.isEmpty()) return;
        UUID tid = tid();
        int touched = 0;
        for (StdLine l : lines) {
            if (l.productId()==null || l.qty()==null || l.qty().signum()<=0) continue;
            int q = l.qty().intValue();
            List<Tuple> rs = em.createNativeQuery(
                "SELECT id, on_hand_qty, locked_qty FROM consignment_stock " +
                "WHERE tenant_id=?1 AND dealer_id=?2 AND product_id=?3 AND COALESCE(batch_no,'')=COALESCE(?4,'') AND COALESCE(serial_no,'')=COALESCE(?5,'')",
                Tuple.class)
                .setParameter(1,tid).setParameter(2,dealerId).setParameter(3,l.productId())
                .setParameter(4,bn(l.batchNo())).setParameter(5,bn(l.serialNo())).getResultList();
            if (rs.isEmpty()) {
                log.warn("补货红冲未匹配到寄售台账行 dealer={} product={} batch={} serial={} qty={}",
                        dealerId, l.productId(), l.batchNo(), l.serialNo(), q);
                continue;
            }
            Tuple row = rs.get(0);
            int onHand = ((Number) row.get("on_hand_qty")).intValue();
            int locked = ((Number) row.get("locked_qty")).intValue();
            if (onHand - q < locked) {
                log.warn("补货红冲冲回数量超过可用在库 dealer={} product={} onHand={} locked={} qty={}，仅冲回至锁定下限",
                        dealerId, l.productId(), onHand, locked, q);
                q = Math.max(onHand - locked, 0);
                if (q == 0) continue;
            }
            adjustStockRow(((Number) row.get("id")).longValue(), -q, 0);
            writeMovement(tid, dealerId, l.productId(), l.batchNo(), l.serialNo(),
                    "REPLENISH_OUT", -q, "SALES_OUT", salesOutId, null, "补货红冲冲回");
            touched++;
        }
        if (touched > 0) recomputeConsignmentUsed(tid, dealerId);
        log.info("补货红冲冲回寄售库存 dealer={} out={} lines={}", dealerId, salesOutId, touched);
    }

    public void recomputeConsignmentUsed(UUID tid, Long dealerId) {
        try {
            Object v = em.createNativeQuery("SELECT COALESCE(SUM(on_hand_qty*std_unit_price),0) FROM consignment_stock WHERE tenant_id=?1 AND dealer_id=?2")
                    .setParameter(1,tid).setParameter(2,dealerId).getSingleResult();
            BigDecimal used = v==null?BigDecimal.ZERO:new BigDecimal(v.toString());
            em.createNativeQuery(
                "INSERT INTO dealer_credit_profiles (tenant_id,dealer_id,consignment_used,status,created_at,updated_at,version) " +
                "VALUES (?1,?2,?3,'ACTIVE',now(),now(),0) ON CONFLICT (tenant_id,dealer_id) WHERE deleted_at IS NULL DO NOTHING")
                .setParameter(1,tid).setParameter(2,dealerId).setParameter(3,used).executeUpdate();
            em.createNativeQuery(
                "UPDATE dealer_credit_profiles SET consignment_used=?3, updated_at=now() WHERE tenant_id=?1 AND dealer_id=?2 AND deleted_at IS NULL")
                .setParameter(1,tid).setParameter(2,dealerId).setParameter(3,used).executeUpdate();
        } catch (Exception e) { log.warn("汇总寄售金额失败 dealer={}: {}", dealerId, e.toString(), e); }
    }

    private String nz(String s){ return s==null?"?":s; }

    /** 开票选库存：返回某经销商当前可用(在库-锁定)寄售库存，按 产品+批号+序列号 维度。 */
    @Transactional(readOnly = true)
    public List<Map<String,Object>> availableForInvoice(Long dealerId, String keyword) {
        UUID tid = tid();
        StringBuilder sql = new StringBuilder(
            "SELECT s.id, s.dealer_id, d.name AS dealer_name, s.product_id, s.product_code, s.product_name, s.product_spec, " +
            "s.batch_no, s.serial_no, s.warehouse_id, w.name AS warehouse_name, s.on_hand_qty, s.locked_qty, " +
            "(s.on_hand_qty - s.locked_qty) AS available_qty, s.std_unit_price, " +
            "((s.on_hand_qty - s.locked_qty) * s.std_unit_price) AS available_amount " +
            "FROM consignment_stock s LEFT JOIN dealers d ON d.id=s.dealer_id " +
            "LEFT JOIN warehouses w ON w.id=s.warehouse_id WHERE s.tenant_id=?1 AND (s.on_hand_qty - s.locked_qty) > 0 ");
        List<Object> p = new ArrayList<>(); p.add(tid);
        int idx = 2;
        if (dealerId != null) { sql.append(" AND s.dealer_id=?").append(idx++); p.add(dealerId); }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (s.product_code ILIKE ?").append(idx++).append(" OR s.product_name ILIKE ?").append(idx++)
               .append(" OR s.batch_no ILIKE ?").append(idx++).append(" OR s.serial_no ILIKE ?").append(idx++).append(")");
            String kw="%"+keyword+"%"; p.add(kw);p.add(kw);p.add(kw);p.add(kw);
        }
        sql.append(" ORDER BY d.name, s.product_code, s.batch_no");
        jakarta.persistence.Query q = em.createNativeQuery(sql.toString(), Tuple.class);
        for (int i=0;i<p.size();i++) q.setParameter(i+1, p.get(i));
        @SuppressWarnings("unchecked")
        List<Tuple> qrows = q.getResultList();
        List<Map<String,Object>> out = new ArrayList<>();
        for (Tuple t : qrows) {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("stockId", t.get("id"));
            m.put("dealerId", t.get("dealer_id"));
            m.put("dealerName", t.get("dealer_name"));
            m.put("productId", t.get("product_id"));
            m.put("productCode", t.get("product_code"));
            m.put("productName", t.get("product_name"));
            m.put("productSpec", t.get("product_spec"));
            m.put("batchNo", t.get("batch_no"));
            m.put("serialNo", t.get("serial_no"));
            m.put("warehouseId", t.get("warehouse_id"));
            m.put("warehouseName", t.get("warehouse_name"));
            m.put("onHandQty", t.get("on_hand_qty"));
            m.put("lockedQty", t.get("locked_qty"));
            m.put("availableQty", t.get("available_qty"));
            m.put("stdUnitPrice", t.get("std_unit_price"));
            m.put("availableAmount", t.get("available_amount"));
            out.add(m);
        }
        return out;
    }
}
