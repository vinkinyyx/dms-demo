package com.dms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dms.entity.OperationLog;
import com.dms.mapper.OperationLogMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OperationLogService extends ServiceImpl<OperationLogMapper, OperationLog> {
    
    public Page<OperationLog> queryByBusiness(String businessType, Long businessId, Page<OperationLog> page) {
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OperationLog::getBusinessType, businessType)
               .eq(OperationLog::getBusinessId, businessId)
               .orderByDesc(OperationLog::getCreatedAt);
        return this.page(page, wrapper);
    }
    
    public List<OperationLog> listByBusiness(String businessType, Long businessId) {
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OperationLog::getBusinessType, businessType)
               .eq(OperationLog::getBusinessId, businessId)
               .orderByDesc(OperationLog::getCreatedAt);
        return this.list(wrapper);
    }
}
