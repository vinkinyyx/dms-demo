/*
 * 通知控制器：/api/notifications
 */
package com.dms.notification.controller;

import com.dms.common.ApiResponse;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.notification.entity.Notification;
import com.dms.notification.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Validated
public class NotificationController {

    private final NotificationService service;

    @GetMapping
    public ApiResponse<PageResult<Notification>> list(@RequestParam(required = false) Boolean isRead,
                                                       @RequestParam(required = false) String refType,
                                                       @Valid PageQuery pageQuery) {
        return ApiResponse.ok(service.list(isRead, refType, pageQuery));
    }

    @GetMapping("/{id}")
    public ApiResponse<Notification> detail(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @GetMapping("/unread-count")
    public ApiResponse<java.util.Map<String, Object>> unreadCount() {
        return ApiResponse.ok(java.util.Map.of("count", service.unreadCount()));
    }

    @PostMapping
    public ApiResponse<Notification> send(@RequestBody Notification req) {
        return ApiResponse.ok(service.send(req));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<Void> read(@PathVariable Long id) {
        service.markRead(id);
        return ApiResponse.ok();
    }

    @PostMapping("/read-all")
    public ApiResponse<Map<String, Object>> readAll(@RequestParam(required = false) String refType) {
        int cnt = service.markAllRead(refType);
        return ApiResponse.ok(Map.of("updated", cnt));
    }

    @PostMapping("/mark-all-read")
    public ApiResponse<Map<String, Object>> markAllReadAlias(@RequestParam(required = false) String refType) {
        return readAll(refType);
    }
}
