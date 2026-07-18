/*
 * 授权检查请求 DTO：接收 dealerId、authType、atTime、lines[]（productId+terminalId）。
 */
package com.dms.authz.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class AuthorizationCheckRequest {

    private Long dealerId;
    private String authType;
    private LocalDate atTime;
    private List<Line> lines;

    @Data
    public static class Line {
        private Long productId;
        private Long terminalId;
    }
}
