package com.dms.common.util;

import org.springframework.http.HttpHeaders;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * v3.7.3: 工具类，生成符合 RFC 5987 的 Content-Disposition 头，支持中英文文件名。
 */
public final class ContentDispositionUtils {

    private ContentDispositionUtils() {}

    /**
     * 生成 attachment 形式的 Content-Disposition，filename* 走 UTF-8 编码。
     * 例：attachment; filename="default.xlsx"; filename*=UTF-8''default.xlsx
     */
    public static String attachment(String filename) {
        if (filename == null || filename.isEmpty()) {
            filename = "download";
        }
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        StringBuilder sb = new StringBuilder();
        sb.append("attachment; filename=\"").append(asciiFallback(filename)).append("\"; filename*=UTF-8''").append(encoded);
        return sb.toString();
    }

    /**
     * 拼装到 HttpHeaders 上。
     */
    public static void setAttachment(HttpHeaders headers, String filename) {
        headers.set(HttpHeaders.CONTENT_DISPOSITION, attachment(filename));
    }

    private static String asciiFallback(String filename) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < filename.length(); i++) {
            char c = filename.charAt(i);
            sb.append(c < 0x80 ? c : '_');
        }
        return sb.toString();
    }
}
