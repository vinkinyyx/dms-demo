/*
 * 盘点上传请求 DTO。
 */
package com.dms.inventory.dto;

import com.dms.inventory.entity.Stocktake;
import com.dms.inventory.entity.StocktakeLine;
import lombok.Data;

import java.util.List;

@Data
public class StocktakeUploadRequest {
    private Stocktake stocktake;
    private List<StocktakeLine> lines;
}
