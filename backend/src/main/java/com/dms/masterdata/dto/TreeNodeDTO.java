package com.dms.masterdata.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TreeNodeDTO {
    private Long id;
    private Long parentId;
    private String code;
    private String name;
    private Integer level;
    private Integer sortOrder;
    private String status;
    @Builder.Default
    private List<TreeNodeDTO> children = new ArrayList<>();
}