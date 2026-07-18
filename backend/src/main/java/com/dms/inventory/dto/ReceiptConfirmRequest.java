/*
 * 收货确认请求 DTO。
 */
package com.dms.inventory.dto;

import com.dms.inventory.entity.ReceiptLine;
import lombok.Data;

import java.util.List;

@Data
public class ReceiptConfirmRequest {
    private List<ReceiptLine> lines;
}
