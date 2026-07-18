/*
 * 订单批量导入/异步导出（US-B-09, US-B-10）
 * V1 Mock：
 *   - 批量导入：接受 JSON 数组或 CSV 内容，逐条调用 OrderService.create
 *   - 异步导出：立即创建导出任务，返回 taskId；后台生成 CSV
 */
package com.dms.order.controller;

import com.dms.common.ApiResponse;
import com.dms.common.util.TenantContext;
import com.dms.order.dto.OrderCreateRequest;
import com.dms.order.service.OrderService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderBatchController {

    private final OrderService orderService;
    private final EntityManager em;
    /** 内存中的导出任务表（V1 Mock，生产版本应写 DB）*/
    private static final Map<String, Map<String, Object>> EXPORT_TASKS = new ConcurrentHashMap<>();

    /**
     * 批量导入订单（US-B-09）
     * 请求体：{ "orders": [ { orderType, dealerId, lines:[...] }, ... ] }
     */
    @PostMapping("/batch-import")
    public ApiResponse<Map<String, Object>> batchImport(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> orders = (List<Map<String, Object>>) body.get("orders");
        if (orders == null || orders.isEmpty()) {
            return ApiResponse.fail(40001, "orders 字段为空");
        }
        int success = 0, failed = 0;
        List<Map<String, Object>> errors = new ArrayList<>();
        for (int i = 0; i < orders.size(); i++) {
            Map<String, Object> raw = orders.get(i);
            try {
                OrderCreateRequest req = new OrderCreateRequest();
                req.setOrderType(String.valueOf(raw.getOrDefault("orderType", "NORMAL")));
                req.setDealerId(toLong(raw.get("dealerId")));
                req.setRemark(String.valueOf(raw.getOrDefault("remark", "batch-import")));
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> rawLines = (List<Map<String, Object>>) raw.get("lines");
                if (rawLines != null) {
                    List<OrderCreateRequest.Line> lines = new ArrayList<>();
                    for (int j = 0; j < rawLines.size(); j++) {
                        Map<String, Object> l = rawLines.get(j);
                        OrderCreateRequest.Line ln = new OrderCreateRequest.Line();
                        ln.setProductId(toLong(l.get("productId")));
                        ln.setQty(toBd(l.get("qty")));
                        ln.setUnitPrice(toBd(l.get("unitPrice")));
                        ln.setTaxRate(toBd(l.getOrDefault("taxRate", "0.13")));
                        ln.setSeq(j + 1);
                        lines.add(ln);
                    }
                    req.setLines(lines);
                }
                orderService.createOrder(req);
                success++;
            } catch (Exception e) {
                failed++;
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("index", i);
                err.put("error", e.getMessage());
                errors.add(err);
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", orders.size());
        data.put("success", success);
        data.put("failed", failed);
        data.put("errors", errors);
        return ApiResponse.ok(data);
    }

    /**
     * 异步导出订单（US-B-10）
     * 立即返回 taskId，后台任务生成 CSV 文件
     */
    @PostMapping("/export-async")
    public ApiResponse<Map<String, Object>> exportAsync(@RequestBody(required = false) Map<String, Object> body) {
        String taskId = "EXP-" + System.currentTimeMillis();
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("taskId", taskId);
        task.put("status", "RUNNING");
        task.put("progress", 0);
        task.put("startAt", LocalDateTime.now().toString());
        task.put("csvUrl", null);
        EXPORT_TASKS.put(taskId, task);

        UUID tenantId = TenantContext.getTenantId();
        runExport(taskId, tenantId);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("taskId", taskId);
        res.put("status", "RUNNING");
        res.put("message", "任务已提交，可通过 /api/orders/export-tasks/" + taskId + " 查询进度");
        return ApiResponse.ok(res);
    }

    /** 查询导出任务状态 */
    @GetMapping("/export-tasks/{taskId}")
    public ApiResponse<Map<String, Object>> getExportTask(@PathVariable String taskId) {
        Map<String, Object> task = EXPORT_TASKS.get(taskId);
        if (task == null) return ApiResponse.fail(40404, "导出任务不存在");
        return ApiResponse.ok(task);
    }

    /** 列出所有导出任务 */
    @GetMapping("/export-tasks")
    public ApiResponse<List<Map<String, Object>>> listTasks() {
        List<Map<String, Object>> list = new ArrayList<>(EXPORT_TASKS.values());
        list.sort((a, b) -> String.valueOf(b.get("startAt")).compareTo(String.valueOf(a.get("startAt"))));
        return ApiResponse.ok(list);
    }

    @Async
    @Transactional(readOnly = true)
    protected void runExport(String taskId, UUID tenantId) {
        Map<String, Object> task = EXPORT_TASKS.get(taskId);
        try {
            Thread.sleep(500);
            var q = em.createNativeQuery(
                    "SELECT id, code, order_type, dealer_id, amount_incl_tax, final_amount, status, expected_date " +
                    "FROM orders WHERE tenant_id = ?1 ORDER BY id DESC LIMIT 1000", Tuple.class);
            q.setParameter(1, tenantId);
            @SuppressWarnings("unchecked")
            List<Tuple> rows = q.getResultList();

            StringBuilder csv = new StringBuilder();
            csv.append("编号,订单号,类型,经销商ID,含税金额,最终金额,状态,期望到货\n");
            for (Tuple r : rows) {
                csv.append(r.get("id")).append(',')
                   .append(r.get("code")).append(',')
                   .append(r.get("order_type")).append(',')
                   .append(r.get("dealer_id")).append(',')
                   .append(r.get("amount_incl_tax")).append(',')
                   .append(r.get("final_amount")).append(',')
                   .append(r.get("status")).append(',')
                   .append(r.get("expected_date")).append('\n');
            }
            task.put("status", "COMPLETED");
            task.put("progress", 100);
            task.put("count", rows.size());
            task.put("csvContent", csv.toString());  // V1 Mock：直接把内容放 map，前端可以下载
            task.put("csvUrl", "/api/orders/export-tasks/" + taskId + "/download");
            task.put("finishAt", LocalDateTime.now().toString());
        } catch (Exception e) {
            task.put("status", "FAILED");
            task.put("error", e.getMessage());
        }
    }

    /** 下载 CSV */
    @GetMapping("/export-tasks/{taskId}/download")
    public org.springframework.http.ResponseEntity<String> download(@PathVariable String taskId) {
        Map<String, Object> task = EXPORT_TASKS.get(taskId);
        if (task == null || !"COMPLETED".equals(task.get("status"))) {
            return org.springframework.http.ResponseEntity.notFound().build();
        }
        String csv = (String) task.get("csvContent");
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Type", "text/csv; charset=UTF-8")
                .header("Content-Disposition", "attachment; filename=\"orders-" + taskId + ".csv\"")
                .body("\ufeff" + csv);  // BOM 让 Excel 识别 UTF-8
    }

    private Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).longValue();
        return Long.valueOf(String.valueOf(o));
    }
    private BigDecimal toBd(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return new BigDecimal(((Number) o).doubleValue());
        return new BigDecimal(String.valueOf(o));
    }
}
