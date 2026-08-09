package com.dms.contract.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.util.DocNoGenerator;
import com.dms.common.util.TenantContext;
import com.dms.contract.dto.TemplateRequest;
import com.dms.contract.entity.ContractTemplate;
import com.dms.contract.repository.ContractTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContractTemplateService {

    private final ContractTemplateRepository repository;
    private final DocNoGenerator docNoGenerator;

    @Transactional(readOnly = true)
    public Map<String, Object> list(int page, int size, String category, String status, String keyword) {
        UUID tid = TenantContext.getTenantId();
        PageRequest pageable = PageRequest.of(Math.max(0, page - 1), Math.max(1, Math.min(200, size)),
                Sort.by(Sort.Direction.DESC, "id"));
        Page<ContractTemplate> p;
        if (tid != null) {
            if (status != null && !status.isBlank()) p = repository.findByTenantIdAndStatus(tid, status, pageable);
            else if (category != null && !category.isBlank()) p = repository.findByTenantIdAndCategory(tid, category, pageable);
            else p = repository.findByTenantId(tid, pageable);
        } else {
            p = repository.findAll(pageable);
        }
        List<ContractTemplate> list = p.getContent();
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.toLowerCase();
            list = list.stream().filter(t -> (t.getName() != null && t.getName().toLowerCase().contains(kw))
                    || (t.getCode() != null && t.getCode().toLowerCase().contains(kw))).toList();
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("total", p.getTotalElements());
        res.put("page", page);
        res.put("size", size);
        res.put("list", list);
        return res;
    }

    @Transactional(readOnly = true)
    public ContractTemplate get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "合同模板不存在"));
    }

    @Transactional(readOnly = true)
    public ContractTemplate matchPublished(String category) {
        UUID tid = TenantContext.getTenantId();
        if (tid == null || category == null || category.isBlank()) return null;
        return repository.findByTenantIdAndCategoryAndStatus(tid, category, "published").orElse(null);
    }

    @Transactional
    public ContractTemplate create(TemplateRequest req) {
        UUID tid = TenantContext.getTenantId();
        if (tid == null) throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");
        if (req.getName() == null || req.getName().isBlank()) throw new BusinessException(ErrorCode.PARAM_MISSING, "模板名称不能为空");
        if (req.getCategory() == null || req.getCategory().isBlank()) throw new BusinessException(ErrorCode.PARAM_MISSING, "绑定分类不能为空");
        ContractTemplate t = ContractTemplate.builder()
                .tenantId(tid)
                .code(req.getCode() != null && !req.getCode().isBlank() ? req.getCode() : docNoGenerator.next("CTT"))
                .name(req.getName())
                .category(req.getCategory())
                .originalFileId(req.getOriginalFileId())
                .fields(req.getFields() != null ? req.getFields() : new ArrayList<>())
                .version(1)
                .status("draft")
                .createdBy(TenantContext.getUserId())
                .updatedBy(TenantContext.getUserId())
                .updatedAt(OffsetDateTime.now())
                .build();
        return repository.save(t);
    }

    @Transactional
    public ContractTemplate update(Long id, TemplateRequest req) {
        ContractTemplate t = get(id);
        if (!"draft".equals(t.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "已发布的模板不可编辑，请新建版本");
        }
        if (req.getName() != null) t.setName(req.getName());
        if (req.getCategory() != null) t.setCategory(req.getCategory());
        if (req.getOriginalFileId() != null) t.setOriginalFileId(req.getOriginalFileId());
        if (req.getFields() != null) t.setFields(req.getFields());
        t.setUpdatedBy(TenantContext.getUserId());
        t.setUpdatedAt(OffsetDateTime.now());
        return repository.save(t);
    }

    @Transactional
    public ContractTemplate publish(Long id) {
        ContractTemplate t = get(id);
        if (!"draft".equals(t.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "只有草稿状态的模板可以发布");
        }
        // 同分类已发布模板置为 disabled
        UUID tid = t.getTenantId();
        repository.findByTenantIdAndCategoryAndStatus(tid, t.getCategory(), "published")
                .ifPresent(prev -> {
                    prev.setStatus("disabled");
                    prev.setUpdatedAt(OffsetDateTime.now());
                    repository.save(prev);
                });
        t.setStatus("published");
        t.setPublishedAt(OffsetDateTime.now());
        t.setUpdatedAt(OffsetDateTime.now());
        t.setUpdatedBy(TenantContext.getUserId());
        return repository.save(t);
    }

    @Transactional
    public ContractTemplate newVersion(Long id) {
        ContractTemplate base = get(id);
        ContractTemplate v = ContractTemplate.builder()
                .tenantId(base.getTenantId())
                .code(base.getCode())
                .name(base.getName())
                .category(base.getCategory())
                .originalFileId(base.getOriginalFileId())
                .fields(base.getFields())
                .numberingRules(base.getNumberingRules())
                .version(base.getVersion() + 1)
                .status("draft")
                .createdBy(TenantContext.getUserId())
                .updatedBy(TenantContext.getUserId())
                .updatedAt(OffsetDateTime.now())
                .build();
        return repository.save(v);
    }

    @Transactional
    public void disable(Long id) {
        ContractTemplate t = get(id);
        if ("published".equals(t.getStatus())) {
            t.setStatus("disabled");
            t.setUpdatedAt(OffsetDateTime.now());
            repository.save(t);
        }
    }

    @Transactional
    public void delete(Long id) {
        ContractTemplate t = get(id);
        if ("published".equals(t.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "已发布的模板不能删除，请先停用");
        }
        t.setDeletedAt(OffsetDateTime.now());
        repository.save(t);
    }
}
