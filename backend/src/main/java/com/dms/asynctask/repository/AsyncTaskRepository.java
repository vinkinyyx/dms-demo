package com.dms.asynctask.repository;

import com.dms.asynctask.entity.AsyncTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AsyncTaskRepository extends JpaRepository<AsyncTask, Long> {
    Page<AsyncTask> findByTenantIdOrderByIdDesc(UUID tenantId, Pageable pageable);
    Page<AsyncTask> findByTenantIdAndTaskTypeOrderByIdDesc(UUID tenantId, String taskType, Pageable pageable);
}