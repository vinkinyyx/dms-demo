package com.dms.openapi;

import com.dms.common.ApiResponse;
import com.dms.openapi.dto.collab.CollabPurchaseOrderRequest;
import com.dms.openapi.dto.collab.CollabPurchaseReturnRequest;
import com.dms.openapi.dto.collab.CollabSubmitResult;
import com.dms.openapi.service.ExternalCollabOpenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台外下游经销商报文协同接口（v4.5.4）。
 *
 * <p>路径前缀 /open/api/collab，由 {@link OpenApiAuthFilter} 做 HMAC-SHA256 机器凭证鉴权
 * （X-App-Key/X-Timestamp/X-Nonce/X-Signature），校验通过后写入厂家租户上下文。
 * <ul>
 *   <li>POST /open/api/collab/purchase-orders/submit    接口1：经销商采购订单提交（-> 厂家销售订单草稿）</li>
 *   <li>POST /open/api/collab/purchase-returns/submit  接口3：经销商采退单提交（-> 厂家红字销退订单草稿）</li>
 * </ul>
 * 接口2/接口4 为厂家发货后主动推送经销商 webhook，见 {@link com.dms.openapi.service.ExternalCollabWebhookService}。
 */
@Slf4j
@RestController
@RequestMapping("/open/api/collab")
@RequiredArgsConstructor
public class OpenCollabController {

    private final ExternalCollabOpenService externalCollabOpenService;

    /** 接口1：经销商采购订单提交。 */
    @PostMapping("/purchase-orders/submit")
    public ApiResponse<CollabSubmitResult> submitPurchaseOrder(@Valid @RequestBody CollabPurchaseOrderRequest request) {
        log.info("[OPEN-COLLAB] 采购订单提交 appKey={} poNo={} dealerCode={} lines={}",
                currentAppKey(), request.getHeader().getPoNo(),
                request.getHeader().getDealerCode(), request.getLines() == null ? 0 : request.getLines().size());
        return externalCollabOpenService.submitPurchaseOrder(request);
    }

    /** 接口3：经销商采购退货单提交。 */
    @PostMapping("/purchase-returns/submit")
    public ApiResponse<CollabSubmitResult> submitPurchaseReturn(@Valid @RequestBody CollabPurchaseReturnRequest request) {
        log.info("[OPEN-COLLAB] 采退单提交 appKey={} returnNo={} dealerCode={} lines={}",
                currentAppKey(), request.getHeader().getReturnNo(),
                request.getHeader().getDealerCode(), request.getLines() == null ? 0 : request.getLines().size());
        return externalCollabOpenService.submitPurchaseReturn(request);
    }

    private String currentAppKey() {
        Object k = com.dms.common.util.TenantContext.get("appKey");
        return k == null ? "-" : String.valueOf(k);
    }
}
