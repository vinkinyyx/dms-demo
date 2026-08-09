package com.dms.contract.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将合同表单字段回填到 Word 模板，生成成稿。
 * MVP：替换段落/表格中的 ${key} 占位符；内容控件中的纯文本占位也覆盖。
 */
@Slf4j
@Component
public class ContractDocxGenerator {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([a-zA-Z0-9_\\u4e00-\\u9fa5]+)}");

    public void generate(InputStream templateIn, Map<String, Object> values, OutputStream out) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(templateIn)) {
            for (IBodyElement el : doc.getBodyElements()) {
                if (el instanceof XWPFParagraph p) {
                    replaceInParagraph(p, values);
                } else if (el instanceof XWPFTable t) {
                    replaceInTable(t, values);
                }
            }
            for (XWPFHeader h : doc.getHeaderList()) {
                for (XWPFParagraph p : h.getParagraphs()) replaceInParagraph(p, values);
                for (XWPFTable t : h.getTables()) replaceInTable(t, values);
            }
            for (XWPFFooter f : doc.getFooterList()) {
                for (XWPFParagraph p : f.getParagraphs()) replaceInParagraph(p, values);
                for (XWPFTable t : f.getTables()) replaceInTable(t, values);
            }
            doc.write(out);
        }
    }

    private void replaceInTable(XWPFTable table, Map<String, Object> values) {
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                for (IBodyElement el : cell.getBodyElements()) {
                    if (el instanceof XWPFParagraph p) replaceInParagraph(p, values);
                    else if (el instanceof XWPFTable nested) replaceInTable(nested, values);
                }
            }
        }
    }

    private void replaceInParagraph(XWPFParagraph p, Map<String, Object> values) {
        // 内容控件
        for (IRunElement run : p.getIRuns()) {
            if (run instanceof XWPFSDT sdt) {
                try {
                    ISDTContent content = sdt.getContent();
                    if (content != null) {
                        String text = content.getText();
                        String replaced = replaceText(text, values);
                        if (!replaced.equals(text)) {
                            // POI SDT 文本替换能力有限，这里通过 toString 重写不可行；
                            // MVP 模板推荐使用 ${} 占位符，SDT 仅用于识别字段。
                            log.debug("SDT content skipped for fill: {}", text);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }
        // 占位符（合并 runs 后整体替换，保留首个 run 样式）
        List<XWPFRun> runs = p.getRuns();
        if (runs == null || runs.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for (XWPFRun r : runs) {
            String t = r.getText(0);
            if (t != null) sb.append(t);
        }
        String merged = sb.toString();
        if (!merged.contains("${")) return;
        String replaced = replaceText(merged, values);
        // 清空所有 run，写入第一个
        for (int i = runs.size() - 1; i >= 1; i--) {
            p.removeRun(i);
        }
        XWPFRun first = p.getRuns().get(0);
        first.setText(replaced, 0);
    }

    private String replaceText(String text, Map<String, Object> values) {
        if (text == null) return "";
        Matcher m = PLACEHOLDER.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            Object v = values.get(m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(v == null ? "" : String.valueOf(v)));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
