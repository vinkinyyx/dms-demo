package com.dms.tenant.controller;

import com.dms.common.ApiResponse;
import com.dms.common.util.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/tenant/features")
@RequiredArgsConstructor
public class TenantFeatureController {
    private final EntityManager em;
    private final ObjectMapper mapper = new ObjectMapper();

    @GetMapping
    public ApiResponse<Map<String, Object>> features() {
        UUID tid = TenantContext.getTenantId();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("inventoryEnabled", true);
        data.put("purchaseEnabled", true);
        if (tid != null) {
            var rs = em.createNativeQuery("SELECT modules_enabled FROM tenants WHERE id=?1").setParameter(1, tid).getResultList();
            if (!rs.isEmpty()) {
                Map<?,?> m = asMap(rs.get(0));
                Object v = m.get("inventoryEnabled");
                boolean enabled = v == null || Boolean.TRUE.equals(v) || "true".equals(String.valueOf(v));
                data.put("inventoryEnabled", enabled);
                data.put("purchaseEnabled", enabled);
            }
        }
        return ApiResponse.ok(data);
    }

    private Map<?,?> asMap(Object value) {
        if (value instanceof Map<?,?> m) return m;
        if (value == null) return Map.of();
        try { return mapper.readValue(String.valueOf(value), Map.class); } catch (Exception e) { return Map.of(); }
    }
}
