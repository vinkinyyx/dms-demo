/*
 * 微信登录回调请求 DTO。
 */
package com.dms.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 微信回调请求：code + state。
 */
@Data
public class WechatCallbackRequest {

    @NotBlank(message = "code 不能为空")
    private String code;

    @NotBlank(message = "state 不能为空")
    private String state;
}
