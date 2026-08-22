package com.dms.tenant.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class ManufacturerTenantCreateRequest {
    @NotBlank(message = "tenant code required") @Size(max = 32) private String code;
    @NotBlank(message = "tenant name required") @Size(max = 200) private String name;
    @Size(max = 64) private String contactName;
    @Size(max = 32) private String contactPhone;
    @Size(max = 128) private String contactEmail;
    @NotBlank(message = "admin username required") @Size(max = 64) private String adminUsername;
    @NotBlank(message = "admin password required") @Size(min = 6, max = 64) private String adminPassword;
    @NotBlank(message = "admin name required") @Size(max = 64) private String adminName;
    @Size(max = 500) private String remark;
    private Boolean inventoryEnabled;
}