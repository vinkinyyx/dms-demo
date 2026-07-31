package com.dms.operationlog.filter;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ContentCachingFilter extends OncePerRequestFilter {

    private static final int MAX_CONTENT_LENGTH = 10 * 1024 * 1024;
    public static final String ATTR_BODY = "__oplog_request_body";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String contentType = request.getContentType();
        if (contentType != null && contentType.toLowerCase().contains("multipart")) {
            filterChain.doFilter(request, response);
            return;
        }
        String method = request.getMethod();
        boolean hasBody = "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method);
        int len = request.getContentLength();
        if (hasBody && len > 0 && len <= MAX_CONTENT_LENGTH) {
            byte[] body = request.getInputStream().readAllBytes();
            String bodyStr = new String(body, StandardCharsets.UTF_8);
            if (bodyStr.length() > 8192) {
                bodyStr = bodyStr.substring(0, 8192) + "...(truncated)";
            }
            CachedBodyRequestWrapper wrapped = new CachedBodyRequestWrapper(request, body);
            wrapped.setAttribute(ATTR_BODY, bodyStr);
            filterChain.doFilter(wrapped, response);
        } else {
            filterChain.doFilter(request, response);
        }
    }

    public static String getBody(HttpServletRequest request) {
        return (String) request.getAttribute(ATTR_BODY);
    }

    private static class CachedBodyRequestWrapper extends HttpServletRequestWrapper {
        private final byte[] body;

        CachedBodyRequestWrapper(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return new ServletInputStream() {
                private final ByteArrayInputStream in = new ByteArrayInputStream(body);

                @Override
                public boolean isFinished() { return in.available() == 0; }

                @Override
                public boolean isReady() { return true; }

                @Override
                public void setReadListener(ReadListener readListener) { }

                @Override
                public int read() throws IOException { return in.read(); }
            };
        }

        @Override
        public BufferedReader getReader() throws IOException {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
