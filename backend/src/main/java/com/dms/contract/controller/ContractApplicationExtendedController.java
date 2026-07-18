/*
 * 合同申请扩展接口（US-A-05/06/07）
 * - 变更前后对照
 * - 续约一键复制
 * - 批量延展（>=50 条一次性延期）
 */
package com.dms.contract.controller;

import com.dms.common.ApiResponse;
import com.dms.common.util.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/contract-applications")
@RequiredArgsConstructor
public class ContractApplicationExtendedController {

    private final EntityManager em;

    /**
     * 变更申请前后对照（US-A-05）
     * 返回原合同 vs 申请中的变更字段对照
     */
    @GetMapping("/{id}/diff")
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> diff(@PathVariable Long id) {
        try {
            var q = em.createNativeQuery(
                    "SELECT ca.id AS app_id, ca.application_type, ca.contract_category, " +
                    " ca.valid_from AS new_from, ca.valid_to AS new_to, ca.dealer_id AS new_dealer, " +
                    " c.id AS contract_id, c.code AS contract_code, " +
                    " c.valid_from AS old_from, c.valid_to AS old_to, c.dealer_id AS old_dealer, " +
                    " c.category AS old_category " +
                    "FROM contract_applications ca " +
                    "LEFT JOIN contracts c ON c.id = ca.ref_contract_id " +
                    "WHERE ca.id = ?1 AND ca.tenant_id = ?2 LIMIT 1", Tuple.class);
            q.setParameter(1, id).setParameter(2, TenantContext.getTenantId());
            @SuppressWarnings("unchecked")
            List<Tuple> rows = q.getResultList();
            if (rows.isEmpty()) return ApiResponse.fail(40404, "申请不存在");
            Tuple t = rows.get(0);

            List<Map<String, Object>> diffs = new ArrayList<>();
            addDiff(diffs, "生效开始", t.get("old_from"), t.get("new_from"));
            addDiff(diffs, "生效结束", t.get("old_to"),   t.get("new_to"));
            addDiff(diffs, "经销商",   t.get("old_dealer"), t.get("new_dealer"));
            addDiff(diffs, "合同分类", t.get("old_category"), t.get("contract_category"));

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("applicationId", t.get("app_id"));
            data.put("applicationType", t.get("application_type"));
            data.put("refContractId", t.get("contract_id"));
            data.put("refContractCode", t.get("contract_code"));
            data.put("diffs", diffs);
            data.put("diffsCount", diffs.stream().filter(d -> Boolean.TRUE.equals(d.get("changed"))).count());
            return ApiResponse.ok(data);
        } catch (Exception e) {
            log.warn("查询合同变更对照失败", e);
            return ApiResponse.fail(50000, "查询失败：" + e.getMessage());
        }
    }

    /**
     * 续约一键复制（US-A-06）
     * 复制原合同信息生成一条 RENEW 申请
     */
    @PostMapping("/renew-from/{contractId}")
    @Transactional
    public ApiResponse<Map<String, Object>> renew(
            @PathVariable Long contractId,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            var q = em.createNativeQuery(
                    "SELECT id, code, dealer_id, category, valid_from, valid_to " +
                    "FROM contracts WHERE id = ?1 AND tenant_id = ?2", Tuple.class);
            q.setParameter(1, contractId).setParameter(2, TenantContext.getTenantId());
            @SuppressWarnings("unchecked")
            List<Tuple> rows = q.getResultList();
            if (rows.isEmpty()) return ApiResponse.fail(40404, "原合同不存在");
            Tuple t = rows.get(0);

            LocalDate oldTo = t.get("valid_to") == null ? LocalDate.now() : LocalDate.parse(String.valueOf(t.get("valid_to")));
            LocalDate newFrom = oldTo.plusDays(1);
            LocalDate newTo = newFrom.plusYears(1);
            if (body != null && body.get("newValidTo") != null) {
                newTo = LocalDate.parse(String.valueOf(body.get("newValidTo")));
            }

            String code = "CA-RENEW-" + contractId + "-" + System.currentTimeMillis();
            var insert = em.createNativeQuery(
                    "INSERT INTO contract_applications " +
                    "(tenant_id, code, application_type, contract_category, dealer_id, ref_contract_id, " +
                    " valid_from, valid_to, status, remark, created_at, updated_at) " +
                    "VALUES (?1, ?2, 'RENEW', ?3, ?4, ?5, ?6, ?7, 'DRAFT', ?8, now(), now())");
            insert.setParameter(1, TenantContext.getTenantId());
            insert.setParameter(2, code);
            insert.setParameter(3, t.get("category"));
            insert.setParameter(4, t.get("dealer_id"));
            insert.setParameter(5, contractId);
            insert.setParameter(6, newFrom);
            insert.setParameter(7, newTo);
            insert.setParameter(8, "由合同 " + t.get("code") + " 一键续约生成");
            insert.executeUpdate();

            Map<String, Object> res = new LinkedHashMap<>();
            res.put("newApplicationCode", code);
            res.put("refContractId", contractId);
            res.put("refContractCode", t.get("code"));
            res.put("newValidFrom", newFrom.toString());
            res.put("newValidTo", newTo.toString());
            return ApiResponse.ok(res);
        } catch (Exception e) {
            log.error("续约失败", e);
            return ApiResponse.fail(50000, "续约失败：" + e.getMessage());
        }
    }

    /**
     * 批量延展（US-A-07）
     * 请求体：{ "contractIds":[1,2,3,...], "extendMonths":12 }
     */
    @PostMapping("/batch-extend")
    @Transactional
    public ApiResponse<Map<String, Object>> batchExtend(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Object> ids = (List<Object>) body.get("contractIds");
        int months = body.get("extendMonths") == null ? 12 : ((Number) body.get("extendMonths")).intValue();
        if (ids == null || ids.isEmpty()) {
            return ApiResponse.fail(40001, "contractIds 不能为空");
        }
        UUID tenantId = TenantContext.getTenantId();
        int success = 0, failed = 0;
        List<Map<String, Object>> generated = new ArrayList<>();
        List<Map<String, Object>> errors = new ArrayList<>();

        for (Object idObj : ids) {
            Long cid = (idObj instanceof Number) ? ((Number) idObj).longValue() : Long.valueOf(String.valueOf(idObj));
            try {
                var q = em.createNativeQuery(
                        "SELECT code, dealer_id, category, valid_to FROM contracts " +
                        "WHERE id = ?1 AND tenant_id = ?2", Tuple.class);
                q.setParameter(1, cid).setParameter(2, tenantId);
                @SuppressWarnings("unchecked")
                List<Tuple> rs = q.getResultList();
                if (rs.isEmpty()) throw new RuntimeException("合同不存在");
                Tuple t = rs.get(0);

                LocalDate oldTo = t.get("valid_to") == null ? LocalDate.now() : LocalDate.parse(String.valueOf(t.get("valid_to")));
                LocalDate newTo = oldTo.plusMonths(months);

                String code = "CA-EXT-" + cid + "-" + System.currentTimeMillis();
                var ins = em.createNativeQuery(
                        "INSERT INTO contract_applications " +
                        "(tenant_id, code, application_type, contract_category, dealer_id, ref_contract_id, " +
                        " valid_from, valid_to, status, remark, created_at, updated_at) " +
                        "VALUES (?1, ?2, 'RENEW', ?3, ?4, ?5, ?6, ?7, 'DRAFT', ?8, now(), now())");
                ins.setParameter(1, tenantId);
                ins.setParameter(2, code);
                ins.setParameter(3, t.get("category"));
                ins.setParameter(4, t.get("dealer_id"));
                ins.setParameter(5, cid);
                ins.setParameter(6, oldTo.plusDays(1));
                ins.setParameter(7, newTo);
                ins.setParameter(8, "批量延展 " + months + " 个月");
                ins.executeUpdate();

                Map<String, Object> g = new LinkedHashMap<>();
                g.put("contractId", cid);
                g.put("contractCode", t.get("code"));
                g.put("applicationCode", code);
                g.put("newValidTo", newTo.toString());
                generated.add(g);
                success++;
            } catch (Exception e) {
                failed++;
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("contractId", cid);
                err.put("error", e.getMessage());
                errors.add(err);
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", ids.size());
        data.put("success", success);
        data.put("failed", failed);
        data.put("extendMonths", months);
        data.put("generated", generated);
        data.put("errors", errors);
        return ApiResponse.ok(data);
    }

    private void addDiff(List<Map<String, Object>> list, String field, Object oldVal, Object newVal) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("field", field);
        m.put("oldValue", oldVal == null ? "-" : String.valueOf(oldVal));
        m.put("newValue", newVal == null ? "-" : String.valueOf(newVal));
        m.put("changed", !Objects.equals(String.valueOf(oldVal), String.valueOf(newVal)));
        list.add(m);
    }
}
