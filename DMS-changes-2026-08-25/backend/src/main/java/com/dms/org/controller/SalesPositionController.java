package com.dms.org.controller;

import com.dms.common.ApiResponse;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import com.dms.common.ApiResponse;
import com.dms.org.service.SalesPositionService;

@RequestMapping("/api/sales-positions")
@RestController
@RequiredArgsConstructor
@Validated
public class SalesPositionController {

    private final SalesPositionService service;

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码从 1 起") int page,
            @RequestParam(defaultValue = "50") @Min(value = 1, message = "每页条数至少为 1") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String createdAtFrom,
            @RequestParam(required = false) String createdAtTo,
            @RequestParam(required = false) String updatedAtFrom,
            @RequestParam(required = false) String updatedAtTo) {
        return service.list(page, size, sort, id, code, name, level, status,
                createdAtFrom, createdAtTo, updatedAtFrom, updatedAtTo);
    }

    @GetMapping("/candidate-users")
    public ApiResponse<List<Map<String, Object>>> candidateUsers( @RequestParam(required = false) String role) {
        return service.candidateUsers(role);
    }

    @GetMapping("/tree")
    public ApiResponse<List<Map<String, Object>>> tree() {
        return service.tree();
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getOne(@PathVariable Long id) {
        return service.getOne(id);
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        return service.create(body);
    }

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return service.update(id, body);
    }

    @PutMapping("/{id}/bind-users")
    public ApiResponse<Map<String, Object>> bindUsers(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return service.bindUsers(id, body);
    }

    @PutMapping("/{id}/bind-dealers")
    public ApiResponse<Map<String, Object>> bindDealers(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return service.bindDealers(id, body);
    }

    @GetMapping("/{id}/candidates/users")
    public ApiResponse<List<Map<String, Object>>> candidateUsersForPosition(@PathVariable Long id) {
        return service.candidateUsersForPosition(id);
    }

    @GetMapping("/{id}/candidates/dealers")
    public ApiResponse<List<Map<String, Object>>> candidateDealersForPosition(@PathVariable Long id) {
        return service.candidateDealersForPosition(id);
    }

    @GetMapping("/{id}/users")
    public ApiResponse<List<Map<String, Object>>> getPositionUsers(@PathVariable Long id) {
        return service.getPositionUsers(id);
    }

    @GetMapping("/{id}/dealers")
    public ApiResponse<List<Map<String, Object>>> getPositionDealers(@PathVariable Long id) {
        return service.getPositionDealers(id);
    }

    @GetMapping("/my-scope")
    public ApiResponse<Map<String, Object>> myScope() {
        return service.myScope();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        return service.delete(id);
    }

}
