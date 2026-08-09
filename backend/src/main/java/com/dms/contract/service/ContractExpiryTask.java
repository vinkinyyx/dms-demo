package com.dms.contract.service;

import com.dms.contract.entity.Contract;
import com.dms.contract.repository.ContractRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContractExpiryTask {

    private final ContractRepository contractRepository;

    @Scheduled(cron = "0 5 0 * * ?")
    @Transactional
    public void markExpired() {
        LocalDate today = LocalDate.now();
        // 标记所有已过有效期但仍生效的合同为 expired
        List<Contract> all = contractRepository.findAll();
        int count = 0;
        for (Contract c : all) {
            if ("effective".equals(c.getStatus()) && c.getValidTo() != null && c.getValidTo().isBefore(today)) {
                c.setStatus("expired");
                contractRepository.save(c);
                count++;
            }
        }
        if (count > 0) log.info("合同到期任务：共标记 {} 份合同为 expired", count);
    }
}
