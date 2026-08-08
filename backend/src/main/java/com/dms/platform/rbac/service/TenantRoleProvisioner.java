/*
 * 租户开通时复制平台默认角色模板到租户内：
 * 1) 按租户类型读取 role_templates；
 * 2) 基于 platform_menus 为该租户生成菜单资源（resources）；
 * 3) 为每个默认角色创建 roles + strategies + strategy_resources + role_data_policies；
 * 4) 返回角色 code -> roleId 映射，供开通流程绑定租户管理员。
 *
 * 为减少跨表批量写入的样板代码，并处理 PostgreSQL 数组列(operations)，使用 JdbcTemplate。
 */
package com.dms.platform.rbac.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantRoleProvisioner {

    private static final String STATUS_ACTIVE = "active";
    private static final String ALL_TENANT_TYPES = "ALL";

    private final JdbcTemplate jdbcTemplate;

    /**
     * 为指定租户预置默认角色与菜单资源。
     *
     * @return 角色模板 code（如 MANUFACTURER_ADMIN）-> 新建 roles.id
     */
    @Transactional
    public Map<String, Long> provision(UUID tenantId, String tenantType) {
        OffsetDateTime now = OffsetDateTime.now();
        Timestamp ts = Timestamp.from(now.toInstant());

        List<Map<String, Object>> menus = jdbcTemplate.queryForList(
                "SELECT menu_key, label, permission_code, route, sort_order " +
                        "FROM platform_menus " +
                        "WHERE status = 'active' AND visible = true " +
                        "  AND (tenant_type = 'ALL' OR tenant_type = ?) " +
                        "ORDER BY sort_order ASC",
                tenantType);

        Map<String, Long> resourceIdByCode = new HashMap<>();
        Long nowLong = now.toInstant().toEpochMilli();
        for (Map<String, Object> menu : menus) {
            String permissionCode = (String) menu.get("permission_code");
            String menuKey = (String) menu.get("menu_key");
            String code = permissionCode != null && !permissionCode.isBlank() ? permissionCode : (menuKey + ":menu");
            String label = (String) menu.get("label");
            String route = (String) menu.get("route");
            Long rid = createResource(tenantId, code, label, "menu", route, now);
            resourceIdByCode.put(code, rid);
        }

        List<Map<String, Object>> templates = jdbcTemplate.queryForList(
                "SELECT id, code, name, data_scope, description FROM role_templates " +
                        "WHERE status = 'active' AND tenant_type = ?",
                tenantType);

        Map<String, Long> roleIdByCode = new HashMap<>();
        for (Map<String, Object> tpl : templates) {
            String code = (String) tpl.get("code");
            String name = (String) tpl.get("name");
            String dataScope = (String) tpl.get("data_scope");
            String description = (String) tpl.get("description");

            Long roleId = insertRole(tenantId, code, name, description, now);
            Long strategyId = insertStrategy(tenantId, name, description, now);

            jdbcTemplate.update(
                    "INSERT INTO role_strategies (role_id, strategy_id, created_at) VALUES (?, ?, ?)",
                    roleId, strategyId, ts);

            bindAllResources(strategyId, resourceIdByCode.values());

            insertRoleDataPolicy(roleId, dataScope, now);

            roleIdByCode.put(code, roleId);
            log.debug("租户 {} 预置角色 {}({}) 完成, roleId={}", tenantId, name, code, roleId);
        }
        log.info("租户 {} 预置默认角色完成: {}", tenantId, roleIdByCode.keySet());
        return roleIdByCode;
    }

    private Long createResource(UUID tenantId, String code, String name, String type,
                                String path, OffsetDateTime now) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO resources (tenant_id, code, name, type, operations, path, status, created_at, updated_at, version) " +
                        "VALUES (?, ?, ?, ?, ?, ?, 'active', ?, ?, 0) RETURNING id",
                Long.class, tenantId, code, name, type, new String[]{"view"}, path,
                Timestamp.from(now.toInstant()), Timestamp.from(now.toInstant()));
    }

    private Long insertRole(UUID tenantId, String code, String name, String description, OffsetDateTime now) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO roles (tenant_id, code, name, role_type, description, status, created_at, updated_at, version) " +
                        "VALUES (?, ?, ?, 'template', ?, 'active', ?, ?, 0) RETURNING id",
                Long.class, tenantId, code, name, description,
                Timestamp.from(now.toInstant()), Timestamp.from(now.toInstant()));
    }

    private Long insertStrategy(UUID tenantId, String name, String description, OffsetDateTime now) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO strategies (tenant_id, name, description, status, created_at, updated_at, version) " +
                        "VALUES (?, ?, ?, 'active', ?, ?, 0) RETURNING id",
                Long.class, tenantId, name, description,
                Timestamp.from(now.toInstant()), Timestamp.from(now.toInstant()));
    }

    private void bindAllResources(Long strategyId, java.util.Collection<Long> resourceIds) {
        jdbcTemplate.batchUpdate(
                "INSERT INTO strategy_resources (strategy_id, resource_id, operations, created_at) VALUES (?, ?, ARRAY['view']::varchar[], now())",
                new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(java.sql.PreparedStatement ps, int i) throws java.sql.SQLException {
                        ps.setLong(1, strategyId);
                        ps.setLong(2, (Long) resourceIds.toArray()[i]);
                    }

                    @Override
                    public int getBatchSize() {
                        return resourceIds.size();
                    }
                });
    }

    private void insertRoleDataPolicy(Long roleId, String dataScope, OffsetDateTime now) {
        boolean positionTree = "POSITION_TREE".equals(dataScope);
        boolean selfCreated = "SELF_CREATED".equals(dataScope);
        jdbcTemplate.update(
                "INSERT INTO role_data_policies (role_id, data_scope, position_tree_enabled, self_created_enabled, config, updated_at) " +
                        "VALUES (?, ?, ?, ?, '{}'::jsonb, ?)",
                roleId, dataScope, positionTree, selfCreated, Timestamp.from(now.toInstant()));
    }
}