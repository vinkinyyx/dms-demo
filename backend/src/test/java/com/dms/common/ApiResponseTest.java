/*
 * 测试目标：验证 ApiResponse / ErrorCode / BusinessException 组合的响应包装行为。
 * 覆盖用户故事：US-1.7（统一响应包装）、US-1.8（错误码规范）。
 */
package com.dms.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiResponseTest {

    @Test
    @DisplayName("正常流程：ok(data) 返回 code=0 且数据被保留")
    void should_returnOkStructure_when_success() {
        ApiResponse<String> resp = ApiResponse.ok("hello");
        assertThat(resp.getCode()).isEqualTo(0);
        assertThat(resp.getMessage()).isEqualTo("OK");
        assertThat(resp.getData()).isEqualTo("hello");
    }

    @Test
    @DisplayName("正常流程：ok() 无参构造 data 为 null")
    void should_returnOkWithoutData_when_okNoArg() {
        ApiResponse<Void> resp = ApiResponse.ok();
        assertThat(resp.getCode()).isZero();
        assertThat(resp.getData()).isNull();
    }

    @Test
    @DisplayName("异常分支：fail(errorCode) 返回错误码与默认消息")
    void should_returnFailStructure_when_failWithErrorCode() {
        ApiResponse<Object> resp = ApiResponse.fail(ErrorCode.PARAM_INVALID);
        assertThat(resp.getCode()).isEqualTo(40001);
        assertThat(resp.getMessage()).isEqualTo("参数校验失败");
        assertThat(resp.getData()).isNull();
    }

    @Test
    @DisplayName("异常分支：fail(errorCode, msg) 使用自定义消息覆盖")
    void should_useCustomMessage_when_failWithMessage() {
        ApiResponse<Object> resp = ApiResponse.fail(ErrorCode.BUSINESS_RULE_VIOLATION, "自定义错误");
        assertThat(resp.getCode()).isEqualTo(40006);
        assertThat(resp.getMessage()).isEqualTo("自定义错误");
    }

    @Test
    @DisplayName("边界：ErrorCode 枚举核心值与 message 正确")
    void should_containCoreCodes_when_lookupEnum() {
        assertThat(ErrorCode.SUCCESS.getCode()).isZero();
        assertThat(ErrorCode.UNAUTHORIZED.getCode()).isEqualTo(40101);
        assertThat(ErrorCode.FORBIDDEN.getCode()).isEqualTo(40301);
        assertThat(ErrorCode.NOT_FOUND.getCode()).isEqualTo(40401);
        assertThat(ErrorCode.PARAM_FORMAT_ERROR.getCode()).isEqualTo(40003);
        assertThat(ErrorCode.PARAM_FORMAT_ERROR.getMessage()).contains("参数格式");
    }

    @Test
    @DisplayName("异常分支：BusinessException 携带 ErrorCode 与自定义消息")
    void should_propagateErrorCode_when_throwBusinessException() {
        BusinessException ex1 = new BusinessException(ErrorCode.NOT_FOUND);
        assertThat(ex1.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(ex1.getMessage()).isEqualTo("资源不存在");

        BusinessException ex2 = new BusinessException(ErrorCode.FORBIDDEN, "无权访问");
        assertThat(ex2.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
        assertThat(ex2.getMessage()).isEqualTo("无权访问");

        assertThatThrownBy(() -> {
            throw new BusinessException(ErrorCode.RATE_LIMITED, "限流", new RuntimeException("root"));
        }).isInstanceOf(BusinessException.class)
                .hasMessageContaining("限流")
                .hasCauseInstanceOf(RuntimeException.class);
    }
}
