package com.dms.apilog;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * 缓存响应体，供接口日志过滤器在请求结束后读取。
 */
public class CachedBodyHttpServletResponse extends HttpServletResponseWrapper {

    private final ByteArrayOutputStream content = new ByteArrayOutputStream();
    private final ServletOutputStream outputStream = new CachedServletOutputStream(content);
    private PrintWriter writer;

    public CachedBodyHttpServletResponse(HttpServletResponse response) {
        super(response);
    }

    @Override
    public ServletOutputStream getOutputStream() {
        return outputStream;
    }

    @Override
    public PrintWriter getWriter() {
        if (writer == null) {
            writer = new PrintWriter(content, true, StandardCharsets.UTF_8);
        }
        return writer;
    }

    public byte[] getContentAsBytes() {
        if (writer != null) writer.flush();
        return content.toByteArray();
    }

    public String getContentAsString() {
        return new String(getContentAsBytes(), StandardCharsets.UTF_8);
    }

    private static class CachedServletOutputStream extends ServletOutputStream {
        private final ByteArrayOutputStream baos;
        CachedServletOutputStream(ByteArrayOutputStream baos) { this.baos = baos; }
        @Override public boolean isReady() { return true; }
        @Override public void setWriteListener(WriteListener writeListener) { }
        @Override public void write(int b) { baos.write(b); }
    }
}
