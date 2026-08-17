package com.dms.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MfaVerifyRequest {
    @NotBlank(message = "MFA token cannot be empty")
    private String mfaToken;
    @NotBlank(message = "Verification code cannot be empty")
    private String code;
}
