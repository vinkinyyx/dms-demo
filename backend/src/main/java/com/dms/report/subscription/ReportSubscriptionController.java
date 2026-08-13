package com.dms.report.subscription;

import com.dms.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/report-subscriptions")
@RequiredArgsConstructor
public class ReportSubscriptionController {

    private final ReportSubscriptionService service;

    @GetMapping
    public ApiResponse<List<ReportSubscription>> list() {
        return ApiResponse.ok(service.list());
    }

    @PostMapping
    public ApiResponse<ReportSubscription> save(@RequestBody ReportSubscription sub) {
        return ApiResponse.ok(service.save(sub));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/toggle")
    public ApiResponse<Void> toggle(@PathVariable Long id) {
        service.toggle(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/run-now")
    public ApiResponse<Void> runNow(@PathVariable Long id) {
        service.list().stream().filter(s -> s.getId().equals(id)).findFirst().ifPresent(service::dispatch);
        return ApiResponse.ok();
    }
}