/*
 * 代金券批量发放请求：按 dealerId 列表或客户等级生成券。
 */
package com.dms.voucher.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Data
public class VoucherBatchIssueRequest {

    @NotBlank(message = "券名称不能为空")
    private String name;

    @DecimalMin(value = "0.01", message = "面值必须大于 0")
    private BigDecimal faceValue;

    @DecimalMin(value = "0", message = "最低消费不能为负")
    private BigDecimal minSpend = BigDecimal.ZERO;

    /** ALL / PRODUCT / CATEGORY */
    private String scopeType = "ALL";

    /** PRODUCT/CATEGORY 时的引用列表，元素形如 {id, code, name} */
    private List<Map<String, Object>> scopeRefs;

    private OffsetDateTime validFrom;
    private OffsetDateTime validTo;

    /** 指定经销商发放；与 dealerLevel 二选一或并存 */
    private List<Long> dealerIds;

    /** 按经销商等级批量发放 */
    private String dealerLevel;

    private String remark;
}
