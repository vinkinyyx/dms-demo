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
                                                       @Valid PageQuery pageQuery) {
        return ApiResponse.ok(service.list(isRead, pageQuery));
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
    public ApiResponse<Map<String, Object>> readAll() {
        int cnt = service.markAllRead();
        return ApiResponse.ok(Map.of("updated", cnt));
    }
}
