/*
 * 单据编号生成器，格式 {PREFIX}-YYYYMMDD-{5位0填充连续流水}，如 PO-20260719-00001。
 * v3.4.12: 改用 DB 序列表 doc_no_sequences，保证同租户同日连续且不与历史数据撞号。
 */
package com.dms.common.util;

import com.dms.common.util.TenantContext;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

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

        // UPSERT 原子自增，返回新序号
        Object seqObj = em.createNativeQuery(
                "INSERT INTO doc_no_sequences (tenant_id, prefix, date_key, last_seq) " +
                "VALUES (?1, ?2, ?3, 1) " +
                "ON CONFLICT (tenant_id, prefix, date_key) " +
                "DO UPDATE SET last_seq = doc_no_sequences.last_seq + 1 " +
                "RETURNING last_seq")
                .setParameter(1, tid).setParameter(2, prefix).setParameter(3, date)
                .getSingleResult();
        long seq = ((Number) seqObj).longValue();
        return String.format("%s-%s-%05d", prefix, date, seq);
    }
}
