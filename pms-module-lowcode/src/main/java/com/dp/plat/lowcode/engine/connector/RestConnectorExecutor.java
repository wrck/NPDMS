package com.dp.plat.lowcode.engine.connector;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * REST 连接器执行器。
 *
 * <p>config JSON 结构：
 * <pre>{@code
 * {
 *   "url": "https://api.example.com/users",
 *   "method": "GET",
 *   "headers": {...},
 *   "body": {...},
 *   "auth": {
 *     "type": "NONE|BASIC|BEARER|API_KEY|OAUTH2",
 *     // BASIC: credentials="user:pass"
 *     // BEARER: token="xxx"
 *     // API_KEY: headerName, apiKey
 *     // OAUTH2 (批次4-T1): tokenUrl, clientId, clientSecret, scope
 *   },
 *   "retry": {"maxAttempts": 3, "waitMillis": 500},
 *   "circuitBreaker": {                          // 批次4-T2
 *     "failureRateThreshold": 50,
 *     "waitDurationMillis": 60000,
 *     "slidingWindowSize": 100
 *   },
 *   "rateLimiter": {                             // 批次4-T2
 *     "limitForPeriod": 10,
 *     "limitRefreshPeriodMillis": 1000
 *   },
 *   "timeoutMillis": 10000,
 *   "pagination": {                              // 批次4-T3
 *     "type": "NONE|OFFSET|PAGE|NEXT_LINK",
 *     // OFFSET: pageSizeParam, offsetParam, dataPath, maxPages
 *     // PAGE: pageSizeParam, pageParam, dataPath, maxPages
 *     // NEXT_LINK: nextLinkPath, dataPath, maxPages
 *   },
 *   "responseMapping": {                         // 批次4-T4
 *     "dataPath": "$.data.records",              // JsonPath 提取数据
 *     "fieldMappings": [                          // 字段重命名
 *       {"from": "user_id", "to": "userId"},
 *       {"from": "user_name", "to": "userName"}
 *     ]
 *   }
 * }
 * }</pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RestConnectorExecutor {

    private final ObjectMapper objectMapper;

    /** OAuth2 token 缓存：key = tokenUrl|clientId|scope，value = OAuth2TokenEntry */
    private static final Map<String, OAuth2TokenEntry> OAUTH2_TOKEN_CACHE = new ConcurrentHashMap<>();

    /** 连接器级 CircuitBreaker 缓存：key = connector url，避免重复创建 */
    private static final Map<String, CircuitBreaker> CIRCUIT_BREAKER_CACHE = new ConcurrentHashMap<>();

    /** 连接器级 RateLimiter 缓存 */
    private static final Map<String, RateLimiter> RATE_LIMITER_CACHE = new ConcurrentHashMap<>();

    /**
     * 执行 REST 连接器调用。
     *
     * <p>执行链：RateLimiter → CircuitBreaker → Retry → 实际 HTTP 请求。
     * 支持分页聚合（T3）与响应映射（T4）。</p>
     */
    @SuppressWarnings("unchecked")
    public ConnectorResult execute(String configJson, Map<String, Object> params) {
        try {
            Map<String, Object> config = objectMapper.readValue(configJson, new TypeReference<>() {});
            String url = (String) config.get("url");
            String method = ((String) config.getOrDefault("method", "GET")).toUpperCase();
            Map<String, Object> headers = new HashMap<>((Map<String, Object>) config.getOrDefault("headers", Map.of()));
            Object body = config.get("body");
            applyAuth(config, headers);

            // 超时配置
            int timeoutMillis = ((Number) config.getOrDefault("timeoutMillis", 10000)).intValue();
            RestTemplate restTemplate = createRestTemplate(timeoutMillis);

            // Retry 配置（T2）
            Map<String, Object> retryConfig = (Map<String, Object>) config.getOrDefault("retry", Map.of());
            int maxAttempts = ((Number) retryConfig.getOrDefault("maxAttempts", 3)).intValue();
            long waitMillis = ((Number) retryConfig.getOrDefault("waitMillis", 500L)).longValue();
            Retry retry = Retry.of("restConnector", RetryConfig.custom()
                    .maxAttempts(maxAttempts)
                    .waitDuration(Duration.ofMillis(waitMillis))
                    .retryOnResult(r -> ((ConnectorResult) r).getStatus() >= 500)
                    .build());

            // CircuitBreaker 配置（T2）
            CircuitBreaker circuitBreaker = getOrCreateCircuitBreaker(url, config);

            // RateLimiter 配置（T2）
            RateLimiter rateLimiter = getOrCreateRateLimiter(url, config);

            // 组合装饰器：RateLimiter → CircuitBreaker → Retry
            var decoratedSupplier = RateLimiter.decorateSupplier(rateLimiter,
                    CircuitBreaker.decorateSupplier(circuitBreaker,
                            Retry.decorateSupplier(retry,
                                    () -> doRequest(restTemplate, url, method, headers, body, params))));

            ConnectorResult result = decoratedSupplier.get();

            // 分页聚合（T3）
            Map<String, Object> paginationConfig = (Map<String, Object>) config.get("pagination");
            if (paginationConfig != null && !"NONE".equals(paginationConfig.getOrDefault("type", "NONE"))) {
                result = aggregatePagination(restTemplate, config, params, headers, body, result);
            }

            // 响应映射（T4）
            Map<String, Object> mappingConfig = (Map<String, Object>) config.get("responseMapping");
            if (mappingConfig != null && result.getData() != null) {
                result = applyResponseMapping(result, mappingConfig);
            }

            return result;
        } catch (Exception e) {
            log.error("REST 连接器执行失败", e);
            return ConnectorResult.error(500, e.getMessage());
        }
    }

    // ==================== T1: OAuth2 认证 ====================

    /**
     * 获取 OAuth2 access_token（client_credentials 模式），带 token 缓存与自动刷新。
     *
     * <p>缓存 key = tokenUrl|clientId|scope。若缓存的 token 未过期（留 60 秒安全余量），
     * 直接复用；否则重新请求并更新缓存。</p>
     */
    @SuppressWarnings("unchecked")
    private String getOAuth2Token(Map<String, Object> auth, RestTemplate restTemplate) {
        String tokenUrl = (String) auth.get("tokenUrl");
        String clientId = (String) auth.get("clientId");
        String clientSecret = (String) auth.get("clientSecret");
        String scope = (String) auth.getOrDefault("scope", "");
        String cacheKey = tokenUrl + "|" + clientId + "|" + scope;

        // 检查缓存：未过期（留 60 秒安全余量）则直接复用
        OAuth2TokenEntry cached = OAUTH2_TOKEN_CACHE.get(cacheKey);
        if (cached != null && cached.expiresAt > System.currentTimeMillis() + 60_000) {
            return cached.token;
        }

        // 请求新 token
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/x-www-form-urlencoded");
        String formBody = "grant_type=client_credentials"
                + "&client_id=" + clientId
                + "&client_secret=" + clientSecret
                + (scope != null && !scope.isEmpty() ? "&scope=" + scope : "");
        HttpEntity<String> entity = new HttpEntity<>(formBody, headers);
        ResponseEntity<Map> resp = restTemplate.exchange(tokenUrl, HttpMethod.POST, entity, Map.class);
        Map<String, Object> tokenResp = resp.getBody();
        if (tokenResp == null) {
            throw new RuntimeException("OAuth2 token 响应为空");
        }
        String accessToken = (String) tokenResp.get("access_token");
        Number expiresIn = (Number) tokenResp.getOrDefault("expires_in", 3600);
        long expiresAt = System.currentTimeMillis() + expiresIn.longValue() * 1000;
        OAUTH2_TOKEN_CACHE.put(cacheKey, new OAuth2TokenEntry(accessToken, expiresAt));
        log.info("OAuth2 token 已刷新: clientId={}, expiresIn={}s", clientId, expiresIn);
        return accessToken;
    }

    // ==================== T2: CircuitBreaker + RateLimiter ====================

    @SuppressWarnings("unchecked")
    private CircuitBreaker getOrCreateCircuitBreaker(String url, Map<String, Object> config) {
        return CIRCUIT_BREAKER_CACHE.computeIfAbsent(url, key -> {
            Map<String, Object> cbConfig = (Map<String, Object>) config.getOrDefault("circuitBreaker", Map.of());
            float failureRate = ((Number) cbConfig.getOrDefault("failureRateThreshold", 50f)).floatValue();
            long waitMillis = ((Number) cbConfig.getOrDefault("waitDurationMillis", 60000L)).longValue();
            int slidingWindowSize = ((Number) cbConfig.getOrDefault("slidingWindowSize", 100)).intValue();
            CircuitBreakerConfig cbConf = CircuitBreakerConfig.custom()
                    .failureRateThreshold(failureRate)
                    .waitDurationInOpenState(Duration.ofMillis(waitMillis))
                    .slidingWindowSize(slidingWindowSize)
                    .build();
            return CircuitBreaker.of("cb-" + key.hashCode(), cbConf);
        });
    }

    @SuppressWarnings("unchecked")
    private RateLimiter getOrCreateRateLimiter(String url, Map<String, Object> config) {
        return RATE_LIMITER_CACHE.computeIfAbsent(url, key -> {
            Map<String, Object> rlConfig = (Map<String, Object>) config.getOrDefault("rateLimiter", Map.of());
            // 默认不限制（limitForPeriod=0 表示禁用）
            int limitForPeriod = ((Number) rlConfig.getOrDefault("limitForPeriod", 0)).intValue();
            if (limitForPeriod <= 0) {
                // 返回一个不限流的 RateLimiter（大配额）
                return RateLimiter.of("rl-nop-" + key.hashCode(), RateLimiterConfig.custom()
                        .limitForPeriod(Integer.MAX_VALUE)
                        .limitRefreshPeriod(Duration.ofSeconds(1))
                        .timeoutDuration(Duration.ZERO)
                        .build());
            }
            long refreshMillis = ((Number) rlConfig.getOrDefault("limitRefreshPeriodMillis", 1000L)).longValue();
            long timeoutMillis = ((Number) rlConfig.getOrDefault("timeoutDurationMillis", 0L)).longValue();
            return RateLimiter.of("rl-" + key.hashCode(), RateLimiterConfig.custom()
                    .limitForPeriod(limitForPeriod)
                    .limitRefreshPeriod(Duration.ofMillis(refreshMillis))
                    .timeoutDuration(Duration.ofMillis(timeoutMillis))
                    .build());
        });
    }

    // ==================== T3: 分页聚合 ====================

    /**
     * 分页聚合：支持 OFFSET / PAGE / NEXT_LINK 三种模式，迭代请求直到无更多数据或达到 maxPages。
     */
    @SuppressWarnings("unchecked")
    private ConnectorResult aggregatePagination(RestTemplate restTemplate, Map<String, Object> config,
                                                  Map<String, Object> params, Map<String, Object> headers,
                                                  Object body, ConnectorResult firstResult) {
        Map<String, Object> paginationConfig = (Map<String, Object>) config.get("pagination");
        String type = (String) paginationConfig.getOrDefault("type", "NONE");
        String dataPath = (String) paginationConfig.getOrDefault("dataPath", "$.data");
        int maxPages = ((Number) paginationConfig.getOrDefault("maxPages", 10)).intValue();

        List<Object> allRecords = new ArrayList<>();
        // 提取首页数据
        allRecords.addAll(extractRecords(firstResult.getData(), dataPath));

        String baseUrl = (String) config.get("url");
        String method = ((String) config.getOrDefault("method", "GET")).toUpperCase();

        try {
            switch (type) {
                case "OFFSET" -> {
                    String offsetParam = (String) paginationConfig.getOrDefault("offsetParam", "offset");
                    String limitParam = (String) paginationConfig.getOrDefault("limitParam", "limit");
                    int pageSize = ((Number) paginationConfig.getOrDefault("pageSize", 100)).intValue();
                    for (int page = 1; page < maxPages; page++) {
                        int offset = page * pageSize;
                        String urlWithParams = appendQueryParams(baseUrl,
                                Map.of(offsetParam, offset, limitParam, pageSize));
                        ConnectorResult pageResult = doRequest(restTemplate, urlWithParams, method,
                                new HashMap<>(headers), body, params);
                        List<Object> pageRecords = extractRecords(pageResult.getData(), dataPath);
                        if (pageRecords.isEmpty()) break;
                        allRecords.addAll(pageRecords);
                        if (pageRecords.size() < pageSize) break; // 不足一页，说明已到末尾
                    }
                }
                case "PAGE" -> {
                    String pageParam = (String) paginationConfig.getOrDefault("pageParam", "page");
                    String sizeParam = (String) paginationConfig.getOrDefault("sizeParam", "size");
                    int pageSize = ((Number) paginationConfig.getOrDefault("pageSize", 100)).intValue();
                    for (int page = 2; page <= maxPages; page++) {
                        String urlWithParams = appendQueryParams(baseUrl,
                                Map.of(pageParam, page, sizeParam, pageSize));
                        ConnectorResult pageResult = doRequest(restTemplate, urlWithParams, method,
                                new HashMap<>(headers), body, params);
                        List<Object> pageRecords = extractRecords(pageResult.getData(), dataPath);
                        if (pageRecords.isEmpty()) break;
                        allRecords.addAll(pageRecords);
                        if (pageRecords.size() < pageSize) break;
                    }
                }
                case "NEXT_LINK" -> {
                    String nextLinkPath = (String) paginationConfig.getOrDefault("nextLinkPath", "$.next");
                    AtomicReference<Object> currentData = new AtomicReference<>(firstResult.getData());
                    for (int page = 1; page < maxPages; page++) {
                        String nextUrl = extractNextLink(currentData.get(), nextLinkPath);
                        if (nextUrl == null || nextUrl.isEmpty()) break;
                        ConnectorResult pageResult = doRequest(restTemplate, nextUrl, method,
                                new HashMap<>(headers), body, params);
                        allRecords.addAll(extractRecords(pageResult.getData(), dataPath));
                        currentData.set(pageResult.getData());
                    }
                }
                default -> { /* NONE: 仅返回首页 */ }
            }
        } catch (Exception e) {
            log.warn("分页聚合中途失败（已聚合 {} 条记录）: {}", allRecords.size(), e.getMessage());
        }

        return ConnectorResult.builder()
                .status(firstResult.getStatus())
                .data(allRecords)
                .success(true)
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<Object> extractRecords(Object data, String dataPath) {
        if (data == null) return List.of();
        try {
            Object extracted = JsonPath.read(data, dataPath);
            if (extracted instanceof List<?> list) {
                return (List<Object>) list;
            }
            return List.of(extracted);
        } catch (Exception e) {
            // dataPath 解析失败，尝试直接当 list 处理
            if (data instanceof List<?> list) {
                return (List<Object>) list;
            }
            return List.of(data);
        }
    }

    @SuppressWarnings("unchecked")
    private String extractNextLink(Object data, String nextLinkPath) {
        if (data == null) return null;
        try {
            Object next = JsonPath.read(data, nextLinkPath);
            return next != null ? String.valueOf(next) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String appendQueryParams(String url, Map<String, Object> params) {
        if (params == null || params.isEmpty()) return url;
        StringBuilder sb = new StringBuilder(url);
        String sep = url.contains("?") ? "&" : "?";
        for (Map.Entry<String, Object> e : params.entrySet()) {
            sb.append(sep).append(e.getKey()).append("=").append(e.getValue());
            sep = "&";
        }
        return sb.toString();
    }

    // ==================== T4: 响应映射 ====================

    /**
     * 响应映射：用 JsonPath 提取数据 + 字段重命名。
     */
    @SuppressWarnings("unchecked")
    private ConnectorResult applyResponseMapping(ConnectorResult result, Map<String, Object> mappingConfig) {
        Object data = result.getData();
        String dataPath = (String) mappingConfig.get("dataPath");
        if (dataPath != null && !dataPath.isBlank()) {
            try {
                data = JsonPath.read(data, dataPath);
            } catch (Exception e) {
                log.warn("responseMapping dataPath 提取失败: {} → 保持原数据", dataPath);
            }
        }

        List<Map<String, Object>> fieldMappings = (List<Map<String, Object>>) mappingConfig.get("fieldMappings");
        if (fieldMappings != null && !fieldMappings.isEmpty() && data instanceof List<?> list) {
            List<Object> mapped = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> mappedItem = new HashMap<>();
                    for (Map<String, Object> fm : fieldMappings) {
                        String from = (String) fm.get("from");
                        String to = (String) fm.get("to");
                        Object val = map.get(from);
                        mappedItem.put(to, val);
                    }
                    // 保留未映射的字段
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        String key = String.valueOf(entry.getKey());
                        if (!mappedItem.containsKey(key)) {
                            mappedItem.put(key, entry.getValue());
                        }
                    }
                    mapped.add(mappedItem);
                } else {
                    mapped.add(item);
                }
            }
            data = mapped;
        }

        return ConnectorResult.builder()
                .status(result.getStatus())
                .data(data)
                .headers(result.getHeaders())
                .success(result.isSuccess())
                .errorMessage(result.getErrorMessage())
                .build();
    }

    // ==================== HTTP 请求 ====================

    private RestTemplate createRestTemplate(int timeoutMillis) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMillis);
        factory.setReadTimeout(timeoutMillis);
        return new RestTemplate(factory);
    }

    @SuppressWarnings("unchecked")
    private ConnectorResult doRequest(RestTemplate restTemplate, String url, String method,
                                        Map<String, Object> headers, Object body, Map<String, Object> params) {
        try {
            // 参数占位符替换：url 中的 {paramName} 替换为 params 中的值
            String resolvedUrl = resolveUrlParams(url, params);
            HttpHeaders httpHeaders = new HttpHeaders();
            headers.forEach((k, v) -> httpHeaders.set(k, String.valueOf(v)));
            HttpEntity<Object> entity = new HttpEntity<>(body, httpHeaders);
            ResponseEntity<Map> resp = restTemplate.exchange(
                    resolvedUrl, HttpMethod.valueOf(method), entity, Map.class);
            return ConnectorResult.builder()
                    .status(resp.getStatusCode().value())
                    .data(resp.getBody())
                    .success(resp.getStatusCode().is2xxSuccessful())
                    .build();
        } catch (Exception e) {
            return ConnectorResult.error(500, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private String resolveUrlParams(String url, Map<String, Object> params) {
        if (params == null || params.isEmpty()) return url;
        String resolved = url;
        for (Map.Entry<String, Object> e : params.entrySet()) {
            resolved = resolved.replace("{" + e.getKey() + "}", String.valueOf(e.getValue()));
        }
        return resolved;
    }

    // ==================== 认证 ====================

    @SuppressWarnings("unchecked")
    private void applyAuth(Map<String, Object> config, Map<String, Object> headers) {
        Map<String, Object> auth = (Map<String, Object>) config.get("auth");
        if (auth == null) {
            return;
        }
        String type = (String) auth.getOrDefault("type", "NONE");
        switch (type) {
            case "BASIC" -> {
                String token = java.util.Base64.getEncoder()
                        .encodeToString(((String) auth.get("credentials")).getBytes());
                headers.put("Authorization", "Basic " + token);
            }
            case "BEARER" -> headers.put("Authorization", "Bearer " + auth.get("token"));
            case "API_KEY" -> headers.put((String) auth.getOrDefault("headerName", "X-API-Key"), auth.get("apiKey"));
            case "OAUTH2" -> {
                // 批次4-T1: client_credentials 模式 + token 缓存刷新
                RestTemplate authRestTemplate = createRestTemplate(
                        ((Number) config.getOrDefault("timeoutMillis", 10000)).intValue());
                String token = getOAuth2Token(auth, authRestTemplate);
                headers.put("Authorization", "Bearer " + token);
            }
            default -> { /* NONE：不做处理 */ }
        }
    }

    /** OAuth2 token 缓存条目 */
    private record OAuth2TokenEntry(String token, long expiresAt) {}
}
