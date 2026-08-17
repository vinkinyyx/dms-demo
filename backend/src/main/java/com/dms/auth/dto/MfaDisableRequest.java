package com.dms.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MfaDisableRequest {
    @NotBlank(message = "Verification code cannot be empty")
    private String code;
}
