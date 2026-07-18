/*
 * 微信扫码请求 DTO（当前无参，预留字段供后续扩展）。
 */
package com.dms.auth.dto;

import lombok.Data;

/**
 * 微信扫码请求：可预留扫码来源等字段。
 */
@Data
public class WechatQrRequest {

    private String source;
}
