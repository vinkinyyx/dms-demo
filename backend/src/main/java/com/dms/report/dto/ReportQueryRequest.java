/*
 * 报表通用查询请求 DTO。
 */
package com.dms.report.dto;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class ReportQueryRequest {
    /** 过滤条件，例如 {"dealerId":1, "startDate":"2026-07-01", "endDate":"2026-07-31"} */
    private Map<String, Object> filters = new HashMap<>();
}
