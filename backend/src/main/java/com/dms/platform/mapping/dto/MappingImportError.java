package com.dms.platform.mapping.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MappingImportError {
    private int rowNumber;
    private String field;
    private String message;
}