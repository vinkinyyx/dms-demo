package com.dms.contract.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class TemplateRequest {
    private String code;
    private String name;
    private String category;
    private Long originalFileId;
    private List<Map<String, Object>> fields;
}
