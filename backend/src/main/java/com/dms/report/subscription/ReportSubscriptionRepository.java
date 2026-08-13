package com.dms.report.subscription;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ReportSubscriptionRepository extends JpaRepository<ReportSubscription, Long> {
    List<ReportSubscription> findByTenantIdOrderByIdDesc(UUID tenantId);
    List<ReportSubscription> findByActiveTrue();
}