package com.dms.openapi.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * ERP 销售出库回传请求（业务编码报文，由 DMS 解析内部 ID）。
 */
@Data
public class ErpOutboundRequest {

    /** ERP 请求流水号，仅用于日志追溯，可选。 */
    private String requestId;

    /** 幂等键：ERP 出库单号或 UUID，必填。重复请求返回首次结果。 */
    @NotBlank(message = "idempotencyKey 不能为空")
    @Size(max = 128, message = "idempotencyKey 长度不能超过 128")
    private String idempotencyKey;

    /** DMS 销售订单号 code（红字时为销退单号）；与 sourceOrderId 二选一。 */
    private String sourceOrderCode;

    /** DMS 销售订单内部 ID；与 sourceOrderCode 二选一，优先使用。 */
    private Long sourceOrderId;

    /** 出库方向：FORWARD=销售出库，RED=红字/销退出库；默认 FORWARD。 */
    private String direction;

    /** ERP 出库单号，必填。 */
    @NotBlank(message = "erpOutboundNo 不能为空")
    @Size(max = 128, message = "erpOutboundNo 长度不能超过 128")
    private String erpOutboundNo;

    /** 仓库编码，可选；缺省回退订单仓库/默认仓。 */
    private String warehouseCode;

    /** 出库日期 yyyy-MM-dd，默认今天。 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate outboundDate;

    /** 备注。 */
    private String remark;

    /** 出库明细，至少 1 行。 */
    @NotEmpty(message = "lines 不能为空")
    @Valid
    private List<ErpOutboundLine> lines;
}