/*
 * 微信扫码返回 DTO。
 */
package com.dms.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 微信扫码返回：scene + qrUrl。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WechatQrResponse {
    private String scene;
    private String qrUrl;
}
