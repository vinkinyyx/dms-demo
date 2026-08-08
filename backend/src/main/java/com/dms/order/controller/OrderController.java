/*
 * 订单 REST 控制器：/api/orders 及状态动作。
 */
package com.dms.order.controller;

import com.dms.annotation.OperationLog;
import com.dms.common.ApiResponse;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.common.enums.OperationAction;
import com.dms.common.util.ExcelExportUtils;
import com.dms.common.util.ContentDispositionUtils;
import com.dms.execution.service.AutoDocGenerator;
import com.dms.execution.service.AuditLogService;
import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.order.dto.OrderCreateRequest;
import com.dms.order.dto.OrderDTO;
import com.dms.order.dto.TransferResponse;
import com.dms.order.entity.Order;
import com.dms.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Validated
public class OrderController {

    private final OrderService service;
    private final AutoDocGenerator autoDocGenerator;
    private final AuditLogService opLog;

    @GetMapping
    public ApiResponse<PageResult<Order>> list(@Valid PageQuery pageQuery) {
        return ApiResponse.ok(service.list(pageQuery));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderDTO> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @OperationLog(businessType = "order", action = OperationAction.CREATE, remark = "销售订单-创建")
    public ApiResponse<OrderDTO> create(@RequestBody OrderCreateRequest request) {
        OrderDTO dto = service.createOrder(request);
        try { if (dto != null && dto.getOrder() != null) opLog.log("order", dto.getOrder().getId(), "CREATE", "创建销售订单"); } catch (Exception ignored) {}
        return ApiResponse.ok(dto);
    }

    /**
     * 销售订单传输接口（同步）。
     *
     * <p>外部/上游系统通过本接口将销售订单一次性推送进 DMS。走 JWT 鉴权，
     * 同步事务（任何步骤失败整体回滚）。</p>
     *
     * <p>请求体复用 {@link OrderCreateRequest}：</p>
     *
     * <p>成功：{@code code=0}，{@code data.code} 即新订单编号（SO-20260806-00001）。</p>
     * <p>失败：{@code code!=0}，{@code message} 即失败原因。</p>
     */
    @PostMapping("/transfer")
    @OperationLog(businessType = "order", action = OperationAction.CREATE, remark = "销售订单传输")
    @Transactional
    public ApiResponse<TransferResponse> transfer(@RequestBody OrderCreateRequest request) {
        if (request == null) {
            return ApiResponse.fail(ErrorCode.PARAM_MISSING, "请求体不能为空");
        }
        if (request.getDealerId() == null) {
            return ApiResponse.fail(ErrorCode.PARAM_MISSING, "dealerId 不能为空");
        }
        if (request.getLines() == null || request.getLines().isEmpty()) {
            return ApiResponse.fail(ErrorCode.PARAM_MISSING, "销售订单明细不能为空");
        }
        try {
            OrderDTO dto = service.createOrder(request);
            if (dto == null || dto.getOrder() == null) {
                return ApiResponse.fail(ErrorCode.INTERNAL_ERROR, "订单创建失败，未返回数据");
            }
            String code = dto.getOrder().getCode();
            try { opLog.log("order", dto.getOrder().getId(), "CREATE", "销售订单传输"); } catch (Exception ignored) {}
            log.info("[transfer] 销售订单创建成功 id={} code={} remark={}",
                    dto.getOrder().getId(), code, request.getRemark());
            TransferResponse body = TransferResponse.builder()
                    .id(dto.getOrder().getId())
                    .code(code)
                    .orderType(dto.getOrder().getOrderType())
                    .status(dto.getOrder().getStatus())
                    .amount(dto.getOrder().getAmountInclTax())
                    .build();
            return ApiResponse.ok(body);
        } catch (BusinessException be) {
            log.warn("[transfer] 销售订单创建失败: {}", be.getMessage());
            return ApiResponse.fail(be.getErrorCode(), be.getMessage());
        } catch (Exception e) {
            log.error("[transfer] 销售订单创建异常", e);
            return ApiResponse.fail(ErrorCode.INTERNAL_ERROR, "销售订单创建失败: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/submit")
    @OperationLog(businessType = "order", action = OperationAction.UPDATE, remark = "销售订单-提交")
    public ApiResponse<Order> submit(@PathVariable Long id) {
        opLog.log("order", id, "SUBMIT", "提交审批");
        return ApiResponse.ok(service.submit(id));
    }

    /**
     * v3.4 增强：审批通过后自动生成销售出库草稿单
     */
    @PostMapping("/{id}/approve")
    @OperationLog(businessType = "order", action = OperationAction.APPROVE, remark = "销售订单-审批")
    @Transactional
    public ApiResponse<Order> approve(@PathVariable Long id) {
        Order order = service.approve(id);
        opLog.log("order", id, "APPROVE", "审批通过");
        try {
            Long soId = autoDocGenerator.createSalesOutForOrder(id);
            log.info("订单 {} 审批通过，自动生成销售出库单 {}", id, soId);
        } catch (Exception e) {
            log.warn("订单 {} 审批通过，但自动生成销售出库失败: {}", id, e.getMessage());
        }
        return ApiResponse.ok(order);
    }

    @PostMapping("/{id}/reject")
    @OperationLog(businessType = "order", action = OperationAction.REJECT, remark = "销售订单-驳回")
    public ApiResponse<Order> reject(@PathVariable Long id,
                                      @RequestBody(required = false) Map<String, String> body) {
        String reason = body == null ? null : body.get("reason");
        opLog.log("order", id, "REJECT", "驳回" + (reason != null ? "：" + reason : ""));
        return ApiResponse.ok(service.reject(id, reason));
    }

    @PostMapping("/{id}/cancel")
    @OperationLog(businessType = "order", action = OperationAction.UPDATE, remark = "销售订单-取消")
    public ApiResponse<Order> cancel(@PathVariable Long id) {
        opLog.log("order", id, "CANCEL", "取消订单");
        return ApiResponse.ok(service.cancel(id));
    }

    @PostMapping("/{id}/split")
    public ApiResponse<Order> split(@PathVariable Long id) {
        return ApiResponse.ok(service.split(id));
    }

    @DeleteMapping("/{id}")
    @OperationLog(businessType = "order", action = OperationAction.DELETE, remark = "销售订单-删除")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.deleteById(id);
        return ApiResponse.ok();
    }

    @GetMapping("/actions/export")
    public ResponseEntity<byte[]> export() throws Exception {
        PageQuery pq = new PageQuery();
        pq.setPage(1);
        pq.setSize(10000);
        java.util.List<Order> list = service.list(pq).getList();

        String[] headers = {"ID", "订单编号", "订单类型", "经销商", "医院", "手术", "状态", "含税金额", "优惠金额", "最终金额", "期望到货", "提交时间", "审批时间"};
        String[] fieldNames = {"id", "code", "orderType", "dealerName", "hospitalName", "surgeryName", "status", "amountInclTax", "discountAmount", "finalAmount", "expectedDate", "submittedAt", "approvedAt"};

        byte[] excelBytes = ExcelExportUtils.exportToExcel(list, headers, fieldNames);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDispositionUtils.attachment("销售订单列表.xlsx"))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(excelBytes);
    }
}
