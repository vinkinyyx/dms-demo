/*
 * 销售出库创建请求 DTO。
 */
package com.dms.sales.dto;

import com.dms.sales.entity.SalesOut;
import com.dms.sales.entity.SalesOutLine;
import lombok.Data;

import java.util.List;

@Data
public class SalesOutCreateRequest {
    private SalesOut salesOut;
    private List<SalesOutLine> lines;
}
