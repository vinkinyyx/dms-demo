/*
 * 外部集成 Mock 客户端骨架
 * 覆盖 US-E-01 (ERP), US-E-02 (WMS), US-E-03 (HR), US-E-06 (UDI), US-E-07 (开关)
 *
 * 通过 SystemSettings 表的 integration.mode.{erp|wms|hr|udi} = "mock" | "real" 切换
 */
package com.dms.integration.controller;

import com.dms.common.ApiResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/integration")
@RequiredArgsConstructor
public class IntegrationController {

    private final EntityManager em;

    /**
     * 集成配置列表 + 切换开关（US-E-07）
     */
    @GetMapping("/config")
    @Transactional(readOnly = true)
    public ApiResponse<List<Map<String, Object>>> config() {
        List<Map<String, Object>> list = new ArrayList<>();
        String[] systems = {"erp", "wms", "hr", "udi", "ca"};
        String[] labels  = {"ERP 主业务系统", "WMS 仓储系统", "HR 人力资源", "UDI 医疗器械追溯", "CA 电子签章"};
        for (int i = 0; i < systems.length; i++) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("system", systems[i]);
            m.put("label", labels[i]);
            m.put("mode", getMode(systems[i]));
            m.put("endpoint", getEndpoint(systems[i]));
            m.put("lastSyncAt", LocalDateTime.now().minusMinutes(new Random().nextInt(120)).toString());
            m.put("status", "healthy");
            list.add(m);
        }
        return ApiResponse.ok(list);
    }

    /**
     * 切换 Mock/真实开关
     */
    @PostMapping("/config/{system}/mode")
    @Transactional
    public ApiResponse<Map<String, Object>> switchMode(
            @PathVariable String system,
            @RequestBody Map<String, Object> body) {
        String mode = String.valueOf(body.getOrDefault("mode", "mock"));
        if (!"mock".equals(mode) && !"real".equals(mode)) {
            return ApiResponse.fail(40001, "mode 只能是 mock 或 real");
        }
        String key = "integration.mode." + system;
        try {
            var upd = em.createNativeQuery(
                    "INSERT INTO system_settings (scope, key, value_json, description) " +
                    "VALUES ('system', ?1, ?2::jsonb, '集成模式开关') " +
                    "ON CONFLICT (scope, key) DO UPDATE SET value_json = EXCLUDED.value_json");
            upd.setParameter(1, key);
            upd.setParameter(2, "\"" + mode + "\"");
            upd.executeUpdate();
        } catch (Exception e) {
            log.warn("切换失败", e);
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("system", system);
        res.put("mode", mode);
        res.put("switchedAt", LocalDateTime.now().toString());
        return ApiResponse.ok(res);
    }

    // ============ ERP Mock（US-E-01）============
    @PostMapping("/erp/sync")
    public ApiResponse<Map<String, Object>> erpSync(@RequestBody(required = false) Map<String, Object> body) {
        String entity = body == null ? "orders" : String.valueOf(body.getOrDefault("entity", "orders"));
        log.info("[MOCK-ERP] 同步 {} 到 ERP", entity);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("system", "ERP");
        res.put("entity", entity);
        res.put("mode", getMode("erp"));
        res.put("recordsSynced", new Random().nextInt(50) + 10);
        res.put("succeededAt", LocalDateTime.now().toString());
        return ApiResponse.ok(res);
    }

    // ============ WMS Mock（US-E-02）============
    @PostMapping("/wms/receive-confirm")
    public ApiResponse<Map<String, Object>> wmsReceiveConfirm(@RequestBody Map<String, Object> body) {
        log.info("[MOCK-WMS] 收货回执: {}", body);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("wmsDocId", "WMS-" + System.currentTimeMillis());
        res.put("mode", getMode("wms"));
        res.put("status", "CONFIRMED");
        res.put("confirmedAt", LocalDateTime.now().toString());
        return ApiResponse.ok(res);
    }

    // ============ HR Mock（US-E-03）============
    @GetMapping("/hr/employees")
    public ApiResponse<List<Map<String, Object>>> hrEmployees(
            @RequestParam(defaultValue = "10") int limit) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 1; i <= limit; i++) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("employeeNo", "EMP" + String.format("%05d", i));
            m.put("name", "员工" + i);
            m.put("department", "部门 " + ((i % 5) + 1));
            m.put("position", i % 3 == 0 ? "经理" : "专员");
            m.put("mode", getMode("hr"));
            list.add(m);
        }
        return ApiResponse.ok(list);
    }

    @PostMapping("/hr/sync")
    public ApiResponse<Map<String, Object>> hrSync() {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("system", "HR");
        res.put("mode", getMode("hr"));
        res.put("employeesUpdated", new Random().nextInt(50));
        res.put("orgUnitsUpdated", new Random().nextInt(10));
        res.put("succeededAt", LocalDateTime.now().toString());
        return ApiResponse.ok(res);
    }

    // ============ UDI 上报 Mock（US-E-06）============
    @PostMapping("/udi/report")
    public ApiResponse<Map<String, Object>> udiReport(@RequestBody Map<String, Object> body) {
        log.info("[MOCK-UDI] 上报到 NMPA: {}", body);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("udiReportId", "UDI-" + System.currentTimeMillis());
        res.put("mode", getMode("udi"));
        res.put("submittedTo", "NMPA-Mock");
        res.put("status", "ACCEPTED");
        res.put("submittedAt", LocalDateTime.now().toString());
        return ApiResponse.ok(res);
    }

    private String getMode(String system) {
        try {
            var q = em.createNativeQuery(
                    "SELECT value_json FROM system_settings WHERE key = ?1 LIMIT 1");
            q.setParameter(1, "integration.mode." + system);
            List<?> rs = q.getResultList();
            if (rs.isEmpty()) return "mock";
            String v = String.valueOf(rs.get(0));
            return v.replace("\"", "").trim();
        } catch (Exception e) {
            return "mock";
        }
    }

    private String getEndpoint(String system) {
        Map<String, String> ep = new HashMap<>();
        ep.put("erp", "https://erp-mock.dms.internal/api");
        ep.put("wms", "https://wms-mock.dms.internal/api");
        ep.put("hr",  "https://hr-mock.dms.internal/api");
        ep.put("udi", "https://udi-mock.nmpa.gov.cn/api");
        ep.put("ca",  "https://ca-mock.esign.cn/api");
        return ep.getOrDefault(system, "-");
    }
}
