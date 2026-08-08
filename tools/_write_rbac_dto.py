from pathlib import Path
p=Path('backend/src/main/java/com/dms/rbac/dto/RolePermissionsDTO.java')
p.write_text('''package com.dms.rbac.dto;

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
''', encoding='utf-8', newline='\n')
p=Path('backend/src/main/java/com/dms/rbac/dto/RolePermissionsRequest.java')
p.write_text('''package com.dms.rbac.dto;

import lombok.Data;

import java.util.List;

@Data
public class RolePermissionsRequest {
    private List<String> resourceCodes;
}
''', encoding='utf-8', newline='\n')
