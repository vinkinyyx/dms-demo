package com.dms.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dms.common.ApiResponse;
import com.dms.entity.OperationLog;
import com.dms.service.OperationLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/operation-log")
public class OperationLogController {
    
    @Autowired
    private OperationLogService operationLogService;
    
    @GetMapping("/list/{businessType}/{businessId}")
    public ApiResponse<Page<OperationLog>> list(
            @PathVariable String businessType,
            @PathVariable Long businessId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        Page<OperationLog> page = new Page<>(pageNum, pageSize);
        return ApiResponse.ok(operationLogService.queryByBusiness(businessType, businessId, page));
    }
}
