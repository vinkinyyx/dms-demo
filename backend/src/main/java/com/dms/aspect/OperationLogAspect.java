package com.dms.aspect;

import com.dms.annotation.OperationLog;
import com.dms.common.enums.OperationAction;
import com.dms.common.util.TenantContext;
import com.dms.service.OperationLogService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Aspect
@Component
public class OperationLogAspect {
    private static final org.slf4j.Logger OPERATION_LOGGER = org.slf4j.LoggerFactory.getLogger("OPERATION_LOGGER");

    @Autowired
    private OperationLogService operationLogService;

    @Autowired
    private ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager em;

    @Around(
            "(execution(* com.dms.controller.*.*(..)) "
                    + "|| execution(* com.dms.masterdata.controller.*.*(..)) "
                    + "|| execution(* com.dms.order.controller.*.*(..)) "
                    + "|| execution(* com.dms.authz.controller.*.*(..)) "
                    + "|| execution(* com.dms.contract.controller.*.*(..)) "
                    + "|| execution(* com.dms.inventory.controller.*.*(..)) "
                    + "|| execution(* com.dms.*.service.*.*(..))) "
                    + "&& @annotation(com.dms.annotation.OperationLog)")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        OperationLog annotation = method.getAnnotation(OperationLog.class);

        Object[] args = joinPoint.getArgs();
        Long businessId = null;
        Map<String, Object> oldValues = null;

        if (annotation.action() == OperationAction.UPDATE) {
            businessId = getBusinessId(joinPoint, annotation);
            if (businessId != null) {
                oldValues = captureCurrentEntity(annotation.businessType(), businessId);
            }
        }

        Object result = joinPoint.proceed();

        try {
            Long userId = TenantContext.getUserId();
            String username = TenantContext.getUsername();
            UUID tenantId = TenantContext.getTenantId();

            if (userId == null) {
                return result;
            }

            // v3.7.6: 优先从结果中提取(可能含 receiptId 等聚合字段), fallback 到方法参数
            Long fromResult = tryExtractIdFromResult(result);
            if (fromResult != null) {
                businessId = fromResult;
            }
            if (businessId == null) {
                businessId = getBusinessId(joinPoint, annotation);
            }
            if (businessId == null) {
                log.debug("skip op-log: businessId unresolved type={} action={}",
                        annotation.businessType(), annotation.action().name());
                return result;
            }

            com.dms.entity.OperationLog operationLog = new com.dms.entity.OperationLog();
            operationLog.setTenantCode(tenantId == null ? "default" : tenantId.toString());
            operationLog.setBusinessType(annotation.businessType());
            operationLog.setBusinessId(businessId);
            operationLog.setOperatorId(userId);
            operationLog.setOperatorName(username);
            operationLog.setAction(annotation.action().name());
            operationLog.setRemark(annotation.remark());
            operationLog.setCreatedAt(LocalDateTime.now());
            operationLog.setUpdatedAt(LocalDateTime.now());

            if (annotation.action() == OperationAction.UPDATE && oldValues != null) {
                Map<String, Object> newValues = extractRequestBody(args);
                String changeJson = buildChangeJson(oldValues, newValues);
                operationLog.setChangeJson(changeJson);
            }

            saveLogSafely(operationLog);
            // 同时记录到日志文件
            OPERATION_LOGGER.info("操作记录: tenantId={}, userId={}, businessType={}, action={}, remark={}",
                    operationLog.getTenantCode(),
                    operationLog.getOperatorId(),
                    annotation.businessType(),
                    annotation.action().name(),
                    annotation.remark());

        } catch (Exception e) {
            log.error("记录操作日志失败", e);
        }

        return result;
    }


    private TransactionTemplate txTemplate;

    @Autowired
    public void setTransactionManager(PlatformTransactionManager txManager) {
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.txTemplate = new TransactionTemplate(txManager, def);
    }

    /**
     * v3.7.2: save op-log in an independent tx (REQUIRES_NEW) so main flow is not affected by any failure.
     * Uses TransactionTemplate to bypass self-invocation AOP limitation.
     */
    public void saveLogSafely(com.dms.entity.OperationLog operationLog) {
        try {
            if (txTemplate != null) {
                txTemplate.executeWithoutResult(status -> {
                    try {
                        operationLogService.save(operationLog);
                    } catch (Exception e) {
                        log.warn("op-log save failed inside new tx: {}", e.getMessage());
                        status.setRollbackOnly();
                    }
                });
            } else {
                operationLogService.save(operationLog);
            }
        } catch (Exception e) {
            log.warn("op-log outer save failed: {}", e.getMessage());
        }
    }

    /**
     * v3.7.2: extract id from controller return value (ApiResponse / Map / entity).
     */
    private Long tryExtractIdFromResult(Object result) {
        if (result == null) return null;
        try {
            Object data = result;
            try {
                java.lang.reflect.Method m = result.getClass().getMethod("getData");
                data = m.invoke(result);
            } catch (NoSuchMethodException ignored) { /* not ApiResponse */ }
            if (data == null) return null;
            if (data instanceof Number n) return n.longValue();
            if (data instanceof Map<?, ?> mm) {
                // v3.7.6: businessType 为 receipt 时, 优先使用 receiptId 作为 businessId (子单场景)
                Object rid = mm.get("receiptId");
                if (rid instanceof Number rn) return rn.longValue();
                if (rid != null) try { return Long.parseLong(String.valueOf(rid)); } catch (Exception ignored) {}
                Object v = mm.get("id");
                if (v instanceof Number nn) return nn.longValue();
                if (v != null) return Long.parseLong(String.valueOf(v));
            }
            try {
                java.lang.reflect.Method gid = data.getClass().getMethod("getId");
                Object v = gid.invoke(data);
                if (v instanceof Number nn) return nn.longValue();
                if (v != null) return Long.parseLong(String.valueOf(v));
            } catch (Exception ignored) { }
        } catch (Exception e) {
            log.debug("tryExtractIdFromResult failed: {}", e.getMessage());
        }
        return null;
    }
    private Map<String, Object> captureCurrentEntity(String businessType, Long id) {
        try {
            String tableName = businessType.toLowerCase() + "s";
            if ("product".equals(businessType)) tableName = "products";
            else if ("dealer".equals(businessType)) tableName = "dealers";
            else if ("hospital".equals(businessType)) tableName = "hospitals";
            else if ("category".equals(businessType)) tableName = "product_categories";
            else if ("productCategory".equals(businessType)) tableName = "product_categories";
            else if ("warehouse".equals(businessType)) tableName = "warehouses";
            else if ("supplier".equals(businessType)) tableName = "suppliers";
            else if ("order".equals(businessType)) tableName = "orders";
            else if ("salesOrder".equals(businessType)) tableName = "orders";
            else if ("salesOut".equals(businessType)) tableName = "sales_outs";
            else if ("receipt".equals(businessType)) tableName = "receipts";
            else if ("purchaseOrder".equals(businessType)) tableName = "purchase_orders";
            else if ("region".equals(businessType)) tableName = "regions";

            var q = em.createNativeQuery(
                    "SELECT * FROM " + tableName + " WHERE id = ?", jakarta.persistence.Tuple.class);
            q.setParameter(1, id);
            @SuppressWarnings("unchecked")
            List<jakarta.persistence.Tuple> rows = q.getResultList();
            if (rows.isEmpty()) return null;

            Map<String, Object> values = new LinkedHashMap<>();
            jakarta.persistence.Tuple t = rows.get(0);
            for (jakarta.persistence.TupleElement<?> e : t.getElements()) {
                values.put(e.getAlias(), t.get(e.getAlias()));
            }
            return values;
        } catch (Exception e) {
            log.warn("捕获原始数据失败: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, Object> extractRequestBody(Object[] args) {
        for (Object arg : args) {
            if (arg != null && !arg.getClass().getName().startsWith("java.lang")
                    && !arg.getClass().getName().startsWith("org.springframework")
                    && !(arg instanceof Long) && !(arg instanceof Integer)) {
                Map<String, Object> values = new LinkedHashMap<>();
                try {
                    for (Field f : arg.getClass().getDeclaredFields()) {
                        f.setAccessible(true);
                        Object v = f.get(arg);
                        if (v != null) {
                            values.put(f.getName(), v);
                        }
                    }
                } catch (Exception e) {
                    log.warn("提取请求体失败: {}", e.getMessage());
                }
                return values;
            }
        }
        return null;
    }

    private String buildChangeJson(Map<String, Object> oldValues, Map<String, Object> newValues) {
        if (newValues == null) return null;
        Map<String, Object> changes = new LinkedHashMap<>();
        for (String key : newValues.keySet()) {
            Object oldVal = oldValues != null ? oldValues.get(key) : null;
            Object newVal = newValues.get(key);
            if (newVal != null && !Objects.equals(oldVal, newVal)) {
                Map<String, Object> diff = new LinkedHashMap<>();
                diff.put("old", oldVal);
                diff.put("new", newVal);
                changes.put(key, diff);
            }
        }
        if (changes.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(changes);
        } catch (JsonProcessingException e) {
            log.warn("序列化变更JSON失败: {}", e.getMessage());
            return null;
        }
    }

    private Long getBusinessId(ProceedingJoinPoint joinPoint, OperationLog annotation) {
        try {
            Object[] args = joinPoint.getArgs();
            if (args.length == 0) {
                return null;
            }
            if (!annotation.businessIdParameterName().isEmpty()) {
                for (Object arg : args) {
                    try {
                        java.lang.reflect.Field field = arg.getClass().getDeclaredField(annotation.businessIdParameterName());
                        field.setAccessible(true);
                        Object value = field.get(arg);
                        if (value instanceof Long) {
                            return (Long) value;
                        }
                        if (value instanceof Integer) {
                            return ((Integer) value).longValue();
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
            if (args[0] instanceof Long) {
                return (Long) args[0];
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
