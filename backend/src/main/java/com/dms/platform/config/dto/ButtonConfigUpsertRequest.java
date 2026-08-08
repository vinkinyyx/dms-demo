/*
 * 按钮配置批量保存请求。
 * 用于：admin-vue "按钮配置" Tab 一次性保存某 pageKey 下所有按钮。
 * body.buttons 中 scope+buttonKey 唯一标识一行。
 */
package com.dms.platform.config.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class ButtonConfigUpsertRequest {
    @NotBlank
    private String pageKey;
    @NotBlank
    private String tenantType;
    /** scope: PLATFORM_DEFAULT（平台默认）或 TENANT_OVERRIDE（租户覆盖）。 */
    @NotBlank
    private String scopeLevel;
    /** 平台默认必传 null；租户覆盖可不传，从 token 上下文取。 */
    private List<ButtonConfigDTO> buttons;
}