/*
 * 客户公开自助注册入口：无需登录，提交注册申请进入审核队列。
 * 审核/列表接口见 CustomerRegistrationController。
 */
package com.dms.auth.controller;

import com.dms.common.ApiResponse;
import com.dms.user.registration.dto.CustomerRegisterRequest;
import com.dms.user.registration.dto.RegistrationDTO;
import com.dms.user.registration.service.CustomerRegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class CustomerRegisterController {

    private final CustomerRegistrationService registrationService;

    /** 客户自助注册（公开接口，PC / H5 / 小程序链接共用）。 */
    @PostMapping("/customer-register")
    public ApiResponse<RegistrationDTO> register(@Valid @RequestBody CustomerRegisterRequest request) {
        return ApiResponse.ok(registrationService.register(request));
    }
}
