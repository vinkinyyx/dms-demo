/*
 * 按钮配置 DTO。
 */
package com.dms.platform.config.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ButtonConfigDTO {
    private Long id;
    private UUID tenantId;
    private String pageKey;
    private String tenantType;
    private String scope;             // toolbar / row
    private String buttonKey;         // search / reset / import / export / create / view / edit / submit / approve / delete ...
    private String label;
    private String buttonType;        // primary / default / danger / warning / info / success
    private String icon;
    private String permissionCode;
    private Boolean visible;
    private Integer sortOrder;
    private String rowButtonPosition; // row 专用：common / danger
    private Boolean confirmRequired;
    private String status;
    /** 是否来自租户覆盖（true=覆盖 / false=平台默认）— 仅供前端 UI 区分显示 */
    private Boolean fromTenant;
}