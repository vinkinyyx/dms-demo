package com.dms.report.subscription;

import com.dms.common.util.TenantContext;
import com.dms.report.service.ReportService;
import com.dms.system.service.SystemSettingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportSubscriptionService {

    private final ReportSubscriptionRepository repository;
    private final ReportService reportService;
    private final JavaMailSender mailSender;
    private final SystemSettingService systemSettingService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${dms.mail.from:${spring.mail.username:}}")
    private String from;

    @Transactional(readOnly = true)
    public List<ReportSubscription> list() {
        return repository.findByTenantIdOrderByIdDesc(TenantContext.getTenantId());
    }

    @Transactional
    public ReportSubscription save(ReportSubscription sub) {
        sub.setTenantId(TenantContext.getTenantId());
        sub.setCreatedBy(TenantContext.getUserId());
        sub.setUpdatedAt(OffsetDateTime.now());
        return repository.save(sub);
    }

    @Transactional
    public void delete(Long id) {
        repository.findById(id).ifPresent(repository::delete);
    }

    @Transactional
    public void toggle(Long id) {
        repository.findById(id).ifPresent(s -> { s.setActive(!Boolean.TRUE.equals(s.getActive())); s.setUpdatedAt(OffsetDateTime.now()); });
    }

    /** 每天 08:00 检查并发送到期订阅（DAILY/WEEKLY/MONTHLY） */
    @Scheduled(cron = "0 0 8 * * *")
    public void scheduleDispatch() {
        if (!systemSettingService.isReportMailEnabled()) {
            log.info("定时报表邮件已被开关关闭，跳过本次发送");
            return;
        }
        List<ReportSubscription> actives = repository.findByActiveTrue();
        LocalDate today = LocalDate.now();
        for (ReportSubscription s : actives) {
            try {
                if (shouldRun(s, today)) dispatch(s);
            } catch (Exception e) {
                log.error("报表订阅发送失败 id={} err={}", s.getId(), e.getMessage());
            }
        }
    }

    boolean shouldRun(ReportSubscription s, LocalDate today) {
        String cron = s.getCronExpr();
        if ("DAILY".equalsIgnoreCase(cron)) return true;
        if ("WEEKLY".equalsIgnoreCase(cron)) return today.getDayOfWeek().getValue() == 1;
        if ("MONTHLY".equalsIgnoreCase(cron)) return today.getDayOfMonth() == 1;
        return false;
    }

    @Transactional
    public void dispatch(ReportSubscription s) {
        TenantContext.setTenantId(s.getTenantId());
        try {
            Map<String, Object> params = parseParams(s.getParams());
            List<Map<String, Object>> rows = reportService.query(s.getReportType(), params);
            String csv = toCsv(rows);
            if (systemSettingService.isReportMailEnabled()) sendMail(s, csv);
            s.setLastRunAt(OffsetDateTime.now());
            s.setLastStatus("SUCCESS");
            s.setLastError(null);
        } catch (Exception e) {
            s.setLastStatus("FAILED");
            s.setLastError(e.getMessage());
            log.error("dispatch report sub {} failed", s.getId(), e);
        } finally {
            TenantContext.clear();
            repository.save(s);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseParams(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try { return objectMapper.readValue(json, Map.class); } catch (Exception e) { return Map.of(); }
    }

    private String toCsv(List<Map<String, Object>> rows) {
        StringBuilder sb = new StringBuilder();
        if (rows.isEmpty()) return "";
        // BOM for Excel
        sb.append('\ufeff');
        Set<String> cols = new LinkedHashSet<>(rows.get(0).keySet());
        sb.append(String.join(",", cols)).append('\n');
        for (Map<String, Object> r : rows) {
            List<String> vals = new ArrayList<>();
            for (String c : cols) {
                Object v = r.get(c);
                vals.add(v == null ? "" : "\"" + String.valueOf(v).replace("\"", "\"\"") + "\"");
            }
            sb.append(String.join(",", vals)).append('\n');
        }
        return sb.toString();
    }

    private void sendMail(ReportSubscription s, String csv) throws Exception {
        if (from == null || from.isBlank()) return;
        var msg = mailSender.createMimeMessage();
        var helper = new MimeMessageHelper(msg, MimeMessageHelper.MULTIPART_MODE_MIXED, StandardCharsets.UTF_8.name());
        helper.setFrom(from);
        helper.setTo(subscriberEmails(s));
        helper.setSubject("[DMS] 报表订阅：" + s.getName() + " " + LocalDate.now());
        helper.setText("您订阅的《" + s.getName() + "》已生成，共 " + countLines(csv) + " 行，详见附件。");
        helper.addAttachment(s.getName() + "_" + LocalDate.now() + ".csv",
                () -> new java.io.ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
        mailSender.send(msg);
    }

    private String[] subscriberEmails(ReportSubscription s) {
        if (s.getEmails() == null || s.getEmails().isBlank()) return new String[]{from};
        return s.getEmails().split("[,;\\s]+");
    }

    private int countLines(String csv) {
        if (csv == null) return 0;
        long n = csv.chars().filter(c -> c == '\n').count();
        return (int) Math.max(n - 1, 0);
    }
}