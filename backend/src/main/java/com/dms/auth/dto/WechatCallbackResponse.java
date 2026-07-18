/*
 * 微信登录回调结果 DTO。
 */
package com.dms.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 微信回调结果：若已绑定则返回登录信息；未绑定则返回 needBind + bindToken。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WechatCallbackResponse {

    /** 是否需要走绑定流程 */
    private Boolean needBind;

    /** 需要绑定时的一次性短期 token */
    private String bindToken;

    /** 已绑定时返回登录信息 */
    private LoginResponse login;
}
