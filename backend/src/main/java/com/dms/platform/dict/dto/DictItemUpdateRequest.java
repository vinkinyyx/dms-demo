package com.dms.platform.dict.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DictItemUpdateRequest {
    @Size(max = 64)
    private String code;
    @Size(max = 200)
    private String name;
    private Integer seq;
}