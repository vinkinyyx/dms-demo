/*
 * 数据权限解析：根据当前登录用户的 role/sales_user_id/dealer_id，
 * 计算其可访问的 dealer_id 集合，以及订单/业务数据的过滤范围。
 *
 * <ul>
 *   <li>admin / sys_admin：返回 null 表示全部可见</li>
 *   <li>dealer：仅可见自己绑定的 dealer_id</li>
 *   <li>sales：可见本人及其下级负责的经销商（销售组织树）</li>
 *   <li>其他角色默认仅可见本人创建的数据</li>
 * </ul>
 */
package com.dms.security;

import com.dms.common.util.TenantContext;
import com.dms.org.controller.SalesOrgResolver;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class DataScope {

    private final EntityManager em;

    public record CurrentUser(Long id, String role, Long salesUserId, Long dealerId) {}

    /** 解析当前登录用户。未登录或用户不存在时返回 admin 级别的空角色对象。 */
    public CurrentUser currentUser() {
        Long uid = TenantContext.getUserId();
        if (uid == null) {
            return new CurrentUser(null, "admin", null, null);
        }
        var q = em.createNativeQuery(
                "SELECT role, sales_user_id, dealer_id FROM users WHERE id = ?1", Tuple.class);
        q.setParameter(1, uid);
        @SuppressWarnings("unchecked")
        java.util.List<Tuple> rows = q.getResultList();
        if (rows.isEmpty()) {
            return new CurrentUser(uid, null, null, null);
        }
        Tuple t = rows.get(0);
        return new CurrentUser(
                uid,
                t.get("role") == null ? null : String.valueOf(t.get("role")),
                t.get("sales_user_id") == null ? null : ((Number) t.get("sales_user_id")).longValue(),
                t.get("dealer_id") == null ? null : ((Number) t.get("dealer_id")).longValue());
    }

    /** 当前用户是否为平台/系统管理员（不受数据范围限制）。 */
    public boolean isAdmin() {
        String role = currentUser().role();
        return role == null || "admin".equalsIgnoreCase(role) || "sys_admin".equalsIgnoreCase(role);
    }

    /**
     * 当前用户可访问的 dealer_id 集合。
     * @return null 表示全部；空集合表示无权访问任何经销商
     */
    public Set<Long> accessibleDealerIds() {
        CurrentUser u = currentUser();
        Set<Long> ids = SalesOrgResolver.resolveAccessibleDealerIds(em, TenantContext.getTenantId(),
                u.role(), u.salesUserId(), u.dealerId());
        // 经销商类角色（DEALER_ADMIN/DEALER_SERVICE/DEALER_SALES）或绑定了 dealer_id 的账号，
        // 必须严格限定到自己绑定的经销商（即使为空也表示无权）。
        String role = u.role() == null ? "" : u.role().toUpperCase();
        boolean isDealerRole = role.startsWith("DEALER") || "dealer".equalsIgnoreCase(u.role())
                || u.dealerId() != null;
        if (isDealerRole) {
            if (u.dealerId() != null) {
                return java.util.Set.of(u.dealerId());
            }
            return ids == null ? java.util.Set.of() : ids;
        }
        // 销售/后台角色（sales_mgr/cs/biz/fin/contract...）：若组织/岗位未映射到具体经销商，
        // 回退为本租户全部可见，避免演示环境因缺少映射数据而看到空列表。
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        return ids;
    }

    /** 当前用户作为销售员时，可见的销售订单创建人集合（本人 + 下级）。非销售角色返回 null（不限）。 */
    public Set<Long> accessibleSalesUserIds() {
        CurrentUser u = currentUser();
        if (isAdmin()) return null;
        if ("sales".equalsIgnoreCase(u.role()) && u.salesUserId() != null) {
            return SalesOrgResolver.recursiveSubordinates(em, TenantContext.getTenantId(), u.salesUserId());
        }
        if ("dealer".equalsIgnoreCase(u.role())) return null;
        // 其他业务角色（财务/客服/合同等）默认看全部本租户数据
        return null;
    }
}