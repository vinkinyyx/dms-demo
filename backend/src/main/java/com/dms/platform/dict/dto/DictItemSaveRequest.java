package com.dms.platform.dict.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DictItemSaveRequest {
    @NotBlank
    @Size(max = 64)
    private String code;
    @NotBlank
    @Size(max = 200)
    private String name;
    private Integer seq;
}