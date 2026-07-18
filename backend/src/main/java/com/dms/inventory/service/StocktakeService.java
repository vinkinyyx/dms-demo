/*
 * 盘点服务：upload → 保存盘点单及明细，简化不做库存修正（走 InventoryAdjustment 修正差异）。
 */
package com.dms.inventory.service;

import com.dms.common.BusinessException;
import com.dms.common.ErrorCode;
import com.dms.common.util.TenantContext;
import com.dms.inventory.entity.Stocktake;
import com.dms.inventory.entity.StocktakeLine;
import com.dms.inventory.repository.StocktakeLineRepository;
import com.dms.inventory.repository.StocktakeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StocktakeService {

    private final StocktakeRepository stocktakeRepository;
    private final StocktakeLineRepository lineRepository;

    @Transactional
    public Stocktake upload(Stocktake stocktake, List<StocktakeLine> lines) {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少 tenantId");
        }
        if (stocktake.getPeriodYyyymm() == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缺少盘点期间 periodYyyymm");
        }
        stocktake.setId(null);
        stocktake.setTenantId(tenantId);
        stocktake.setUploadedAt(OffsetDateTime.now());
        stocktake.setUploadedBy(TenantContext.getUserId());
        stocktake.ensureJson();

        // 汇总差异
        int totalLines = lines == null ? 0 : lines.size();
        BigDecimal totalDiff = BigDecimal.ZERO;
        if (lines != null) {
            for (StocktakeLine l : lines) {
                BigDecimal book = l.getBookQty() == null ? BigDecimal.ZERO : l.getBookQty();
                BigDecimal actual = l.getActualQty() == null ? BigDecimal.ZERO : l.getActualQty();
                BigDecimal diff = actual.subtract(book);
                l.setDiffQty(diff);
                totalDiff = totalDiff.add(diff.abs());
            }
        }
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalLines", totalLines);
        summary.put("totalDiffAbs", totalDiff.toPlainString());
        stocktake.setDiffSummary(summary);
        stocktake.setUpdatedAt(OffsetDateTime.now());

        Stocktake saved = stocktakeRepository.save(stocktake);
        if (lines != null) {
            for (StocktakeLine l : lines) {
                l.setId(null);
                l.setStocktakeId(saved.getId());
                lineRepository.save(l);
            }
        }
        log.info("盘点单 {} 上传完毕，明细 {} 行，差异合计 {}", saved.getId(), totalLines, totalDiff);
        return saved;
    }
}
