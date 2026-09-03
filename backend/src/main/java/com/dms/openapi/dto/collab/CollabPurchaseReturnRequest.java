package com.dms.openapi.dto.collab;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 接口3：经销商采购退货单提交报文（经销商 -> 厂家 DMS）。
 * DMS 收到后在厂家租户创建一张 DRAFT 红字销退订单，按 returnNo 幂等。
 */
@Data
public class CollabPurchaseReturnRequest {

    @NotNull(message = "header 不能为空")
    @Valid
    private CollabReturnHeader header;

    @NotEmpty(message = "lines 不能为空")
    @Valid
    private List<CollabReturnLine> lines;

    @Data
    public static class CollabReturnHeader {
        /** 经销商采退单号（幂等键），必填。 */
        @NotBlank(message = "header.returnNo 不能为空")
        @Size(max = 64)
        private String returnNo;
        /** 原厂家出库单号（退货来源），可选。 */
        private String refOutboundNo;
        /** 经销商编码，必填。 */
        @NotBlank(message = "header.dealerCode 不能为空")
        @Size(max = 32)
        private String dealerCode;
        /** 厂家编码，可选。 */
        private String manufacturerCode;
        /** 退货日期 yyyy-MM-dd，可选。 */
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate returnDate;
        /** 退回仓库编码，可选。 */
        private String warehouseCode;
        /** 备注，可选。 */
        private String remark;
    }

    @Data
    public static class CollabReturnLine {
        private Integer lineNo;
        @NotBlank(message = "lines[].materialCode 不能为空")
        @Size(max = 64)
        private String materialCode;
        private String materialName;
        @jakarta.validation.constraints.NotNull(message = "lines[].qty 不能为空")
        private BigDecimal qty;
        private String unit;
        /** 退货原因，可选。 */
        private String returnReason;
        /** 批号，可选。 */
        private String batchNo;
    }
}
