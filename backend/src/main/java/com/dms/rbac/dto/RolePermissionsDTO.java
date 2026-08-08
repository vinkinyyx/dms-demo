package com.dms.rbac.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RolePermissionsDTO {
    private Long roleId;
    private List<ResourceDTO> resources;
    private List<String> selectedCodes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResourceDTO {
        private Long id;
        private String code;
        private String name;
        private String type;
        private Long parentId;
        private String path;
    }
}
