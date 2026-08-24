package com.dms.openapi;

import com.dms.common.ApiResponse;
import com.dms.openapi.dto.ErpOutboundRequest;
import com.dms.openapi.dto.ErpOutboundResult;
import com.dms.openapi.service.ErpOutboundOpenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ERP 标准对接接口 —— 销售出库回传。
 *
 * <p>路径前缀 /open/api/erp，由 {@link OpenApiAuthFilter} 做 HMAC 鉴权并写入租户上下文。
 * <ul>
 *   <li>POST /open/api/erp/sales-outbounds            接收 ERP 出库回传（幂等）</li>
 *   <li>GET  /open/api/erp/sales-outbounds/{key}      查询某次回调处理结果</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/open/api/erp")
@RequiredArgsConstructor
public class OpenErpOutboundController {

    private final ErpOutboundOpenService erpOutboundOpenService;

    /** 接收 ERP 销售出库回传。 */
    @PostMapping("/sales-outbounds")
    public ApiResponse<ErpOutboundResult> receive(@Valid @RequestBody ErpOutboundRequest request) {
        log.info("[OPEN-ERP] 收到出库回传 requestId={} key={} order={}/{} direction={} lines={}",
                request.getRequestId(), request.getIdempotencyKey(),
                request.getSourceOrderCode(), request.getSourceOrderId(),
                request.getDirection(), request.getLines() == null ? 0 : request.getLines().size());
        return erpOutboundOpenService.receive(request);
    }

    /** 查询某次回调的处理结果。 */
    @GetMapping("/sales-outbounds/{idempotencyKey}")
    public ApiResponse<ErpOutboundResult> query(@PathVariable String idempotencyKey) {
        return erpOutboundOpenService.query(idempotencyKey);
    }
}