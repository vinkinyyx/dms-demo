/*
 * 鐢ㄦ埛 REST 鎺у埗鍣紝鎻愪緵鐢ㄦ埛绠＄悊鐩稿叧鐨?HTTP 鎺ュ彛銆? */
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
import org.springframework.security.access.prepost.PreAuthorize;
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
 * 鐢ㄦ埛鎺ュ彛锛?api/users 鐩稿叧 CRUD 涓庤В閿?閲嶇疆瀵嗙爜銆? */
@RestController
@RequestMapping("/api/users")
@PreAuthorize("@perm.canManageUsers()")
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
    @PreAuthorize("@perm.hasAny('user:edit', 'user:unlock')")
    public ApiResponse<Void> unlock(@PathVariable Long id) {
        userService.unlock(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("@perm.hasAny('user:reset_password')")
    public ApiResponse<Void> resetPassword(@PathVariable Long id,
                                           @Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(id, request);
        return ApiResponse.ok();
    }
}
