/*
 * 促销预览请求 DTO：接收草稿订单信息。
 */
package com.dms.promotion.dto;

import lombok.Data;

import java.util.List;

@Data
public class PromotionPreviewRequest {

    private Long dealerId;
    private List<PromotionLine> lines;
}
