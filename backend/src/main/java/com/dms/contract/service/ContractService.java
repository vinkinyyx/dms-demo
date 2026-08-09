package com.dms.contract.service;

import com.dms.approval.dto.StartApprovalRequest;
import com.dms.approval.entity.ApprovalInstance;
import com.dms.approval.service.ApprovalService;
import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.util.DocNoGenerator;
import com.dms.common.util.TenantContext;
import com.dms.contract.dto.ContractRequest;
import com.dms.contract.entity.Contract;
import com.dms.contract.entity.ContractAttachment;
import com.dms.contract.entity.ContractRevision;
import com.dms.contract.entity.ContractTemplate;
import com.dms.contract.repository.ContractAttachmentRepository;
import com.dms.contract.repository.ContractRepository;
import com.dms.contract.repository.ContractRevisionRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContractService {

    private final ContractRepository repository;
    private final ContractAttachmentRepository attachmentRepository;
    private final ContractRevisionRepository revisionRepository;
    private final ContractTemplateService templateService;
    private final ContractDocxGenerator docxGenerator;
    @Lazy
    private final ApprovalService approvalService;
    private final DocNoGenerator docNoGenerator;
    private final EntityManager em;

    @Value("${dms.file.storage-root:/data/dms-files}")
    private String storageRoot;

    @Transactional(readOnly = true)
    public Map<String, Object> list(int page, int size, String status, String keyword, Long dealerId, String category) {
        UUID tid = TenantContext.getTenantId();
        PageRequest pageable = PageRequest.of(Math.max(0, page - 1), Math.max(1, Math.min(200, size)),
                Sort.by(Sort.Direction.DESC, "id"));
        Page<Contract> p;
        if (tid != null) {
            if (status != null && !status.isBlank()) p = repository.findByTenantIdAndStatus(tid, status, pageable);
            else p = repository.findByTenantId(tid, pageable);
        } else {
            p = repository.findAll(pageable);
        }
        List<Contract> list = new ArrayList<>(p.getContent());
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.toLowerCase();
            list = list.stream().filter(c -> (c.getCode() != null && c.getCode().toLowerCase().contains(kw))
                    || (c.getName() != null && c.getName().toLowerCase().contains(kw))).toList();
        }
        if (dealerId != null) list = list.stream().filter(c -> dealerId.equals(c.getDealerId())).toList();
        if (category != null && !category.isBlank()) list = list.stream().filter(c -> category.equals(c.getCategory())).toList();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Contract c : list) rows.add(toRow(c));
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("total", p.getTotalElements());
        res.put("page", page);
        res.put("size", size);
        res.put("list", rows);
        return res;
    }

    private Map<String, Object> toRow(Contract c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("code", c.getCode());
        m.put("name", c.getName());
        m.put("category", c.getCategory());
        m.put("applicationType", c.getApplicationType());
        m.put("dealerId", c.getDealerId());
        m.put("vendorParty", c.getVendorParty());
        m.put("dealerParty", c.getDealerParty());
        m.put("validFrom", c.getValidFrom());
        m.put("validTo", c.getValidTo());
        m.put("signedAmount", c.getSignedAmount());
        m.put("status", c.getStatus());
        m.put("templateId", c.getTemplateId());
        m.put("sourceFileId", c.getSourceFileId());
        m.put("submittedAt", c.getSubmittedAt());
        m.put("effectiveAt", c.getEffectiveAt());
        m.put("createdAt", c.getCreatedAt());
        m.put("updatedAt", c.getUpdatedAt());
        return m;
    }

    @Transactional(readOnly = true)
    public byte[] export(String status, String keyword, Long dealerId, String category) throws java.io.IOException {
        java.util.UUID tid = TenantContext.getTenantId();
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10000,
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "id"));
        org.springframework.data.domain.Page<Contract> p;
        if (tid != null) {
            if (status != null && !status.isBlank()) p = repository.findByTenantIdAndStatus(tid, status, pageable);
            else p = repository.findByTenantId(tid, pageable);
        } else {
            p = repository.findAll(pageable);
        }
        java.util.List<Contract> list = new java.util.ArrayList<>(p.getContent());
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.toLowerCase();
            list = list.stream().filter(c -> (c.getCode() != null && c.getCode().toLowerCase().contains(kw))
                    || (c.getName() != null && c.getName().toLowerCase().contains(kw))).toList();
        }
        if (dealerId != null) list = list.stream().filter(c -> dealerId.equals(c.getDealerId())).toList();
        if (category != null && !category.isBlank()) list = list.stream().filter(c -> category.equals(c.getCategory())).toList();
        java.util.List<java.util.Map<String, Object>> rows = new java.util.ArrayList<>();
        for (Contract c : list) {
            java.util.Map<String, Object> m = toRow(c);
            m.put("validFromText", c.getValidFrom() != null ? c.getValidFrom().toString() : "");
            m.put("validToText", c.getValidTo() != null ? c.getValidTo().toString() : "");
            m.put("createdAtText", c.getCreatedAt() != null ? c.getCreatedAt().toString() : "");
            m.put("updatedAtText", c.getUpdatedAt() != null ? c.getUpdatedAt().toString() : "");
            rows.add(m);
        }
        String[] headers = {"ID", "合同编号", "合同名称", "分类", "申请类型", "经销商ID",
                "甲方", "乙方", "签约金额", "有效期起", "有效期止", "状态",
                "提交时间", "生效时间", "创建时间", "更新时间"};
        String[] fieldNames = {"id", "code", "name", "category", "applicationType", "dealerId",
                "vendorParty", "dealerParty", "signedAmount", "validFromText", "validToText", "status",
                "submittedAt", "effectiveAt", "createdAtText", "updatedAtText"};
        return com.dms.common.util.ExcelExportUtils.exportMapToExcel(rows, headers, fieldNames);
    }
    @Transactional(readOnly = true)
    public Map<String, Object> getDetail(Long id) {
        Contract c = get(id);
        Map<String, Object> res = new LinkedHashMap<>(toRow(c));
        res.put("refContractId", c.getRefContractId());
        res.put("templateVersion", c.getTemplateVersion());
        res.put("signCity", c.getSignCity());
        res.put("targetAmount", c.getTargetAmount());
        res.put("paymentTerms", c.getPaymentTerms());
        res.put("settlementCycle", c.getSettlementCycle());
        res.put("ownerName", c.getOwnerName());
        res.put("ownerPhone", c.getOwnerPhone());
        res.put("formData", c.getFormData());
        res.put("terminatedAt", c.getTerminatedAt());
        if (c.getTemplateId() != null) {
            try { res.put("template", templateService.get(c.getTemplateId())); } catch (Exception ignored) {}
        }
        res.put("attachments", attachmentRepository.findByContractIdOrderByUploadedAtDesc(id));
        res.put("revisions", revisionRepository.findByContractIdOrderByCreatedAtAsc(id));
        return res;
    }

    @Transactional(readOnly = true)
    public Contract get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "合同不存在"));
    }

    @Transactional
    public Contract create(ContractRequest req) {
        UUID tid = TenantContext.getTenantId();
        if (tid == null) throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");
        Contract c = new Contract();
        c.setTenantId(tid);
        c.setCode(docNoGenerator.next("CT"));
        c.setApplicationType(req.getApplicationType() != null ? req.getApplicationType() : "NEW");
        c.setStatus("draft");
        c.setCreatedBy(TenantContext.getUserId());
        c.setUpdatedBy(TenantContext.getUserId());
        applyRequest(c, req);
        c.ensureMaps();
        c.setUpdatedAt(OffsetDateTime.now());
        return repository.save(c);
    }

    @Transactional
    public Contract update(Long id, ContractRequest req) {
        Contract c = get(id);
        if (!"draft".equals(c.getStatus()) && !"rejected".equals(c.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "当前状态不可编辑");
        }
        applyRequest(c, req);
        c.setUpdatedBy(TenantContext.getUserId());
        c.setUpdatedAt(OffsetDateTime.now());
        if ("rejected".equals(c.getStatus())) c.setStatus("draft");
        return repository.save(c);
    }

    private void applyRequest(Contract c, ContractRequest req) {
        if (req.getName() != null) c.setName(req.getName());
        if (req.getCategory() != null) c.setCategory(req.getCategory());
        if (req.getApplicationType() != null) c.setApplicationType(req.getApplicationType());
        if (req.getRefContractId() != null) c.setRefContractId(req.getRefContractId());
        if (req.getTemplateId() != null) c.setTemplateId(req.getTemplateId());
        if (req.getDealerId() != null) c.setDealerId(req.getDealerId());
        if (req.getVendorParty() != null) c.setVendorParty(req.getVendorParty());
        if (req.getDealerParty() != null) c.setDealerParty(req.getDealerParty());
        if (req.getSignCity() != null) c.setSignCity(req.getSignCity());
        if (req.getValidFrom() != null) c.setValidFrom(req.getValidFrom());
        if (req.getValidTo() != null) c.setValidTo(req.getValidTo());
        if (req.getTargetAmount() != null) c.setTargetAmount(req.getTargetAmount());
        if (req.getSignedAmount() != null) c.setSignedAmount(req.getSignedAmount());
        if (req.getPaymentTerms() != null) c.setPaymentTerms(req.getPaymentTerms());
        if (req.getSettlementCycle() != null) c.setSettlementCycle(req.getSettlementCycle());
        if (req.getOwnerName() != null) c.setOwnerName(req.getOwnerName());
        if (req.getOwnerPhone() != null) c.setOwnerPhone(req.getOwnerPhone());
        if (req.getFormData() != null) {
            c.ensureMaps();
            c.getFormData().putAll(req.getFormData());
        }
    }

    @Transactional
    public void delete(Long id) {
        Contract c = get(id);
        if (!"draft".equals(c.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "只有草稿可以删除");
        }
        c.setDeletedAt(OffsetDateTime.now());
        repository.save(c);
    }

    @Transactional
    public Contract submit(Long id) {
        Contract c = get(id);
        if (!"draft".equals(c.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "只有草稿可以提交");
        }
        if (c.getName() == null || c.getName().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "合同名称不能为空");
        }
        if (c.getCategory() == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "合同分类不能为空");
        }
        if (c.getTemplateId() != null) {
            try {
                c.setSourceFileId(generateDocx(c));
            } catch (BusinessException be) {
                throw be;
            } catch (Exception e) {
                log.error("生成合同成稿失败 contract={}: {}", c.getId(), e.getMessage(), e);
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "生成合同成稿失败: " + e.getMessage());
            }
        }
        c.setStatus("pending");
        c.setSubmittedAt(OffsetDateTime.now());
        c.setUpdatedAt(OffsetDateTime.now());
        c = repository.save(c);

        long rounds = revisionRepository.countByContractId(c.getId());
        revisionRepository.save(ContractRevision.builder()
                .tenantId(c.getTenantId()).contractId(c.getId()).round((int) rounds + 1)
                .action("submit").operatorId(TenantContext.getUserId())
                .operatorName(TenantContext.getUsername()).snapshot(buildSnapshot(c)).build());

        try {
            StartApprovalRequest request = new StartApprovalRequest();
            request.setBusinessType("CONTRACT");
            request.setBusinessId(c.getId());
            request.setBusinessCode(c.getCode());
            request.setTitle("合同审批: " + (c.getName() != null ? c.getName() : c.getCode()));
            request.setBusinessSnapshot(buildSnapshot(c));
            ApprovalInstance instance = approvalService.start(request);
            if (instance != null && (instance.getStatus().name().equals("APPROVED")
                    || instance.getStatus().name().equals("AUTO_APPROVED"))) {
                c.setStatus("effective");
                c.setEffectiveAt(OffsetDateTime.now());
                repository.save(c);
            }
        } catch (Exception e) {
            c.setStatus("draft");
            repository.save(c);
            throw e;
        }
        return c;
    }

    private Map<String, Object> buildSnapshot(Contract c) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("code", c.getCode());
        s.put("name", c.getName());
        s.put("category", c.getCategory());
        s.put("applicationType", c.getApplicationType());
        s.put("dealerId", c.getDealerId());
        s.put("vendorParty", c.getVendorParty());
        s.put("dealerParty", c.getDealerParty());
        s.put("validFrom", c.getValidFrom());
        s.put("validTo", c.getValidTo());
        s.put("signedAmount", c.getSignedAmount());
        s.put("formData", c.getFormData());
        return s;
    }

    @Transactional
    public Contract withdraw(Long id) {
        Contract c = get(id);
        if (!"pending".equals(c.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "只有审批中的合同可以撤回");
        }
        c.setStatus("draft");
        c.setUpdatedAt(OffsetDateTime.now());
        revisionRepository.save(ContractRevision.builder()
                .tenantId(c.getTenantId()).contractId(c.getId())
                .round((int) revisionRepository.countByContractId(c.getId()) + 1)
                .action("withdraw").operatorId(TenantContext.getUserId())
                .operatorName(TenantContext.getUsername()).build());
        return repository.save(c);
    }

    @Transactional
    public void markApproved(Long id) {
        Contract c = get(id);
        c.setStatus("effective");
        c.setEffectiveAt(OffsetDateTime.now());
        c.setUpdatedAt(OffsetDateTime.now());
        repository.save(c);
        revisionRepository.save(ContractRevision.builder()
                .tenantId(c.getTenantId()).contractId(c.getId())
                .round((int) revisionRepository.countByContractId(c.getId()) + 1)
                .action("approve").operatorId(TenantContext.getUserId())
                .operatorName(TenantContext.getUsername()).build());
    }

    @Transactional
    public void markRejected(Long id, String comment) {
        Contract c = get(id);
        c.setStatus("rejected");
        c.setUpdatedAt(OffsetDateTime.now());
        repository.save(c);
        revisionRepository.save(ContractRevision.builder()
                .tenantId(c.getTenantId()).contractId(c.getId())
                .round((int) revisionRepository.countByContractId(c.getId()) + 1)
                .action("reject")
                .operatorId(TenantContext.getUserId())
                .operatorName(TenantContext.getUsername())
                .snapshot(comment != null ? Map.of("comment", comment) : null).build());
    }

    @SuppressWarnings("unchecked")
    private Long generateDocx(Contract c) throws Exception {
        ContractTemplate tpl = templateService.get(c.getTemplateId());
        if (tpl.getOriginalFileId() == null) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "模板未上传 Word 文件");
        }
        Object[] row = (Object[]) em.createNativeQuery(
                "SELECT tenant_id, rel_path, original_name, content_type FROM files WHERE id = ?1")
                .setParameter(1, tpl.getOriginalFileId()).getSingleResult();
        String rel = (String) row[1];
        String original = (String) row[2];
        String contentType = (String) row[3];

        Path tplPath = Paths.get(storageRoot, rel);
        if (!Files.exists(tplPath)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "模板文件不存在");
        }
        UUID tid = c.getTenantId();
        String today = new SimpleDateFormat("yyyyMMdd").format(new Date());
        Path dir = Paths.get(storageRoot, tid.toString(), "contract-final", today);
        Files.createDirectories(dir);
        String outName = UUID.randomUUID().toString().replace("-", "") + ".docx";
        Path outPath = dir.resolve(outName);

        Map<String, Object> values = new LinkedHashMap<>();
        if (c.getFormData() != null) values.putAll(c.getFormData());
        values.put("contract_code", c.getCode());
        values.put("contract_name", c.getName());
        values.put("vendor_party", c.getVendorParty());
        values.put("dealer_party", c.getDealerParty());
        values.put("valid_from", c.getValidFrom() != null ? c.getValidFrom().toString() : "");
        values.put("valid_to", c.getValidTo() != null ? c.getValidTo().toString() : "");

        try (InputStream in = Files.newInputStream(tplPath); OutputStream out = Files.newOutputStream(outPath)) {
            docxGenerator.generate(in, values, out);
        }

        String outRel = Paths.get(tid.toString(), "contract-final", today, outName).toString().replace("\\", "/");
        long size = Files.size(outPath);
        Number fileId = (Number) em.createNativeQuery(
                "INSERT INTO files (tenant_id, biz_type, biz_id, original_name, stored_name, rel_path, content_type, size_bytes, uploaded_by) "
                + "VALUES (?1, 'contract-final', ?2, ?3, ?4, ?5, ?6, ?7, ?8) RETURNING id")
                .setParameter(1, tid)
                .setParameter(2, c.getId())
                .setParameter(3, (original != null ? original : "contract") + "-" + c.getCode() + ".docx")
                .setParameter(4, outName)
                .setParameter(5, outRel)
                .setParameter(6, contentType != null ? contentType : "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .setParameter(7, size)
                .setParameter(8, TenantContext.getUserId())
                .getSingleResult();
        return fileId.longValue();
    }

    @Transactional
    public ContractAttachment addAttachment(Long contractId, Long fileId, String fileName, Long size, String category) {
        Contract c = get(contractId);
        ContractAttachment a = ContractAttachment.builder()
                .tenantId(c.getTenantId())
                .contractId(contractId)
                .category(category != null ? category : "annex")
                .fileId(fileId)
                .fileUrl("/api/files/" + fileId + "/download")
                .fileName(fileName)
                .sizeBytes(size)
                .uploadedBy(TenantContext.getUserId())
                .build();
        return attachmentRepository.save(a);
    }

    @Transactional
    public void deleteAttachment(Long contractId, Long attachmentId) {
        attachmentRepository.findById(attachmentId).ifPresent(a -> {
            if (a.getContractId() != null && a.getContractId().equals(contractId)) {
                a.setDeletedAt(OffsetDateTime.now());
                attachmentRepository.save(a);
            }
        });
    }


}
