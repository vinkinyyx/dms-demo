package com.dms.platform.mapping.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MappingImportPreviewResponse {
    private Long batchId;
    private String batchNo;
    private int totalCount;
    private int validCount;
    private int errorCount;
    private List<MappingImportRow> rows;
    private List<MappingImportError> errors;

    public List<MappingImportRow> rowsOrEmpty() {
        return rows == null ? new ArrayList<>() : rows;
    }

    public List<MappingImportError> errorsOrEmpty() {
        return errors == null ? new ArrayList<>() : errors;
    }
}