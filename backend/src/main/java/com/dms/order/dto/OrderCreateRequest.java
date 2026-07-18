/*
 * 订单创建请求 DTO。
 */
package com.dms.order.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
public class OrderCreateRequest {

    private String orderType;
    private Long dealerId;
    private Long shipAddressId;
    private Map<String, Object> shipSnapshot;
    private String remark;
    private LocalDate expectedDate;
    private List<Line> lines;

    @Data
    public static class Line {
        private Long productId;
        private BigDecimal qty;
        private BigDecimal unitPrice;
        private BigDecimal taxRate;
        private Integer seq;
    }
}
