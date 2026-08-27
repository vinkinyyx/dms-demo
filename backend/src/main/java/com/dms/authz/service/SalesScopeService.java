/*
 * 销售下单数据范围服务（v4.3.0 R9）。
 *
 * 供 order 等业务模块在创建/查看销售订单时调用，判断当前用户是否可为目标客户下单：
 * <ul>
 *   <li>平台/系统管理员：不限制。</li>
 *   <li>客户账号（user_type=customer / role=CUSTOMER）：只能为自己绑定的 dealer 下单/查看，越权抛 403。</li>
 *   <li>经销商账号：只能为自己绑定的 dealer 下单。</li>
 *   <li>销售账号：只能为自己负责（sales_owner_user_id）或下级负责（销售组织树 + sales_dealer_mapping）的客户下单。</li>
 * </ul>
 *
 * 本服务不修改 order 业务代码，仅暴露可被 order 模块调用的校验/查询方法。
 */
package com.dms.authz.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.util.TenantContext;
import com.dms.org.controller.SalesOrgResolver;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SalesScopeService {

    private final EntityManager em;

    public record ScopeUser(Long id, String role, String userType, Long salesUserId, Long dealerId) {}

    /** 读取当前登录用户的范围信息。未登录返回 null。 */
    @Transactional(readOnly = true)
    public ScopeUser currentUser() {
        Long uid = TenantContext.getUserId();
        if (uid == null) {
            return null;
        }
        var q = em.createNativeQuery(
                "SELECT role, user_type, sales_user_id, dealer_id FROM users WHERE id = ?1 AND deleted_at IS NULL",
                Tuple.class);
        q.setParameter(1, uid);
        @SuppressWarnings("unchecked")
        List<Tuple> rows = q.getResultList();
        if (rows.isEmpty()) {
            return new ScopeUser(uid, null, null, null, null);
        }
        Tuple t = rows.get(0);
        return new ScopeUser(
                uid,
                t.get("role") == null ? null : String.valueOf(t.get("role")),
                t.get("user_type") == null ? null : String.valueOf(t.get("user_type")),
                t.get("sales_user_id") == null ? null : ((Number) t.get("sales_user_id")).longValue(),
                t.get("dealer_id") == null ? null : ((Number) t.get("dealer_id")).longValue());
    }

    public boolean isAdmin(ScopeUser u) {
        if (u == null || u.role() == null) return true;
        String r = u.role().toUpperCase();
        return "ADMIN".equals(r) || "SYS_ADMIN".equals(r) || "TENANT_ADMIN".equals(r)
                || "MANUFACTURER_ADMIN".equals(r) || "MFR_ADMIN".equals(r);
    }

    public boolean isCustomer(ScopeUser u) {
        if (u == null) return false;
        return "customer".equalsIgnoreCase(u.userType()) || "CUSTOMER".equalsIgnoreCase(u.role());
    }

    /**
     * 当前用户可访问/下单的经销商 id 集合。
     * @return null 表示全部可见（管理员）；空集合表示无权访问任何客户
     */
    @Transactional(readOnly = true)
    public Set<Long> accessibleDealerIds() {
        ScopeUser u = currentUser();
        if (isAdmin(u)) {
            return null;
        }
        UUID tid = TenantContext.getTenantId();
        // 客户 / 经销商账号：严格限定为自身绑定 dealer
        if (isCustomer(u) || (u.dealerId() != null && !"sales".equalsIgnoreCase(u.role()))) {
            Set<Long> self = new HashSet<>();
            if (u.dealerId() != null) self.add(u.dealerId());
            return self;
        }
        // 销售：本人 + 下级负责的经销商（组织树映射 + dealers.sales_owner_user_id 双来源）
        if ("sales".equalsIgnoreCase(u.role()) && u.salesUserId() != null) {
            return salesOwnedDealers(tid, u.salesUserId());
        }
        // 其他后台角色：不限（保持与现有 DataScope 回退一致）
        return null;
    }

    /**
     * 校验当前用户是否可为目标经销商下单/查看。越权抛 BusinessException(FORBIDDEN)。
     * 供 order 模块在创建销售订单、查询订单详情时调用。
     */
    public void requireDealerAccessible(Long dealerId) {
        if (dealerId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "下单客户不能为空");
        }
        ScopeUser u = currentUser();
        if (isAdmin(u)) {
            return;
        }
        Set<Long> allowed = accessibleDealerIds();
        if (allowed == null) {
            return;
        }
        if (!allowed.contains(dealerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "无权为该客户下单或查看：当前账号只能操作自己负责范围内的客户");
        }
    }

    /** 不抛异常的布尔判断，供查询拼装条件使用。 */
    @Transactional(readOnly = true)
    public boolean canAccessDealer(Long dealerId) {
        ScopeUser u = currentUser();
        if (isAdmin(u)) return true;
        if (dealerId == null) return false;
        Set<Long> allowed = accessibleDealerIds();
        return allowed == null || allowed.contains(dealerId);
    }

    /** 计算某销售（含其下级）负责的经销商集合。 */
    @Transactional(readOnly = true)
    public Set<Long> salesOwnedDealers(UUID tid, Long salesUserId) {
        Set<Long> salesIds = SalesOrgResolver.recursiveSubordinates(em, tid, salesUserId);
        Set<Long> dealers = new HashSet<>();
        if (salesIds.isEmpty()) {
            return dealers;
        }
        // 来源 1：sales_dealer_mapping
        var q1 = em.createNativeQuery(
                "SELECT DISTINCT dealer_id FROM sales_dealer_mapping " +
                "WHERE tenant_id = ?1 AND sales_user_id = ANY(?2)");
        q1.setParameter(1, tid).setParameter(2, salesIds.toArray(new Long[0]));
        @SuppressWarnings("unchecked")
        List<Number> mapped = q1.getResultList();
        for (Number n : mapped) if (n != null) dealers.add(n.longValue());

        // 来源 2：dealers.sales_owner_user_id
        var q2 = em.createNativeQuery(
                "SELECT id FROM dealers WHERE tenant_id = ?1 AND deleted_at IS NULL " +
                "AND sales_owner_user_id = ANY(?2)");
        q2.setParameter(1, tid).setParameter(2, salesIds.toArray(new Long[0]));
        @SuppressWarnings("unchecked")
        List<Number> owned = q2.getResultList();
        for (Number n : owned) if (n != null) dealers.add(n.longValue());
        return dealers;
    }
}
