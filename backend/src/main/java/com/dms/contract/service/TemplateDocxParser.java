package com.dms.contract.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析 Word(.docx) 模板：识别内容控件(SDT)与 ${placeholder} 占位符，生成字段定义。
 */
@Slf4j
@Component
public class TemplateDocxParser {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([a-zA-Z0-9_\\u4e00-\\u9fa5]+)}");

    public List<Map<String, Object>> parse(InputStream in) throws Exception {
        Map<String, Map<String, Object>> fields = new LinkedHashMap<>();
        try (XWPFDocument doc = new XWPFDocument(in)) {
            // 1) 内容控件（段落级）
            for (IBodyElement el : doc.getBodyElements()) {
                if (el instanceof XWPFParagraph p) {
                    scanParagraph(p, fields);
                } else if (el instanceof XWPFTable t) {
                    scanTable(t, fields);
                }
            }
            // 2) 页眉页脚占位符
            for (XWPFHeader h : doc.getHeaderList()) {
                for (XWPFParagraph p : h.getParagraphs()) {
                    scanPlaceholders(p.getText(), fields);
                }
            }
            for (XWPFFooter f : doc.getFooterList()) {
                for (XWPFParagraph p : f.getParagraphs()) {
                    scanPlaceholders(p.getText(), fields);
                }
            }
        }
        return new ArrayList<>(fields.values());
    }

    private void scanTable(XWPFTable table, Map<String, Map<String, Object>> fields) {
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                for (IBodyElement el : cell.getBodyElements()) {
                    if (el instanceof XWPFParagraph p) {
                        scanParagraph(p, fields);
                    } else if (el instanceof XWPFTable nested) {
                        scanTable(nested, fields);
                    }
                }
            }
        }
    }

    private void scanParagraph(XWPFParagraph p, Map<String, Map<String, Object>> fields) {
        // 段落中的内容控件
        for (IRunElement run : p.getIRuns()) {
            if (run instanceof XWPFSDT sdt) {
                addSdt(sdt, fields);
            }
        }
        // 占位符
        scanPlaceholders(p.getText(), fields);
    }

    private void addSdt(XWPFSDT sdt, Map<String, Map<String, Object>> fields) {
        try {
            ISDTContent content = sdt.getContent();
            String text = content == null ? "" : content.getText();
            String tag = sdt.getTag();
            String title = sdt.getTitle();
            String key = (tag != null && !tag.isBlank()) ? tag : text;
            if (key == null || key.isBlank()) return;
            key = key.trim();
            String type = inferType(text);
            addField(fields, key, title != null && !title.isBlank() ? title : key, type);
        } catch (Exception e) {
            log.warn("解析内容控件失败: {}", e.getMessage());
        }
    }

    private String inferType(String text) {
        // 简化：根据文本内容/控件类型推断
        if (text != null) {
            if (text.matches(".*\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}.*")) return "date";
            if (text.matches("^[\\d,]+\\.?\\d*$")) return "number";
        }
        return "text";
    }

    private void scanPlaceholders(String text, Map<String, Map<String, Object>> fields) {
        if (text == null) return;
        Matcher m = PLACEHOLDER.matcher(text);
        while (m.find()) {
            String key = m.group(1);
            addField(fields, key, key, "text");
        }
    }

    private void addField(Map<String, Map<String, Object>> fields, String key, String label, String type) {
        fields.computeIfAbsent(key, k -> {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("key", k);
            f.put("label", label);
            f.put("type", type);
            f.put("required", false);
            f.put("approvalVisible", true);
            f.put("group", "基本信息");
            f.put("sort", fields.size() + 1);
            return f;
        });
    }
}
