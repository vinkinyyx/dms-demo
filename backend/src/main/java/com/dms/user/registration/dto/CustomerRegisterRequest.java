/*
 * 客户公开自助注册请求。
 */
package com.dms.user.registration.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class CustomerRegisterRequest {

    /** 厂家租户 code，用于在公开接口中定位所属厂家；为空时取默认厂家。 */
    private String tenantCode;

    @NotBlank(message = "联系人/注册人姓名不能为空")
    @Size(max = 64, message = "姓名过长")
    private String registerName;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Email(message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "登录密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度需在 6-64 位之间")
    private String password;

    @NotBlank(message = "公司/单位名称不能为空")
    @Size(max = 200)
    private String companyName;

    @Size(max = 32)
    private String uscNo;

    @Size(max = 64)
    private String legalPerson;

    @Size(max = 100)
    private String contactName;

    @Size(max = 32)
    private String contactPhone;

    @Size(max = 500)
    private String regAddress;

    /** 收货地址列表，元素含 province/city/district/address/contactName/phone/isDefault 等。 */
    private List<Map<String, Object>> addresses;

    /** 附件资料列表，元素含 name/url/type 等。 */
    private List<Map<String, Object>> attachments;
}
