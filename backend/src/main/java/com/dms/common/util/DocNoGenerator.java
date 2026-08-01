/*
 * 单据编号生成器，格式 {PREFIX}-YYYYMMDD-{5位0填充连续流水}，如 PO-20260719-00001。
 * v3.4.12: 改用 DB 序列表 doc_no_sequences，保证同租户同日连续且不与历史数据撞号。
 */
package com.dms.common.util;

import com.dms.common.util.TenantContext;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocNoGenerator {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final EntityManager em;

    /**
     * 生成单据编号。基于 doc_no_sequences 表原子自增，格式 PREFIX-YYYYMMDD-00001。
     *
     * @param prefix 业务前缀，如 SO / PO / RK / CK
     * @return 完整单据号
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String next(String prefix) {
        String date = LocalDate.now().format(DATE_FMT);
        UUID tid = TenantContext.getTenantId();

        // On rare occasions (shared test DB, historical data, or rolled-back sequence gaps)
        // the generated code may collide with an existing business code. Retry a bounded
        // number of times by advancing the sequence instead of failing the whole operation.
        for (int attempt = 0; attempt < 50; attempt++) {
            Object seqObj = em.createNativeQuery(
                    "INSERT INTO doc_no_sequences (tenant_id, prefix, date_key, last_seq) " +
                    "VALUES (?1, ?2, ?3, 1) " +
                    "ON CONFLICT (tenant_id, prefix, date_key) " +
                    "DO UPDATE SET last_seq = doc_no_sequences.last_seq + 1 " +
                    "RETURNING last_seq")
                    .setParameter(1, tid).setParameter(2, prefix).setParameter(3, date)
                    .getSingleResult();
            long seq = ((Number) seqObj).longValue();
            String candidate = String.format("%s-%s-%05d", prefix, date, seq);
            if (!codeExists(prefix, candidate)) {
                return candidate;
            }
            log.warn("单据号 {} 已存在，顺延重试", candidate);
        }
        throw new IllegalStateException("无法生成唯一单据号，前缀=" + prefix);
    }

    private boolean codeExists(String prefix, String code) {
        String table = switch (prefix) {
            case "PO", "RPO", "RP" -> "purchase_orders";
            case "SO", "SR", "RS" -> "orders";
            case "GR", "GRR", "RGR" -> "receipts";
            case "GI", "GIR", "RGI" -> "sales_outs";
            case "ADJ" -> "inventory_adjustments";
            case "MV" -> "stock_moves";
            case "CT" -> "contracts";
            case "CT-APP" -> "contract_applications";
            default -> null;
        };
        if (table == null) {
            return false;
        }
        try {
            Long count = (Long) em.createNativeQuery("SELECT COUNT(1) FROM " + table + " WHERE code = ?1")
                    .setParameter(1, code)
                    .getSingleResult();
            return count != null && count > 0;
        } catch (Exception ex) {
            log.warn("检查单据号是否存在失败 table={} code={}: {}", table, code, ex.getMessage());
            return false;
        }
    }
}
