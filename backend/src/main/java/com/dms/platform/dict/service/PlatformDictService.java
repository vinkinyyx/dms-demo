/*
 * 平台全局字典管理：dict_types/dict_items 中 tenant_id IS NULL 的全局记录。
 * 业务前台已有 /api/dicts/** 只读接口，优先读取当前租户字典并回退到全局字典。
 */
package com.dms.platform.dict.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.platform.audit.service.PlatformAuditService;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RedissonClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PlatformDictService {

    private static final String CACHE_PREFIX = "dms:cfg:dict:";

    private final JdbcTemplate jdbcTemplate;
    private final RedissonClient redisson;
    private final PlatformAuditService auditService;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listTypes() {
        return jdbcTemplate.queryForList(
                "SELECT id, code, name, description, created_at, updated_at " +
                        "FROM dict_types WHERE tenant_id IS NULL ORDER BY code");
    }

    @Transactional
    public Map<String, Object> createType(String code, String name, String description) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM dict_types WHERE tenant_id IS NULL AND code = ?",
                Integer.class, code);
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "字典类型编码已存在");
        }
        OffsetDateTime now = OffsetDateTime.now();
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO dict_types (tenant_id, code, name, description, created_at, updated_at) " +
                            "VALUES (NULL, ?, ?, ?, ?, ?)", new String[]{"id"});
            ps.setString(1, code);
            ps.setString(2, name);
            ps.setString(3, description);
            ps.setTimestamp(4, Timestamp.from(now.toInstant()));
            ps.setTimestamp(5, Timestamp.from(now.toInstant()));
            return ps;
        }, kh);
        evictDict(code);
        auditService.log("DICT_TYPE_CREATE", "dict_type", code, Map.of("code", code, "name", name));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", kh.getKey());
        result.put("code", code);
        result.put("name", name);
        return result;
    }

    @Transactional
    public void updateType(Long id, String name, String description) {
        int n = jdbcTemplate.update(
                "UPDATE dict_types SET name = COALESCE(?, name), description = ?, updated_at = ? " +
                        "WHERE id = ? AND tenant_id IS NULL",
                name, description, Timestamp.from(OffsetDateTime.now().toInstant()), id);
        if (n == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "字典类型不存在");
        }
        auditService.log("DICT_TYPE_UPDATE", "dict_type", String.valueOf(id), Map.of("name", name));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listItems(String typeCode) {
        return jdbcTemplate.queryForList(
                "SELECT di.id, di.code, di.name, di.seq, di.status, di.attrs " +
                        "FROM dict_items di JOIN dict_types dt ON dt.id = di.type_id " +
                        "WHERE dt.code = ? AND dt.tenant_id IS NULL ORDER BY di.seq, di.id",
                typeCode);
    }

    @Transactional
    public Map<String, Object> createItem(String typeCode, String code, String name, Integer seq) {
        Long typeId = jdbcTemplate.queryForObject(
                "SELECT id FROM dict_types WHERE tenant_id IS NULL AND code = ?",
                Long.class, typeCode);
        if (typeId == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "字典类型不存在");
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM dict_items WHERE type_id = ? AND code = ?",
                Integer.class, typeId, code);
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "字典项编码已存在");
        }
        OffsetDateTime now = OffsetDateTime.now();
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO dict_items (type_id, code, name, seq, status, created_at, updated_at) " +
                            "VALUES (?, ?, ?, ?, 'active', ?, ?)", new String[]{"id"});
            ps.setLong(1, typeId);
            ps.setString(2, code);
            ps.setString(3, name);
            ps.setObject(4, seq == null ? 100 : seq);
            ps.setTimestamp(5, Timestamp.from(now.toInstant()));
            ps.setTimestamp(6, Timestamp.from(now.toInstant()));
            return ps;
        }, kh);
        evictDict(typeCode);
        auditService.log("DICT_ITEM_CREATE", "dict_item", String.valueOf(kh.getKey()),
                Map.of("typeCode", typeCode, "code", code));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", kh.getKey());
        result.put("code", code);
        result.put("name", name);
        return result;
    }

    @Transactional
    public void updateItem(Long id, String code, String name, Integer seq) {
        int n = jdbcTemplate.update(
                "UPDATE dict_items SET code = COALESCE(?, code), name = COALESCE(?, name), " +
                        "seq = COALESCE(?, seq), updated_at = ? WHERE id = ?",
                code, name, seq, Timestamp.from(OffsetDateTime.now().toInstant()), id);
        if (n == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "字典项不存在");
        }
        auditService.log("DICT_ITEM_UPDATE", "dict_item", String.valueOf(id), Map.of());
    }

    @Transactional
    public void setItemStatus(Long id, boolean active) {
        int n = jdbcTemplate.update("UPDATE dict_items SET status = ?, updated_at = ? WHERE id = ?",
                active ? "active" : "disabled", Timestamp.from(OffsetDateTime.now().toInstant()), id);
        if (n == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "字典项不存在");
        }
        auditService.log(active ? "DICT_ITEM_ENABLE" : "DICT_ITEM_DISABLE", "dict_item",
                String.valueOf(id), Map.of("status", active ? "active" : "disabled"));
    }

    public void refreshCache() {
        redisson.getKeys().deleteByPattern(CACHE_PREFIX + "*");
        auditService.log("DICT_REFRESH_CACHE", "dict", null, null);
    }

    private void evictDict(String code) {
        redisson.getBucket(CACHE_PREFIX + code).delete();
    }
}