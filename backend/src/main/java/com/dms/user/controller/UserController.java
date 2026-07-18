/*
 * 用户 REST 控制器，提供用户管理相关的 HTTP 接口。
 */
package com.dms.user.controller;

import com.dms.common.ApiResponse;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.user.dto.ResetPasswordRequest;
import com.dms.user.dto.UserCreateRequest;
import com.dms.user.dto.UserDTO;
import com.dms.user.dto.UserUpdateRequest;
import com.dms.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 用户接口：/api/users 相关 CRUD 与解锁/重置密码。
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public ApiResponse<PageResult<UserDTO>> list(@RequestParam(required = false) UUID tenantId,
                                                 @Valid PageQuery pageQuery) {
        return ApiResponse.ok(userService.list(tenantId, pageQuery));
    }

    @GetMapping("/{id}")
    public ApiResponse<UserDTO> get(@PathVariable Long id) {
        return ApiResponse.ok(userService.get(id));
    }

    @PostMapping
    public ApiResponse<UserDTO> create(@Valid @RequestBody UserCreateRequest request) {
        return ApiResponse.ok(userService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<UserDTO> update(@PathVariable Long id,
                                       @Valid @RequestBody UserUpdateRequest request) {
        return ApiResponse.ok(userService.updateProfile(id, request));
    }

    @PostMapping("/{id}/unlock")
    public ApiResponse<Void> unlock(@PathVariable Long id) {
        userService.unlock(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/reset-password")
    public ApiResponse<Void> resetPassword(@PathVariable Long id,
                                           @Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(id, request);
        return ApiResponse.ok();
    }
}
