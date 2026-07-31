package com.dms.operationlog.repository;

import com.dms.operationlog.entity.OpLogEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OpLogRepository extends JpaRepository<OpLogEntry, Long> {
}
