package com.dms.platform.config.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class FilterConfigUpsertRequest {
    @NotBlank
    private String pageKey;
    @NotBlank
    private String tenantType;
    private List<FilterConfigDTO> filters;
}