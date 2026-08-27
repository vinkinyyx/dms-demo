/*
 * 客户注册审核接口（厂家管理员）：列表、详情、通过、驳回。
 */
package com.dms.user.registration.controller;

import com.dms.common.ApiResponse;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.user.registration.dto.RegistrationDTO;
import com.dms.user.registration.dto.RegistrationRejectRequest;
import com.dms.user.registration.service.CustomerRegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customer-registrations")
@RequiredArgsConstructor
public class CustomerRegistrationController {

    private final CustomerRegistrationService registrationService;

    @GetMapping
    @PreAuthorize("@perm.hasAny('customer_registration:view','customer_registration:search','dealer:view','dealer:approve')")
    public ApiResponse<PageResult<RegistrationDTO>> list(PageQuery pageQuery,
                                                          @RequestParam(required = false) String status,
                                                          @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(registrationService.list(pageQuery, status, keyword));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.hasAny('customer_registration:view','customer_registration:search','dealer:view','dealer:approve')")
    public ApiResponse<RegistrationDTO> detail(@PathVariable Long id) {
        return ApiResponse.ok(registrationService.detail(id));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("@perm.hasAny('customer_registration:approve','dealer:approve','dealer:create')")
    public ApiResponse<RegistrationDTO> approve(@PathVariable Long id) {
        return ApiResponse.ok(registrationService.approve(id));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("@perm.hasAny('customer_registration:approve','dealer:approve','dealer:create')")
    public ApiResponse<RegistrationDTO> reject(@PathVariable Long id,
                                                @Valid @RequestBody RegistrationRejectRequest request) {
        return ApiResponse.ok(registrationService.reject(id, request.getRejectReason()));
    }
}
