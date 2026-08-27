package com.dms.tenant.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.util.TenantContext;
import com.dms.security.DataScope;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantFeatureGuard {
    private final EntityManager em;
    private final DataScope dataScope;
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * v4.4.0：进销存开关仅约束「厂家用户」。
     * 经销商用户（绑定了 dealer_id 的客户账号）即使租户关闭进销存，仍可使用库存/收货/采购相关菜单与接口。
     */
    private boolean isDealerUser() {
        try {
            DataScope.CurrentUser u = dataScope.currentUser();
            return u != null && u.dealerId() != null;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean inventoryEnabled() {
        UUID tid = TenantContext.getTenantId();
        if (tid == null) return true;
        var rs = em.createNativeQuery("SELECT modules_enabled FROM tenants WHERE id=?1").setParameter(1, tid).getResultList();
        if (rs.isEmpty()) return true;
        Object value = rs.get(0);
        if (value instanceof Map<?,?> m) return bool(m.get("inventoryEnabled"), true);
        try {
            Map<?,?> m = mapper.readValue(String.valueOf(value), Map.class);
            return bool(m.get("inventoryEnabled"), true);
        } catch (Exception e) {
            return true;
        }
    }

    public void requireInventory() {
        if (isDealerUser()) return;
        if (!inventoryEnabled()) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "当前租户未启用进销存/库存模块");
    }

    public void requirePurchase() {
        requireInventory();
    }

    private boolean bool(Object v, boolean def) {
        if (v == null) return def;
        if (v instanceof Boolean b) return b;
        return "true".equalsIgnoreCase(String.valueOf(v));
    }
}
