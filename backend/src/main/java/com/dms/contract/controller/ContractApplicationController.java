/*
 * 合同申请 REST 控制器。
 */
package com.dms.contract.controller;

import com.dms.common.ApiResponse;
import com.dms.common.PageQuery;
import com.dms.common.PageResult;
import com.dms.contract.entity.Contract;
import com.dms.contract.entity.ContractApplication;
import com.dms.contract.service.ContractApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/contract-applications")
@RequiredArgsConstructor
@Validated
public class ContractApplicationController {

    private final ContractApplicationService service;

    @GetMapping
    public ApiResponse<PageResult<ContractApplication>> list(@Valid PageQuery pageQuery) {
        return ApiResponse.ok(service.list(pageQuery));
    }

    @GetMapping("/{id}")
    public ApiResponse<ContractApplication> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    public ApiResponse<ContractApplication> create(@RequestBody ContractApplication request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ContractApplication> update(@PathVariable Long id,
                                                    @RequestBody ContractApplication request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @PostMapping("/{id}/submit")
    public ApiResponse<ContractApplication> submit(@PathVariable Long id) {
        return ApiResponse.ok(service.submit(id));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<Contract> approve(@PathVariable Long id) {
        return ApiResponse.ok(service.approve(id));
    }

    @PostMapping("/{id}/terminate")
    public ApiResponse<Void> terminate(@PathVariable Long id) {
        service.terminate(id);
        return ApiResponse.ok();
    }
}
