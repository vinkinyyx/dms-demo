/*
 * Report query request DTO.
 */
package com.dms.report.dto;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class ReportQueryRequest {
    private String range;
    private String startDate;
    private String endDate;
    private Integer page;
    private Integer size;
    private Integer limit;
    private Long dealerId;
    private Long hospitalId;
    private Long productId;
    private Map<String, Object> filters = new HashMap<>();
}
