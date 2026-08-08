package com.dms.platform.apilog.repository;

import com.dms.platform.apilog.entity.ApiHttpLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ApiHttpLogRepository extends JpaRepository<ApiHttpLog, Long>,
        JpaSpecificationExecutor<ApiHttpLog> {
}