package com.dms.tenant.service;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class TenantFeatureAspect {
    private final TenantFeatureGuard guard;

    @Around("within(com.dms.order.controller.PurchaseOrderController) || within(com.dms.order.controller.PurchaseReturnController) || within(com.dms.inventory.controller..*) || within(com.dms.masterdata.controller.WarehouseController) || within(com.dms.masterdata.controller.SupplierController)")
    public Object requireInventory(ProceedingJoinPoint pjp) throws Throwable {
        guard.requireInventory();
        return pjp.proceed();
    }
}
