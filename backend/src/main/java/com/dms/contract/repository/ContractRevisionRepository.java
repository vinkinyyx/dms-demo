package com.dms.contract.repository;

import com.dms.contract.entity.ContractRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContractRevisionRepository extends JpaRepository<ContractRevision, Long> {
    List<ContractRevision> findByContractIdOrderByCreatedAtAsc(Long contractId);
    long countByContractId(Long contractId);
}
