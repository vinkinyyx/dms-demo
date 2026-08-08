/*
 * 合同 REST 控制器。
 */
package com.dms.contract.controller;

import com.dms.common.ApiResponse;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.util.ContentDispositionUtils;
import com.dms.common.util.DateFmt;
import com.dms.common.util.ExcelExportUtils;
import com.dms.common.util.TenantContext;
import com.dms.contract.entity.Contract;
import com.dms.contract.service.ContractService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/contracts")
@RequiredArgsConstructor
@Validated
public class ContractController {

    private final ContractService service;
    private final EntityManager em;

    @GetMapping
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApiResponse<Map<String, Object>> list(@Valid PageQuery pageQuery) {
        UUID tid = TenantContext.getTenantId();
        // 1) count
        Long total;
        if (tid == null) {
            total = ((Number) em.createNativeQuery("SELECT COUNT(*) FROM contracts WHERE deleted_at IS NULL").getSingleResult()).longValue();
        } else {
            total = ((Number) em.createNativeQuery("SELECT COUNT(*) FROM contracts WHERE deleted_at IS NULL AND tenant_id = :t").setParameter("t", tid).getSingleResult()).longValue();
        }
        // 2) data with dealer / region / contractName
        int page = pageQuery.getPage();
        int size = pageQuery.getSize();
        String where = tid == null ? "c.deleted_at IS NULL" : "c.deleted_at IS NULL AND c.tenant_id = :t";
        var q = em.createNativeQuery(
                "SELECT c.id, c.code, c.category, c.dealer_id, d.code AS dealer_code, d.name AS dealer_name, d.level AS dealer_level, " +
                " r.name AS region_name, " +
                " c.valid_from, c.valid_to, c.status, c.dealer_signed_at, c.vendor_signed_at, " +
                " c.ca_serial_no, c.pdf_url, c.application_id, c.created_at, c.updated_at, " +
                " (SELECT COUNT(*) FROM contract_signatures cs WHERE cs.contract_id = c.id) AS sign_count, " +
                " (SELECT COUNT(*) FROM contract_attachments ca WHERE ca.ref_type = 'CONTRACT' AND ca.ref_id = c.id) AS attach_count " +
                " FROM contracts c " +
                " LEFT JOIN dealers d ON d.id = c.dealer_id " +
                " LEFT JOIN regions r ON r.id = d.region_id " +
                " WHERE " + where + " ORDER BY c.id DESC LIMIT :lim OFFSET :off", Tuple.class);
        if (tid != null) q.setParameter("t", tid);
        q.setParameter("lim", Math.max(1, Math.min(500, size)));
        q.setParameter("off", Math.max(0, (page - 1) * size));
        @SuppressWarnings("unchecked")
        List<Tuple> rows = q.getResultList();
        List<Map<String, Object>> list = new ArrayList<>(rows.size());
        for (Tuple t : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.get("id"));
            m.put("code", t.get("code"));
            m.put("category", t.get("category"));
            m.put("dealerId", t.get("dealer_id"));
            m.put("dealerCode", t.get("dealer_code"));
            m.put("dealerName", t.get("dealer_name"));
            m.put("dealerLevel", t.get("dealer_level"));
            m.put("regionName", t.get("region_name"));
            m.put("validFrom", t.get("valid_from"));
            m.put("validTo", t.get("valid_to"));
            m.put("status", t.get("status"));
            m.put("dealerSignedAt", t.get("dealer_signed_at"));
            m.put("vendorSignedAt", t.get("vendor_signed_at"));
            m.put("caSerialNo", t.get("ca_serial_no"));
            m.put("pdfUrl", t.get("pdf_url"));
            m.put("applicationId", t.get("application_id"));
            m.put("signCount", t.get("sign_count"));
            m.put("attachCount", t.get("attach_count"));
            m.put("createdAt", t.get("created_at"));
            m.put("updatedAt", t.get("updated_at"));
            list.add(m);
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("total", total);
        res.put("page", page);
        res.put("size", size);
        res.put("list", list);
        return ApiResponse.ok(res);
    }

    @GetMapping("/{id}")
    public ApiResponse<Contract> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @GetMapping("/actions/export")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ResponseEntity<byte[]> export() throws Exception {
        UUID tid = TenantContext.getTenantId();
        var q = em.createNativeQuery(
                "SELECT c.id, c.code, c.category, c.dealer_id, d.name AS dealer_name, " +
                "c.valid_from, c.valid_to, c.status, c.created_at, c.updated_at " +
                "FROM contracts c LEFT JOIN dealers d ON d.id = c.dealer_id " +
                "WHERE c.tenant_id = :tid AND c.deleted_at IS NULL " +
                "ORDER BY c.id DESC", Tuple.class);
        q.setParameter("tid", tid);
        @SuppressWarnings("unchecked")
        List<Tuple> rows = q.getResultList();

        List<Map<String, Object>> list = new ArrayList<>();
        for (Tuple t : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.get("id"));
            m.put("code", t.get("code"));
            m.put("category", t.get("category"));
            m.put("dealerId", t.get("dealer_id"));
            m.put("dealerName", t.get("dealer_name"));
            m.put("validFrom", DateFmt.fmt(t.get("valid_from")));
            m.put("validTo", DateFmt.fmt(t.get("valid_to")));
            m.put("status", t.get("status"));
            m.put("createdAt", DateFmt.fmt(t.get("created_at")));
            m.put("updatedAt", DateFmt.fmt(t.get("updated_at")));
            list.add(m);
        }

        String[] headers = {"ID", "\u5408\u540c\u7f16\u53f7", "\u5206\u7c7b", "\u7ecf\u9500\u5546ID", "\u7ecf\u9500\u5546", "\u751f\u6548", "\u622a\u6b62", "\u72b6\u6001", "\u521b\u5efa\u65f6\u95f4", "\u66f4\u65b0\u65f6\u95f4"};
        String[] fieldNames = {"id", "code", "category", "dealerId", "dealerName", "validFrom", "validTo", "status", "createdAt", "updatedAt"};

        byte[] excelBytes = ExcelExportUtils.exportMapToExcel(list, headers, fieldNames);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDispositionUtils.attachment("\u5408\u540c\u5217\u8868.xlsx"))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(excelBytes);
    }

    @PostMapping("/{id}/terminate")
    public ApiResponse<Void> terminate(@PathVariable Long id) {
        service.terminate(id);
        return ApiResponse.ok();
    }
}
