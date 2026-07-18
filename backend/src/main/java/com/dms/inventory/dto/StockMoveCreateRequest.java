/*
 * 移库创建请求 DTO。
 */
package com.dms.inventory.dto;

import com.dms.inventory.entity.StockMove;
import com.dms.inventory.entity.StockMoveLine;
import lombok.Data;

import java.util.List;

@Data
public class StockMoveCreateRequest {
    private StockMove move;
    private List<StockMoveLine> lines;
}
