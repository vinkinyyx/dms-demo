package com.dms.openapi.dto.collab;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 入站报文处理结果（接口1 采购订单提交 / 接口3 采退单提交 通用）。
 * 字段按接口文档响应结构命名，空字段不序列化。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CollabSubmitResult {
    /** 经销商报文单号（poNo / returnNo 回显）。 */
    private String partnerDocNo;
    /** 接口1：厂家生成的销售订单号。 */
    private String salesOrderNo;
    /** 接口1：厂家单据状态，DRAFT（待厂家补价格并审批）。 */
    private String salesOrderStatus;
    /** 接口3：厂家生成的红字销退订单号。 */
    private String redSalesReturnNo;
    /** 接口3：厂家红字单据状态，DRAFT。 */
    private String redSalesReturnStatus;
    /** 是否本次新建；false 表示幂等命中（重复报文）。 */
    private boolean created;
}
