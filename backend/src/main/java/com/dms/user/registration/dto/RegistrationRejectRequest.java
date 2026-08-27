/*
 * 注册审核驳回请求。
 */
package com.dms.user.registration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegistrationRejectRequest {

    @NotBlank(message = "驳回原因不能为空")
    @Size(max = 500, message = "驳回原因过长")
    private String rejectReason;
}
