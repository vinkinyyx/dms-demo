package com.dms.openapi.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * ERP 销售出库回传处理结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErpOutboundResult {

    /** 业务码：0 成功，其余为错误码。 */
    private Integer code;

    /** 结果说明。 */
    private String message;

    /** DMS 销售出库单内部 ID。 */
    private Long salesOutId;

    /** DMS 销售出库单号 code。 */
    private String salesOutCode;

    /** 是否为幂等命中（重复请求）。 */
    private boolean idempotent;

    /** 出库方向：FORWARD/RED。 */
    private String direction;

    /** 成功处理的明细行数。 */
    private Integer processedLines;

    /** 处理失败的明细行（行号/产品/原因）。 */
    private List<FailedLine> failedLines = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FailedLine {
        /** 报文行号（从 1 开始）。 */
        private Integer lineNo;
        /** 产品编码或 ID。 */
        private String product;
        /** 失败原因。 */
        private String reason;
    }
}