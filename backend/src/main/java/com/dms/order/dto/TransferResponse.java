/*
 * 订单传输统一响应 DTO（销售/采购传输接口共用）
 *
 * 成功：data.code = 订单编号（SO-/PO- 前缀），data.id = 内部主键，data.status = 初始状态
 * 失败：ApiResponse.fail(errCode, reason)，message 字段即失败原因
 */
package com.dms.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TransferResponse {

    /** 内部主键 ID，成功时返回，失败为 null。 */
    private Long id;

    /** 订单编号，成功时返回如 SO-20260806-00001。 */
    private String code;

    /** 订单类型：NORMAL / RED / RUSH。 */
    private String orderType;

    /** 初始状态，成功创建后通常为 DRAFT。 */
    private String status;

    /** 含税总金额，成功时返回。 */
    private BigDecimal amount;
}
