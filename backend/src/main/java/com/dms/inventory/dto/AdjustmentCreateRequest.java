/*
 * 库存调整创建请求 DTO。
 */
package com.dms.inventory.dto;

import com.dms.inventory.entity.AdjustmentLine;
import com.dms.inventory.entity.InventoryAdjustment;
import lombok.Data;

import java.util.List;

@Data
public class AdjustmentCreateRequest {
    private InventoryAdjustment adjustment;
    private List<AdjustmentLine> lines;
}
