package com.dms.openapi.dto.collab;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 接口1：经销商采购订单提交报文（经销商 -> 厂家 DMS）。
 * DMS 收到后在厂家租户创建一张 DRAFT 销售订单，按 poNo 幂等。
 */
@Data
public class CollabPurchaseOrderRequest {

    @NotNull(message = "header 不能为空")
    @Valid
    private CollabPoHeader header;

    @NotEmpty(message = "lines 不能为空")
    @Valid
    private List<CollabPoLine> lines;

    @Data
    public static class CollabPoHeader {
        /** 经销商采购订单号（幂等键），必填。 */
        @NotBlank(message = "header.poNo 不能为空")
        @Size(max = 64)
        private String poNo;
        /** 经销商编码（需与开放应用绑定的 dealerCode 一致），必填。 */
        @NotBlank(message = "header.dealerCode 不能为空")
        @Size(max = 32)
        private String dealerCode;
        /** 厂家编码，可选（用于对账，DMS 按自身租户处理）。 */
        private String manufacturerCode;
        /** 下单日期 yyyy-MM-dd，可选。 */
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate orderDate;
        /** 期望到货日期 yyyy-MM-dd，可选。 */
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate expectedDate;
        /** 收货仓库编码，可选（厂家侧暂存备注，不参与建单）。 */
        private String warehouseCode;
        /** 备注，可选。 */
        private String remark;
    }

    @Data
    public static class CollabPoLine {
        /** 行号，可选。 */
        private Integer lineNo;
        /** 经销商物料编码（按开放应用映射厂家产品），必填。 */
        @NotBlank(message = "lines[].materialCode 不能为空")
        @Size(max = 64)
        private String materialCode;
        /** 物料名称，可选（映射缺失时用于报错提示）。 */
        private String materialName;
        /** 数量，必填。 */
        @jakarta.validation.constraints.NotNull(message = "lines[].qty 不能为空")
        private java.math.BigDecimal qty;
        /** 单位，可选。 */
        private String unit;
        /** 单价（不含税参考价），可选；草稿单价格由厂家补录。 */
        private java.math.BigDecimal unitPrice;
    }
}
