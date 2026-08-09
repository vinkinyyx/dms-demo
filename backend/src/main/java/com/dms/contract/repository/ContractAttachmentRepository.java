package com.dms.contract.repository;

import com.dms.contract.entity.ContractAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContractAttachmentRepository extends JpaRepository<ContractAttachment, Long> {
    List<ContractAttachment> findByContractIdOrderByUploadedAtDesc(Long contractId);
}
