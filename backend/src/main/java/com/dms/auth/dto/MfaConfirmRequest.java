package com.dms.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MfaConfirmRequest {
    @NotBlank(message = "Verification code cannot be empty")
    private String code;
}
