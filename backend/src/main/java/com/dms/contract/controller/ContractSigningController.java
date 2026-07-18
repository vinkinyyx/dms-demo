/*
 * 合同签章、PDF 打印视图、ERP 归档相关端点
 * 覆盖 US-A-13 (5min PDF)、US-A-14 (短信 Token 签章)、US-A-15 (ERP 归档)
 *
 * 说明：
 *  1. PDF 生成使用 HTML 打印视图占位（用户已确认 V1 用 HTML 占位）
 *  2. 短信 Token 使用 Mock（打印到日志，也存 Redis 5 分钟）
 *  3. ERP 归档调用 mocks/erp 桩接口
 */
package com.dms.contract.controller;

import com.dms.common.ApiResponse;
import com.dms.common.util.TenantContext;
import com.dms.contract.entity.Contract;
import com.dms.contract.service.ContractService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/api/contracts")
@RequiredArgsConstructor
public class ContractSigningController {

    private final ContractService contractService;
    private final EntityManager em;
    private final StringRedisTemplate redis;

    /**
     * 合同 HTML 打印视图（用户浏览器打印为 PDF）
     * US-A-13：5 分钟内生成合同 PDF 视图，用户直接 Ctrl+P 打印
     */
    @GetMapping(value = "/{id}/print-view", produces = MediaType.TEXT_HTML_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<String> printView(@PathVariable Long id) {
        Contract c = contractService.get(id);
        if (c == null) {
            return ResponseEntity.notFound().build();
        }
        String html = renderContractHtml(c);
        return ResponseEntity.ok(html);
    }

    /**
     * 发送签章短信验证码（Mock）
     * US-A-14：短信验证码签章。V1 用 Mock，验证码打印到日志并存 Redis 5 分钟
     */
    @PostMapping("/{id}/send-sign-code")
    public ApiResponse<Map<String, Object>> sendSignCode(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        Contract c = contractService.get(id);
        if (c == null) return ApiResponse.fail(40404, "合同不存在");

        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1000000));
        String key = signCodeKey(id);
        redis.opsForValue().set(key, code, 5, TimeUnit.MINUTES);
        String phone = body != null ? String.valueOf(body.getOrDefault("phone", "138****0000")) : "138****0000";

        log.warn("[MOCK-SMS] 合同 #{} 签章验证码 = {}，接收手机 = {}，5 分钟有效", id, code, phone);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("contractId", id);
        res.put("phone", phone);
        res.put("expiresInSeconds", 300);
        res.put("mockCode", code);  // V1 Mock 环境返回给前端提示
        return ApiResponse.ok(res);
    }

    /**
     * 验证短信 Token 并完成签章
     */
    @PostMapping("/{id}/sign")
    @Transactional
    public ApiResponse<Map<String, Object>> sign(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        Contract c = contractService.get(id);
        if (c == null) return ApiResponse.fail(40404, "合同不存在");

        String code = String.valueOf(body.getOrDefault("code", ""));
        String key = signCodeKey(id);
        String actual = redis.opsForValue().get(key);
        if (actual == null || !actual.equals(code)) {
            return ApiResponse.fail(40007, "签章验证码错误或已过期");
        }
        redis.delete(key);

        // 记录签章日志到 audit_logs
        em.createNativeQuery(
            "INSERT INTO audit_logs (tenant_id, user_id, action, resource_type, resource_id, ip, at_time) " +
            "VALUES (:tid, :uid, 'CONTRACT_SIGN', 'contract', :rid, :ip, now())")
            .setParameter("tid", TenantContext.getTenantId())
            .setParameter("uid", TenantContext.getUserId())
            .setParameter("rid", String.valueOf(id))
            .setParameter("ip", "127.0.0.1")
            .executeUpdate();

        // 触发 ERP Mock 归档
        boolean archived = mockErpArchive(c);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("contractId", id);
        res.put("signedAt", LocalDateTime.now().toString());
        res.put("erpArchived", archived);
        res.put("erpDocId", archived ? ("ERP-DOC-" + id + "-" + System.currentTimeMillis()) : null);
        return ApiResponse.ok(res);
    }

    /**
     * 合同 ERP 归档触发（US-A-15）
     * V1 Mock：仅打日志 + 返回归档号
     */
    @PostMapping("/{id}/archive-to-erp")
    public ApiResponse<Map<String, Object>> archiveToErp(@PathVariable Long id) {
        Contract c = contractService.get(id);
        if (c == null) return ApiResponse.fail(40404, "合同不存在");
        boolean ok = mockErpArchive(c);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("contractId", id);
        res.put("archived", ok);
        res.put("erpDocId", ok ? ("ERP-DOC-" + id + "-" + System.currentTimeMillis()) : null);
        return ApiResponse.ok(res);
    }

    private String signCodeKey(Long id) {
        return "dms:sign-code:contract:" + id;
    }

    private boolean mockErpArchive(Contract c) {
        log.info("[MOCK-ERP] 归档合同 #{} 编号={} 到 ERP 系统", c.getId(), c.getCode());
        return true;
    }

    private String renderContractHtml(Contract c) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy年MM月dd日");
        String today = LocalDateTime.now().format(fmt);
        String validFrom = c.getValidFrom() == null ? "___________" : c.getValidFrom().format(fmt);
        String validTo = c.getValidTo() == null ? "___________" : c.getValidTo().format(fmt);
        String category = mapCategory(String.valueOf(c.getCategory()));

        return "<!DOCTYPE html><html lang='zh-CN'><head><meta charset='UTF-8'>" +
            "<title>合同打印 - " + c.getCode() + "</title>" +
            "<style>" +
            "@page { size: A4; margin: 20mm; }" +
            "body { font-family:'SimSun','宋体',serif; color:#000; line-height:1.8; padding:20px; max-width:800px; margin:0 auto; }" +
            "h1 { text-align:center; font-size:24pt; margin:30px 0; letter-spacing:8px; font-weight:bold; }" +
            ".sub { text-align:center; color:#333; margin-bottom:40px; font-size:12pt; }" +
            ".party { margin:16px 0; font-size:12pt; }" +
            ".party strong { display:inline-block; width:120px; }" +
            ".section { margin:20px 0; }" +
            ".section h3 { font-size:14pt; margin:16px 0 8px; }" +
            ".section p { font-size:11pt; text-indent:2em; margin:6px 0; }" +
            ".sign-area { margin-top:80px; display:flex; justify-content:space-between; }" +
            ".sign-box { width:45%; }" +
            ".sign-line { border-bottom:1px solid #000; height:60px; margin:20px 0 8px; }" +
            ".actions { position:fixed; top:10px; right:10px; }" +
            ".actions button { padding:8px 16px; background:#2C4B8E; color:#fff; border:none; border-radius:4px; cursor:pointer; margin-left:6px; }" +
            "@media print { .actions { display:none; } }" +
            "</style></head><body>" +
            "<div class='actions'>" +
            "<button onclick='window.print()'>🖨️ 打印/保存为 PDF</button>" +
            "<button onclick='window.close()'>关闭</button>" +
            "</div>" +
            "<h1>" + category + "合同</h1>" +
            "<div class='sub'>合同编号：" + c.getCode() + "</div>" +
            "<div class='party'><strong>甲方（供方）：</strong>" + defaultVal(null, "________________________________") + "</div>" +
            "<div class='party'><strong>乙方（经销商）：</strong>ID = " + c.getDealerId() + "</div>" +
            "<div class='party'><strong>签订日期：</strong>" + today + "</div>" +
            "<div class='section'>" +
            "<h3>第一条 合同类别</h3>" +
            "<p>" + category + "，甲乙双方本着平等自愿、诚实信用的原则，就相关事宜达成如下协议。</p>" +
            "</div>" +
            "<div class='section'>" +
            "<h3>第二条 有效期</h3>" +
            "<p>本合同自 " + validFrom + " 起至 " + validTo + " 止。</p>" +
            "</div>" +
            "<div class='section'>" +
            "<h3>第三条 双方权利与义务</h3>" +
            "<p>乙方作为甲方授权经销商，须按照合同附件约定的产品范围、区域范围、销售目标开展业务活动。</p>" +
            "<p>甲方应提供符合国家法规的合格产品，并保障乙方合法权益。</p>" +
            "</div>" +
            "<div class='section'>" +
            "<h3>第四条 违约责任</h3>" +
            "<p>任何一方违反本合同约定，应承担相应违约责任，包括但不限于赔偿另一方损失。</p>" +
            "</div>" +
            "<div class='section'>" +
            "<h3>第五条 争议解决</h3>" +
            "<p>因本合同引起的任何争议，双方应协商解决；协商不成，提交合同签订地人民法院管辖。</p>" +
            "</div>" +
            "<div class='sign-area'>" +
            "<div class='sign-box'><div>甲方（盖章）：</div><div class='sign-line'></div><div>日期：______年____月____日</div></div>" +
            "<div class='sign-box'><div>乙方（盖章）：</div><div class='sign-line'></div><div>日期：______年____月____日</div></div>" +
            "</div>" +
            "<div style='margin-top:40px;text-align:center;color:#999;font-size:9pt;'>本视图由 DMS 系统生成 · 状态：" + c.getStatus() + " · 打印时间：" + today + "</div>" +
            "</body></html>";
    }

    private String mapCategory(String cat) {
        if (cat == null) return "销售";
        switch (cat) {
            case "SALES": return "销售";
            case "AUTHORIZATION": return "授权";
            case "DISTRIBUTION": return "经销";
            default: return cat;
        }
    }

    private String defaultVal(String s, String def) {
        return (s == null || s.isBlank()) ? def : s;
    }
}
