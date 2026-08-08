/*
 * 统一错误码枚举，业务层与全局异常处理器使用此枚举返回结构化错误。
 */
package com.dms.common;

import lombok.Getter;

@Getter
public enum ErrorCode {

    SUCCESS(0, "成功"),

    PARAM_INVALID(40001, "参数校验失败"),
    PARAM_MISSING(40002, "缺少必要参数"),
    PARAM_FORMAT_ERROR(40003, "参数格式错误"),
    BUSINESS_RULE_VIOLATION(40006, "业务规则校验失败"),

    UNAUTHORIZED(40101, "未登录或凭证已失效"),
    TOKEN_EXPIRED(40102, "令牌已过期"),
    TOKEN_INVALID(40103, "令牌非法"),

    FORBIDDEN(40301, "无操作权限"),

    NOT_FOUND(40401, "资源不存在"),

    RESOURCE_CONFLICT(40901, "资源冲突"),
    OPTIMISTIC_LOCK_FAILURE(40902, "并发更新冲突，请重试"),

    RATE_LIMITED(42901, "请求过于频繁，请稍后再试"),

    INTERNAL_ERROR(50000, "系统内部错误"),

    RESOURCE_IN_USE(40903, "资源正在被使用，无法删除"),
    HAS_REFERENCES(40904, "存在引用记录，无法删除"),
    CANNOT_DELETE(40905, "不可删除：该资源已被业务数据引用"),

    TENANT_DISABLED(40302, "租户已停用"),
    TENANT_NOT_FOUND(40402, "租户不存在"),
    DEALER_ALREADY_BOUND(40906, "dealer 已被启用中的经销商租户绑定"),
    TENANT_ADMIN_EXISTS(40907, "该租户已存在启用中的租户管理员"),
    PRODUCT_MAPPING_CONFLICT(40908, "产品对码冲突"),
    INVALID_MANUFACTURER_SCOPE(40303, "无权访问其他厂家数据"),
    PLATFORM_AUTH_REQUIRED(40104, "需要平台后台登录"),
    UI_CONFIG_INVALID(40004, "页面配置不合法");

    private final Integer code;
    private final String message;

    ErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}

