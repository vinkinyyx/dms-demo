package com.dms.apilog;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiCallLogRepository extends JpaRepository<ApiCallLog, Long> {
}
