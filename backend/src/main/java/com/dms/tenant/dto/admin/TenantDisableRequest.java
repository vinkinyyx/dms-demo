package com.dms.tenant.dto.admin;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TenantDisableRequest {

    @Size(max = 500)
    private String reason;
}
