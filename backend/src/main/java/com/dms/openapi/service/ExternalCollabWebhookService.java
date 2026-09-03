package com.dms.openapi.service;

import com.dms.apilog.ApiCallLogService;
import com.dms.collab.ShippedLine;
import com.dms.common.util.TenantContext;
import com.dms.openapi.OpenApiAuthFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 厂家 DMS -> 平台外下游经销商 报文协同出站服务（v4.5.4）。
 *
 * <p>接口2：厂家销售出库发货 -> 推送 ship-notice，经销商系统生成待收货入库单。
 * <p>接口4：厂家红字出库（销退收货）发货 -> 推送 red-ship-notice，经销商系统生成红字销退入库。
 *
 * <p>可靠性设计：发货事务内只登记 open_collab_messages(OUT, PENDING) 台账并注册 afterCommit 回调；
 * 事务提交后异步 HMAC 签名推送；推送失败仅落 FAILED + next_retry_at，由定时任务重试，
 * <b>绝不因推送失败回滚或阻断发货</b>。物料编码经 open_partner_materials 反查外部编码，
 * 映射缺失的行整条消息挂起待重试（补齐映射后下一轮自动发出）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalCollabWebhookService {

    private static final int MAX_RETRY = 8;
    private static final long[] RETRY_BACKOFF_MIN = {5, 15, 30, 60, 120, 360, 720, 1440};

    private final EntityManager em;
    private final ObjectMapper objectMapper;
    private final ApiCallLogService apiCallLogService;
    private final PlatformTransactionManager transactionManager;

    private TransactionTemplate txNew;

    @PostConstruct
    void initTx() {
        this.txNew = new TransactionTemplate(transactionManager);
        this.txNew.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 发货事务内登记出站消息（正常出库 / 红字出库统一入口）。
     * 找不到经销商对接应用、无明细、物料映射缺失等情况均安全处理，不抛业务异常。
     *
     * @param salesOutId 厂家销售出库单 id
     * @param shipped    本次发货行（outLineId 用销售出库执行行 id，分批幂等去重）
     * @param red        true=红字出库（接口4）；false=正常出库（接口2）
     */
    public void registerOutbound(Long salesOutId, List<ShippedLine> shipped, boolean red) {
        try {
            UUID tid = TenantContext.getTenantId();
            if (tid == null || salesOutId == null || shipped == null || shipped.isEmpty()) return;

            List<Tuple> soRows = em.createNativeQuery(
                    "SELECT so.id, so.code, so.dealer_id, so.source_order_id, COALESCE(so.is_red,false) AS is_red, " +
                    "       so.logistics_company, so.tracking_no, so.remark " +
                    "FROM sales_outs so WHERE so.id=?1 AND so.tenant_id=?2 AND so.deleted_at IS NULL", Tuple.class)
                    .setParameter(1, salesOutId).setParameter(2, tid).getResultList();
            if (soRows.isEmpty()) return;
            Tuple so = soRows.get(0);
            Long dealerId = lng(so.get("dealer_id"));
            if (dealerId == null) return;

            Tuple app = findDealerApp(tid, dealerId);
            if (app == null) {
                log.info("[OPEN-COLLAB-OUT] 经销商 {} 未配置 DEALER 类型开放应用/webhook，跳过出库回传 so={}", dealerId, salesOutId);
                return;
            }
            Long appId = lng(app.get("id"));
            String appKey = str(app.get("app_key"));
            String dealerCode = str(app.get("dealer_code"));
            String webhookUrl = str(app.get("webhook_url"));
            if (webhookUrl == null || webhookUrl.isBlank()) {
                log.info("[OPEN-COLLAB-OUT] 应用 {} 未配置 webhook_url，跳过出库回传 so={}", appKey, salesOutId);
                return;
            }

            String msgType = red ? "RED_SHIP_NOTICE" : "SHIP_NOTICE";
            String outCode = str(so.get("code"));

            // 分批幂等：已登记过的执行行不再重复登记
            List<Long> doneLineIds = collectRegisteredOutLineIds(tid, appId, msgType, outCode);
            List<ShippedLine> pending = new ArrayList<>();
            for (ShippedLine sl : shipped) {
                if (sl.getOutLineId() != null && doneLineIds.contains(sl.getOutLineId())) continue;
                pending.add(sl);
            }
            if (pending.isEmpty()) {
                log.info("[OPEN-COLLAB-OUT] 出库单 {} 本次发货行均已登记回传，跳过", outCode);
                return;
            }

            List<Map<String, Object>> lineRefs = buildLineRefs(pending);
            Long msgId = insertPendingMessage(tid, appId, appKey, msgType, outCode, dealerCode, webhookUrl, lineRefs);
            if (msgId == null) return;

            registerAfterCommit(tid, msgId);
            log.info("[OPEN-COLLAB-OUT] 出库单 {} 登记出站消息 {} type={} lines={}", outCode, msgId, msgType, pending.size());
        } catch (Exception e) {
            log.error("[OPEN-COLLAB-OUT] 登记出站消息失败 soId={}（不影响发货）", salesOutId, e);
        }
    }

    /** 定时重试：扫描 PENDING/FAILED 且到达重试时间的出站消息。 */
    @Scheduled(fixedDelay = 60_000L, initialDelay = 30_000L)
    public void retryPending() {
        try {
            List<Tuple> rows = em.createNativeQuery(
                    "SELECT id FROM open_collab_messages " +
                    "WHERE direction='OUT' AND status IN ('PENDING','FAILED') " +
                    "AND COALESCE(retry_count,0) < :max AND (next_retry_at IS NULL OR next_retry_at <= now()) " +
                    "ORDER BY id LIMIT 20", Tuple.class)
                    .setParameter("max", MAX_RETRY)
                    .getResultList();
            for (Tuple r : rows) {
                Long id = lng(r.get("id"));
                try {
                    dispatchInContext(id, true);
                } catch (Exception e) {
                    log.warn("[OPEN-COLLAB-OUT] 重试消息 {} 失败: {}", id, e.getMessage());
                } finally {
                    TenantContext.clear();
                }
            }
        } catch (Exception e) {
            log.warn("[OPEN-COLLAB-OUT] 出站重试扫描异常: {}", e.getMessage());
        }
    }

    // ---------------- 推送核心 ----------------

    private void registerAfterCommit(UUID tid, Long msgId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        dispatchInContext(msgId, true);
                    } catch (Exception e) {
                        log.warn("[OPEN-COLLAB-OUT] afterCommit 推送消息 {} 异常: {}", msgId, e.getMessage());
                    } finally {
                        TenantContext.clear();
                    }
                }
            });
        } else {
            try {
                dispatchInContext(msgId, true);
            } finally {
                TenantContext.clear();
            }
        }
    }

    /**
     * 推送入口：先取消息租户并设置 TenantContext（afterCommit 回调/定时线程均无上下文），
     * 组装+推送+台账回写整体在 REQUIRES_NEW 事务内执行（调用点可能已脱离发货事务）。
     */
    private void dispatchInContext(Long msgId, boolean requireTx) {
        List<Tuple> headRows = em.createNativeQuery(
                "SELECT tenant_id, status FROM open_collab_messages WHERE id=?1 AND direction='OUT'", Tuple.class)
                .setParameter(1, msgId).getResultList();
        if (headRows.isEmpty()) return;
        Tuple head = headRows.get(0);
        if ("SUCCESS".equals(str(head.get("status")))) return;
        UUID tid = toUuid(head.get("tenant_id"));
        if (tid != null) TenantContext.setTenantId(tid);
        if (requireTx) {
            txNew.executeWithoutResult(status -> dispatchMessage(msgId));
        } else {
            dispatchMessage(msgId);
        }
    }

    /** 组装报文并推送（必须在事务内调用；台账状态独立回写）。 */
    private void dispatchMessage(Long msgId) {
        List<Tuple> rows = em.createNativeQuery(
                "SELECT id, tenant_id, app_id, app_key, msg_type, partner_doc_no, dealer_code, webhook_url, " +
                "       CAST(line_refs AS text) AS line_refs, status, retry_count " +
                "FROM open_collab_messages WHERE id=?1 AND direction='OUT'", Tuple.class)
                .setParameter(1, msgId).getResultList();
        if (rows.isEmpty()) return;
        Tuple msg = rows.get(0);
        if ("SUCCESS".equals(str(msg.get("status")))) return;

        UUID tid = toUuid(msg.get("tenant_id"));
        if (tid != null) TenantContext.setTenantId(tid);
        Long appId = lng(msg.get("app_id"));
        String msgType = str(msg.get("msg_type"));
        boolean red = "RED_SHIP_NOTICE".equals(msgType);
        String outCode = str(msg.get("partner_doc_no"));

        Tuple app = appId == null ? null : findAppById(tid, appId);
        if (app == null) {
            markFailed(msgId, retryCountOf(msgId) + 1, "开放应用不存在或已停用");
            return;
        }
        String webhookUrl = str(app.get("webhook_url"));
        if (webhookUrl == null || webhookUrl.isBlank()) {
            markFailed(msgId, retryCountOf(msgId) + 1, "应用未配置 webhook_url");
            return;
        }
        String secret = str(app.get("webhook_secret"));
        if (secret == null || secret.isBlank()) secret = str(app.get("app_secret"));

        // 出库单 + 关联销售订单
        Tuple so = loadSalesOut(tid, outCode, red);
        if (so == null) {
            markRetryable(msgId, "出库单暂不可见，等待后续重试: " + outCode);
            return;
        }
        String orderCode = resolveOrderCode(tid, so, red);
        String dealerCode = str(msg.get("dealer_code"));

        // 行明细 -> 外部物料编码
        List<Long> outLineIds = parseOutLineIds(msg.get("line_refs"));
        List<Tuple> outLines = loadOutLines(tid, outCode, outLineIds, red);
        List<Map<String, Object>> lines = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        int lineNo = 1;
        for (Tuple l : outLines) {
            Long productId = lng(l.get("product_id"));
            Tuple map = findMaterialMappingByProduct(tid, appId, productId);
            String materialCode = map == null ? str(l.get("product_code")) : str(map.get("external_code"));
            String materialName = map == null ? str(l.get("product_name")) : str(map.get("external_name"));
            if (map == null) {
                missing.add(str(l.get("product_code")));
                materialCode = str(l.get("product_code"));
            }
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("lineNo", lineNo++);
            line.put("materialCode", materialCode);
            if (materialName != null) line.put("materialName", materialName);
            line.put("qty", bd(l.get("qty")));
            String unit = str(l.get("unit"));
            if (unit != null) line.put("unit", unit);
            String batchNo = str(l.get("batch_no"));
            if (batchNo != null) line.put("batchNo", batchNo);
            String serialNo = str(l.get("serial_no"));
            if (serialNo != null) line.put("serialNo", serialNo);
            lines.add(line);
        }
        if (!missing.isEmpty()) {
            markRetryable(msgId, "以下厂家物料未配置外部物料映射，待映射补齐后自动重试: " + String.join("、", missing));
            return;
        }

        // 组装报文
        Map<String, Object> body = new LinkedHashMap<>();
        Map<String, Object> header = new LinkedHashMap<>();
        String shipDate = LocalDate.now().toString();
        if (red) {
            header.put("redOutboundNo", outCode);
            header.put("redSalesReturnNo", orderCode);
        } else {
            header.put("outboundNo", outCode);
            header.put("salesOrderNo", orderCode);
        }
        header.put("manufacturerCode", resolveTenantCode(tid));
        header.put("dealerCode", dealerCode);
        header.put("shipDate", shipDate);
        String logistics = str(so.get("logistics_company"));
        String tracking = str(so.get("tracking_no"));
        if (logistics != null) header.put("logisticsCompany", logistics);
        if (tracking != null) header.put("trackingNo", tracking);
        String remark = str(so.get("remark"));
        if (remark != null) header.put("remark", remark);
        body.put("header", header);
        body.put("lines", lines);

        String payload;
        try {
            payload = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            markFailed(msgId, retryCountOf(msgId) + 1, "报文序列化失败: " + e.getMessage());
            return;
        }

        // HMAC 签名（与入站 OpenApiAuthFilter 同一套规则；path 取 webhook 路径）
        String path = safePath(webhookUrl);
        String ts = String.valueOf(System.currentTimeMillis());
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String bodyHash = OpenApiAuthFilter.sha256Hex(payload.getBytes(StandardCharsets.UTF_8));
        String signString = "POST\n" + path + "\n" + ts + "\n" + nonce + "\n" + bodyHash;
        String signature = OpenApiAuthFilter.hmacSha256Hex(secret, signString);

        ApiCallLogService.ExternalCall call = new ApiCallLogService.ExternalCall();
        call.system = "DEALER_ERP";
        call.endpoint = red ? "sales-returns/ship-notice" : "sales-outs/ship-notice";
        call.url = webhookUrl;
        call.method = "POST";
        call.body = payload;
        call.appKey = str(app.get("app_key"));
        call.traceId = "collab-out-" + msgId;
        call.connectTimeoutMs = 10000;
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json;charset=UTF-8");
        headers.put("X-App-Key", call.appKey);
        headers.put("X-Timestamp", ts);
        headers.put("X-Nonce", nonce);
        headers.put("X-Signature", signature);
        call.headers = headers;

        ApiCallLogService.ExternalResult result = apiCallLogService.callExternal(call);

        em.createNativeQuery(
                "UPDATE open_collab_messages SET request_body=?1, response_body=?2, http_status=?3, last_sent_at=now(), " +
                "updated_at=now() WHERE id=?4")
                .setParameter(1, payload)
                .setParameter(2, result.body == null ? (result.error == null ? null : String.valueOf(result.error)) : result.body)
                .setParameter(3, result.statusCode > 0 ? result.statusCode : null)
                .setParameter(4, msgId)
                .executeUpdate();

        if (result.success && isBusinessOk(result.body)) {
            em.createNativeQuery(
                    "UPDATE open_collab_messages SET status='SUCCESS', error_msg=NULL, next_retry_at=NULL, updated_at=now() WHERE id=?1")
                    .setParameter(1, msgId).executeUpdate();
            log.info("[OPEN-COLLAB-OUT] 消息 {} 推送成功 {} -> {}", msgId, outCode, webhookUrl);
        } else {
            String err = result.success ? "经销商返回业务失败: " + abbreviate(result.body)
                    : "HTTP " + result.statusCode + " " + abbreviate(result.error != null ? result.error : result.body);
            markFailed(msgId, retryCountOf(msgId) + 1, err);
            log.warn("[OPEN-COLLAB-OUT] 消息 {} 推送失败: {}", msgId, err);
        }
    }

    // ---------------- 台账 ----------------

    private Long insertPendingMessage(UUID tid, Long appId, String appKey, String msgType, String outCode,
                                      String dealerCode, String webhookUrl, List<Map<String, Object>> lineRefs) {
        try {
            String refsJson = objectMapper.writeValueAsString(lineRefs);
            Object ins = em.createNativeQuery(
                    "INSERT INTO open_collab_messages (tenant_id, app_id, app_key, direction, msg_type, partner_doc_no, " +
                    "local_doc_no, dealer_code, webhook_url, line_refs, status, retry_count, created_at, updated_at) " +
                    "VALUES (?1,?2,?3,'OUT',?4,?5,?5,?6,?7,CAST(?8 AS jsonb),'PENDING',0,now(),now()) RETURNING id")
                    .setParameter(1, tid).setParameter(2, appId).setParameter(3, appKey)
                    .setParameter(4, msgType).setParameter(5, outCode)
                    .setParameter(6, dealerCode).setParameter(7, webhookUrl)
                    .setParameter(8, refsJson)
                    .getSingleResult();
            return ((Number) ins).longValue();
        } catch (Exception e) {
            log.warn("[OPEN-COLLAB-OUT] 登记出站台账失败 type={} out={}: {}", msgType, outCode, e.getMessage());
            return null;
        }
    }

    private List<Long> collectRegisteredOutLineIds(UUID tid, Long appId, String msgType, String outCode) {
        List<Long> ids = new ArrayList<>();
        List<Tuple> rows = em.createNativeQuery(
                "SELECT CAST(line_refs AS text) AS line_refs FROM open_collab_messages " +
                "WHERE tenant_id=?1 AND app_id=?2 AND direction='OUT' AND msg_type=?3 AND partner_doc_no=?4", Tuple.class)
                .setParameter(1, tid).setParameter(2, appId).setParameter(3, msgType).setParameter(4, outCode)
                .getResultList();
        for (Tuple r : rows) {
            ids.addAll(parseOutLineIds(r.get("line_refs")));
        }
        return ids;
    }

    /** 推送失败（HTTP/业务错误/配置终态）：计数退避，达到上限后停止重试（next_retry_at=NULL，需人工介入）。 */
    private void markFailed(Long msgId, int retryCount, String error) {
        boolean giveUp = retryCount >= MAX_RETRY;
        long backoffMin = RETRY_BACKOFF_MIN[Math.min(Math.max(retryCount - 1, 0), RETRY_BACKOFF_MIN.length - 1)];
        String nextSql = giveUp ? "next_retry_at=NULL"
                : "next_retry_at=now() + (" + backoffMin + " * interval '1 minute')";
        em.createNativeQuery(
                "UPDATE open_collab_messages SET status='FAILED', error_msg=?1, retry_count=?2, " + nextSql + ", " +
                "updated_at=now() WHERE id=?3")
                .setParameter(1, abbreviate(error))
                .setParameter(2, retryCount)
                .setParameter(3, msgId)
                .executeUpdate();
    }

    /**
     * 配置/数据态失败（物料映射未维护、出库单暂不可见）：不消耗重试次数，
     * 固定 30 分钟后低频重试；运维补齐映射后自动恢复推送。
     */
    private void markRetryable(Long msgId, String error) {
        em.createNativeQuery(
                "UPDATE open_collab_messages SET status='FAILED', error_msg=?1, " +
                "next_retry_at=now() + (30 * interval '1 minute'), updated_at=now() WHERE id=?2")
                .setParameter(1, abbreviate(error))
                .setParameter(2, msgId)
                .executeUpdate();
    }

    /** 厂家编码：取厂家租户 tenants.code。 */
    private String resolveTenantCode(UUID tid) {
        if (tid == null) return null;
        List<Tuple> rows = em.createNativeQuery("SELECT code FROM tenants WHERE id=?1", Tuple.class)
                .setParameter(1, tid).getResultList();
        return rows.isEmpty() ? null : str(rows.get(0).get("code"));
    }

    // ---------------- 查询助手 ----------------

    private Tuple findDealerApp(UUID tid, Long dealerId) {
        List<Tuple> rows = em.createNativeQuery(
                "SELECT id, app_key, app_secret, dealer_code, webhook_url, webhook_secret " +
                "FROM open_app WHERE tenant_id=?1 AND dealer_id=?2 AND partner_type='DEALER' " +
                "AND status='active' AND webhook_url IS NOT NULL AND webhook_url <> '' ORDER BY id LIMIT 1", Tuple.class)
                .setParameter(1, tid).setParameter(2, dealerId).getResultList();
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Tuple findAppById(UUID tid, Long appId) {
        List<Tuple> rows = em.createNativeQuery(
                "SELECT id, app_key, app_secret, dealer_code, webhook_url, webhook_secret " +
                "FROM open_app WHERE id=?1 AND tenant_id=?2 AND status='active'", Tuple.class)
                .setParameter(1, appId).setParameter(2, tid).getResultList();
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Tuple loadSalesOut(UUID tid, String outCode, boolean red) {
        List<Tuple> rows = em.createNativeQuery(
                "SELECT so.id, so.code, so.dealer_id, so.source_order_id, so.logistics_company, so.tracking_no, so.remark " +
                "FROM sales_outs so WHERE so.tenant_id=?1 AND so.code=?2 AND COALESCE(so.is_red,false)=?3 " +
                "AND so.deleted_at IS NULL ORDER BY so.id DESC LIMIT 1", Tuple.class)
                .setParameter(1, tid).setParameter(2, outCode).setParameter(3, red).getResultList();
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String resolveOrderCode(UUID tid, Tuple so, boolean red) {
        Long orderId = lng(so.get("source_order_id"));
        if (orderId == null) return null;
        List<Tuple> rows = em.createNativeQuery(
                "SELECT code FROM orders WHERE id=?1 AND tenant_id=?2", Tuple.class)
                .setParameter(1, orderId).setParameter(2, tid).getResultList();
        return rows.isEmpty() ? null : str(rows.get(0).get("code"));
    }

    /** 取本次发货执行行（sales_out_lines），红字/正常同表；用 outLineIds 限定本批。 */
    private List<Tuple> loadOutLines(UUID tid, String outCode, List<Long> outLineIds, boolean red) {
        String sql = "SELECT sol.id, sol.product_id, sol.qty, sol.batch_no, sol.serial_no, " +
                "       p.code AS product_code, p.name_cn AS product_name, p.unit AS unit " +
                "FROM sales_out_lines sol " +
                "JOIN sales_outs so ON so.id = sol.sales_out_id " +
                "LEFT JOIN products p ON p.id = sol.product_id " +
                "WHERE so.tenant_id=?1 AND so.code=?2 AND COALESCE(so.is_red,false)=?3 AND so.deleted_at IS NULL";
        jakarta.persistence.Query q = em.createNativeQuery(sql + (outLineIds.isEmpty() ? "" : " AND sol.id IN (?4)")
                + " ORDER BY sol.seq, sol.id", Tuple.class)
                .setParameter(1, tid).setParameter(2, outCode).setParameter(3, red);
        if (!outLineIds.isEmpty()) q.setParameter(4, outLineIds);
        @SuppressWarnings("unchecked")
        List<Tuple> rs = q.getResultList();
        return rs;
    }

    private Tuple findMaterialMappingByProduct(UUID tid, Long appId, Long productId) {
        if (productId == null) return null;
        List<Tuple> rows = em.createNativeQuery(
                "SELECT external_code, external_name FROM open_partner_materials " +
                "WHERE tenant_id=?1 AND app_id=?2 AND product_id=?3 AND status='active' AND deleted_at IS NULL LIMIT 1", Tuple.class)
                .setParameter(1, tid).setParameter(2, appId).setParameter(3, productId).getResultList();
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<Map<String, Object>> buildLineRefs(List<ShippedLine> lines) {
        List<Map<String, Object>> refs = new ArrayList<>();
        for (ShippedLine sl : lines) {
            Map<String, Object> ref = new LinkedHashMap<>();
            if (sl.getOutLineId() != null) ref.put("outLineId", sl.getOutLineId());
            if (sl.getProductId() != null) ref.put("productId", sl.getProductId());
            if (sl.getProductCode() != null) ref.put("productCode", sl.getProductCode());
            if (sl.getQty() != null) ref.put("qty", sl.getQty());
            refs.add(ref);
        }
        return refs;
    }

    @SuppressWarnings("unchecked")
    private List<Long> parseOutLineIds(Object lineRefs) {
        List<Long> ids = new ArrayList<>();
        if (lineRefs == null) return ids;
        try {
            String json = lineRefs instanceof String ? (String) lineRefs : objectMapper.writeValueAsString(lineRefs);
            for (com.fasterxml.jackson.databind.JsonNode n : objectMapper.readTree(json)) {
                if (n.hasNonNull("outLineId")) ids.add(n.get("outLineId").asLong());
            }
        } catch (Exception e) {
            log.warn("[OPEN-COLLAB-OUT] 解析 line_refs 失败: {}", e.getMessage());
        }
        return ids;
    }

    private boolean isBusinessOk(String body) {
        if (body == null || body.isBlank()) return true;
        try {
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(body);
            com.fasterxml.jackson.databind.JsonNode code = node.get("code");
            return code == null || code.asInt(-1) == 0;
        } catch (Exception e) {
            return true;
        }
    }

    private int retryCountOf(Long msgId) {
        List<Tuple> rows = em.createNativeQuery(
                "SELECT COALESCE(retry_count,0) AS rc FROM open_collab_messages WHERE id=?1", Tuple.class)
                .setParameter(1, msgId).getResultList();
        Long rc = lng(rows.get(0).get("rc"));
        return rc == null ? 0 : rc.intValue();
    }

    private String safePath(String url) {
        try {
            return java.net.URI.create(url).getPath();
        } catch (Exception e) {
            return "/";
        }
    }

    private String abbreviate(String s) {
        if (s == null) return null;
        return s.length() > 500 ? s.substring(0, 500) : s;
    }

    private Long lng(Object o) { return o == null ? null : ((Number) o).longValue(); }
    private BigDecimal bd(Object o) { return o == null ? null : new BigDecimal(String.valueOf(o)); }
    private String str(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }
    private UUID toUuid(Object o) {
        if (o == null) return null;
        try { return o instanceof UUID ? (UUID) o : UUID.fromString(String.valueOf(o)); }
        catch (Exception e) { return null; }
    }
}
