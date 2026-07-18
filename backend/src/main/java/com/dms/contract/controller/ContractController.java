/*
 * 合同 REST 控制器。
 */
package com.dms.contract.controller;

import com.dms.common.ApiResponse;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.contract.entity.Contract;
import com.dms.contract.service.ContractService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/contracts")
@RequiredArgsConstructor
@Validated
public class ContractController {

    private final ContractService service;

    @GetMapping
    public ApiResponse<PageResult<Contract>> list(@Valid PageQuery pageQuery) {
        return ApiResponse.ok(service.list(pageQuery));
    }

    @GetMapping("/{id}")
    public ApiResponse<Contract> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping("/{id}/terminate")
    public ApiResponse<Void> terminate(@PathVariable Long id) {
        service.terminate(id);
        return ApiResponse.ok();
    }
}
