package com.dms.apilog;

import com.dms.common.util.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 接口调用日志记录服务（v3.8.2）。
 *
 * <p>同时服务两类记录：
 * <ul>
 *   <li>IN：{@link com.dms.apilog.ApiCallLogFilter} 在过滤器中写入（外部调用 DMS）</li>
 *   <li>OUT：业务代码调用 {@link #callExternal(ExternalCall)} 或 {@link #recordOutbound(ApiCallLog)} 写入（DMS 调用外部）</li>
 * </ul>
 * 写库异步执行，避免影响主链路性能。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiCallLogService {

    private static final int MAX_BODY = 64 * 1024;
    private final ApiCallLogRepository repository;
    private final ObjectMapper objectMapper;

    @Async
    public void recordInbound(ApiCallLog entry) {
        save(entry);
    }

    @Async
    public void recordOutbound(ApiCallLog entry) {
        save(entry);
    }

    private void save(ApiCallLog entry) {
        try {
            entry.setRequestBody(truncate(entry.getRequestBody()));
            entry.setResponseBody(truncate(entry.getResponseBody()));
            entry.setRequestHeaders(truncate(entry.getRequestHeaders()));
            entry.setErrorMsg(truncate(entry.getErrorMsg()));
            repository.save(entry);
        } catch (Exception e) {
            log.warn("api_call_log persist failed: {}", e.getMessage());
        }
    }

    /**
     * 发起一次外部 HTTP 调用并自动记录出站日志。
     * 未来新增对接系统时，直接复用本方法即可，无需重复写日志逻辑。
     */
    public ExternalResult callExternal(ExternalCall call) {
        ApiCallLog entry = new ApiCallLog();
        entry.setDirection("OUT");
        entry.setTenantId(TenantContext.getTenantId());
        entry.setUserId(TenantContext.getUserId());
        entry.setUsername(TenantContext.getUsername());
        entry.setRequestId(MDC.get("requestId"));
        entry.setTraceId(call.traceId);
        entry.setSystem(call.system);
        entry.setEndpoint(call.endpoint);
        entry.setHttpMethod(call.method == null ? "GET" : call.method.toUpperCase());
        entry.setUrl(call.url);
        entry.setPath(safePath(call.url));
        entry.setAppKey(call.appKey);
        entry.setStartedAt(OffsetDateTime.now());

        ExternalResult result = new ExternalResult();
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(call.connectTimeoutMs <= 0 ? 10000 : call.connectTimeoutMs))
                    .build();
            HttpRequest.Builder rb = HttpRequest.newBuilder().uri(URI.create(call.url));
            if (call.headers != null) call.headers.forEach((k, v) -> rb.header(k, String.valueOf(v)));
            entry.setRequestHeaders(safeJson(call.headers));
            HttpRequest.BodyPublisher bp = call.body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(call.body);
            rb.method(entry.getHttpMethod(), bp);
            entry.setRequestBody(call.body);

            long t0 = System.currentTimeMillis();
            HttpResponse<String> resp = client.send(rb.build(), HttpResponse.BodyHandlers.ofString());
            long spent = System.currentTimeMillis() - t0;
            entry.setStatusCode(resp.statusCode());
            entry.setSuccess(resp.statusCode() >= 200 && resp.statusCode() < 300);
            entry.setResponseBody(resp.body());
            entry.setSpentMs(spent);
            entry.setFinishedAt(OffsetDateTime.now());
            result.statusCode = resp.statusCode();
            result.body = resp.body();
            result.success = entry.getSuccess();
        } catch (Exception e) {
            entry.setSuccess(false);
            entry.setErrorMsg(e.getClass().getSimpleName() + ": " + e.getMessage());
            entry.setSpentMs(System.currentTimeMillis() - millis(entry.getStartedAt()));
            entry.setFinishedAt(OffsetDateTime.now());
            result.success = false;
            result.error = e.getMessage();
        } finally {
            recordOutbound(entry);
            result.logId = entry.getId();
        }
        return result;
    }

    private long millis(OffsetDateTime t) { return t == null ? System.currentTimeMillis() : t.toInstant().toEpochMilli(); }

    private String safePath(String url) {
        if (url == null) return null;
        try { return URI.create(url).getPath(); } catch (Exception e) { return url.length() > 512 ? url.substring(0, 512) : url; }
    }

    private String safeJson(Object o) {
        if (o == null) return null;
        try { return objectMapper.writeValueAsString(o); } catch (Exception e) { return String.valueOf(o); }
    }

    private String truncate(String s) {
        if (s == null) return null;
        return s.length() > MAX_BODY ? s.substring(0, MAX_BODY) + "...(truncated)" : s;
    }

    /** 出站调用入参 */
    public static class ExternalCall {
        public String system;
        public String endpoint;
        public String url;
        public String method = "GET";
        public String body;
        public Map<String, String> headers;
        public String appKey;
        public String traceId;
        public long connectTimeoutMs;
    }

    /** 出站调用结果 */
    public static class ExternalResult {
        public int statusCode;
        public String body;
        public boolean success;
        public String error;
        public Long logId;
    }
}
