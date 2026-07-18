/*
 * 授权检查结果 DTO：单行的 productId/terminalId/authorized/reason。
 */
package com.dms.authz.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuthorizationCheckResult {

    private Long productId;
    private Long terminalId;
    private Boolean authorized;
    private String reason;
}
