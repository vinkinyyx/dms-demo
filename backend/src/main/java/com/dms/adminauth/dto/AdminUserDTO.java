package com.dms.adminauth.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminUserDTO {

    private Long id;
    private String username;
    private String name;
    private Boolean mustChangePassword;
}
