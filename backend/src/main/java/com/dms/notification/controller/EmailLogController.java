/*
 * Email log controller.
 */
package com.dms.notification.controller;

import com.dms.common.ApiResponse;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.util.TenantContext;
import com.dms.notification.entity.EmailLog;
import com.dms.notification.service.EmailLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/email-logs")
@RequiredArgsConstructor
public class EmailLogController {
    private final EmailLogService service;
    private final JavaMailSender mailSender;

    @Value("${dms.mail.from:${spring.mail.username:}}")
    private String from;

    @GetMapping
    public ApiResponse<PageResult<EmailLog>> list(@Valid PageQuery pageQuery,
                                                  @RequestParam(required = false) String status) {
        return ApiResponse.ok(service.list(status, pageQuery));
    }

    @PostMapping("/test")
    public ApiResponse<EmailLog> sendTest(@RequestBody Map<String, String> body) {
        String to = body == null ? null : body.get("to");
        if (to == null || to.isBlank()) {
            return ApiResponse.fail(40002, "\u6536\u4ef6\u4eba\u4e0d\u80fd\u4e3a\u7a7a");
        }
        String subject = "\u005b\u0044\u004d\u0053\u0020\u6d4b\u8bd5\u005d\u0020\u90ae\u4ef6\u53d1\u9001\u6d4b\u8bd5";
        String text = "\u8fd9\u662f\u4e00\u5c01\u6765\u81ea\u0020\u0044\u004d\u0053\u0020\u7684\u6d4b\u8bd5\u90ae\u4ef6\uff0c\u6536\u5230\u8bf4\u660e\u0020\u0053\u004d\u0054\u0050\u0020\u90ae\u4ef6\u53d1\u9001\u6b63\u5e38\u3002";
        boolean success = false;
        String error = null;
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
            success = true;
        } catch (Exception e) {
            error = e.getMessage();
        }
        UUID tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();
        EmailLog log = service.log(tenantId, userId, from, to, subject, text, "TEST", "test", success, error);
        return ApiResponse.ok(log);
    }
}
