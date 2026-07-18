/*
 * 分销出库创建请求 DTO。
 */
package com.dms.sales.dto;

import com.dms.sales.entity.DistributionLine;
import com.dms.sales.entity.DistributionShipment;
import lombok.Data;

import java.util.List;

@Data
public class DistributionShipmentCreateRequest {
    private DistributionShipment shipment;
    private List<DistributionLine> lines;
}
