package com.dms.platform.dict.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DictTypeUpdateRequest {
    @Size(max = 200)
    private String name;
    private String description;
}