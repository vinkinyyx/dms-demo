/*
 * 移库控制器：/api/stock-moves
 * v3.4.13: 接受前端扁平结构（含批次/序列号/数量）+ 提供详情接口 + 操作日志
 * 跨仓语义：源仓库 → 目标仓库。
 */
package com.dms.inventory.controller;

import com.dms.common.ApiResponse;
import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.util.DateFmt;
import com.dms.common.util.TenantContext;
import com.dms.execution.service.OperationLogService;
import com.dms.inventory.service.InventoryStatusOps;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/stock-moves")
@RequiredArgsConstructor
public class StockMoveController {

    private final EntityManager em;
    private final InventoryStatusOps inventoryOps;
    private final OperationLogService opLog;

    @PostMapping
    @Transactional
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        UUID tid = TenantContext.getTenantId();
        if (tid == null) throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");

        Long fromWh = toLong(body.get("fromWarehouseId"));
        Long toWh = toLong(body.get("toWarehouseId"));
        String remark = strOr(body.get("remark"), "");
        String stockStatus = strOr(body.get("stockStatus"), "QUALIFIED");

        if (fromWh == null || toWh == null) throw new BusinessException(ErrorCode.PARAM_MISSING, "源仓库与目标仓库必填");
        if (fromWh.equals(toWh)) throw new BusinessException(ErrorCode.PARAM_INVALID, "源仓库与目标仓库不能相同");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) body.get("lines");
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "移动明细不能为空");
        }

        String code = "MOV-" + java.time.LocalDate.now().toString().replace("-", "") + "-" + (System.currentTimeMillis() % 100000);

        var ins = em.createNativeQuery(
                "INSERT INTO stock_moves (tenant_id, code, src_warehouse_id, dst_warehouse_id, status, reason, at_time, created_at, updated_at) " +
                "VALUES (?1, ?2, ?3, ?4, 'COMPLETED', ?5, now(), now(), now()) RETURNING id");
        ins.setParameter(1, tid).setParameter(2, code).setParameter(3, fromWh)
                .setParameter(4, toWh).setParameter(5, remark);
        Long moveId = ((Number) ins.getSingleResult()).longValue();

        for (Map<String, Object> l : lines) {
            Long productId = toLong(l.get("productId"));
            BigDecimal qty = toBd(l.get("qty"));
            String batchNo = strOr(l.get("batchNo"), null);
            String serialNo = strOr(l.get("serialNo"), null);
            if (productId == null || qty == null || qty.signum() <= 0) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "明细 productId/qty 必填且 > 0");
            }
            em.createNativeQuery(
                    "INSERT INTO stock_move_lines (move_id, product_id, batch_no, serial_no, qty) " +
                    "VALUES (?1, ?2, ?3, ?4, ?5)")
                .setParameter(1, moveId).setParameter(2, productId).setParameter(3, batchNo)
                .setParameter(4, serialNo).setParameter(5, qty)
                .executeUpdate();

            inventoryOps.change(tid, productId, fromWh, batchNo, qty.negate(), stockStatus, "MOVE_OUT", "stock_move", moveId);
            inventoryOps.change(tid, productId, toWh, batchNo, qty, stockStatus, "MOVE_IN", "stock_move", moveId);
        }

        opLog.log("stock_move", moveId, "CREATE", "库存移动 " + code + "，" + lines.size() + " 项");

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", moveId);
        res.put("code", code);
        res.put("fromWarehouseId", fromWh);
        res.put("toWarehouseId", toWh);
        res.put("lineCount", lines.size());
        res.put("message", "库存移动完成");
        return ApiResponse.ok(res);
    }

    @GetMapping({"/{id}/detail", "/{id}"})
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        UUID tid = TenantContext.getTenantId();
        var q = em.createNativeQuery(
                "SELECT m.id, m.code, m.src_warehouse_id, sw.name AS src_name, m.dst_warehouse_id, dw.name AS dst_name, " +
                "m.status, m.reason, m.created_at, m.updated_at " +
                "FROM stock_moves m " +
                "LEFT JOIN warehouses sw ON sw.id = m.src_warehouse_id " +
                "LEFT JOIN warehouses dw ON dw.id = m.dst_warehouse_id " +
                "WHERE m.id = ?1 AND m.tenant_id = ?2", Tuple.class);
        q.setParameter(1, id).setParameter(2, tid);
        List<?> rs = q.getResultList();
        if (rs.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "库存移动单不存在");
        Tuple t = (Tuple) rs.get(0);
        Map<String, Object> head = new LinkedHashMap<>();
        head.put("id", t.get("id"));
        head.put("code", t.get("code"));
        head.put("fromWarehouseId", t.get("src_warehouse_id"));
        head.put("fromWarehouseName", t.get("src_name"));
        head.put("toWarehouseId", t.get("dst_warehouse_id"));
        head.put("toWarehouseName", t.get("dst_name"));
        head.put("status", t.get("status"));
        head.put("remark", t.get("reason"));
        head.put("createdAt", DateFmt.fmt(t.get("created_at")));
        head.put("updatedAt", DateFmt.fmt(t.get("updated_at")));

        var lq = em.createNativeQuery(
                "SELECT l.product_id, p.name_cn AS product_name, p.code AS product_code, " +
                "l.batch_no, l.serial_no, l.qty " +
                "FROM stock_move_lines l LEFT JOIN products p ON p.id = l.product_id " +
                "WHERE l.move_id = ?1 ORDER BY l.id", Tuple.class);
        lq.setParameter(1, id);
        @SuppressWarnings("unchecked")
        List<Tuple> ls = lq.getResultList();
        List<Map<String, Object>> lines = new ArrayList<>();
        for (Tuple l : ls) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("productId", l.get("product_id"));
            m.put("productName", l.get("product_name"));
            m.put("productCode", l.get("product_code"));
            m.put("batchNo", l.get("batch_no"));
            m.put("serialNo", l.get("serial_no"));
            m.put("qty", l.get("qty"));
            lines.add(m);
        }
        head.put("lines", lines);
        return ApiResponse.ok(head);
    }

    private Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).longValue();
        try { return Long.valueOf(String.valueOf(o).trim()); } catch (Exception e) { return null; }
    }
    private BigDecimal toBd(Object o) {
        if (o == null) return null;
        if (o instanceof BigDecimal) return (BigDecimal) o;
        if (o instanceof Number) return BigDecimal.valueOf(((Number) o).doubleValue());
        try { return new BigDecimal(String.valueOf(o).trim()); } catch (Exception e) { return null; }
    }
    private String strOr(Object o, String d) {
        if (o == null) return d;
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? d : s;
    }
}
