package com.dms.annotation;

import com.dms.common.enums.OperationAction;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {
    
    String businessType();
    
    OperationAction action();
    
    String businessIdGetter() default "";
    
    String businessIdParameterName() default "id";
    
    String remark() default "";
}
