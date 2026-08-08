package com.dms.platform.rbac.dto;

import lombok.Data;

import java.util.List;

@Data
public class RoleTemplatePermissionsRequest {
    private List<String> resourceCodes;
}