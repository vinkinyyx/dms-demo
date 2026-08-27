package com.dms.v4;

import com.dms.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v4")
@RequiredArgsConstructor
public class V4Controller {
    private final V4OrderService orderService;
    private final V4ErpService erpService;

    @PostMapping("/calc/preview")
    public ApiResponse<Map<String, Object>> calcPreview(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(orderService.calcPreview(body));
    }

    @PostMapping("/erp/outbound-callbacks")
    public ApiResponse<Map<String, Object>> callback(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(erpService.receiveOutbound(body));
    }

    @PostMapping("/sales-orders/{id}/simulate-ship")
    public ApiResponse<Map<String, Object>> simulate(@PathVariable Long id) {
        return ApiResponse.ok(erpService.simulateShip(id));
    }
}