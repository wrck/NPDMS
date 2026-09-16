package cn.iocoder.yudao.module.pms.integration.extsystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.iocoder.yudao.module.pms.integration.extsystem.exception.BusinessException;
import cn.iocoder.yudao.module.pms.integration.extsystem.exception.IntegrationException;
import cn.iocoder.yudao.module.pms.integration.extsystem.config.OaProperties;
import cn.iocoder.yudao.module.pms.integration.extsystem.constant.IntegrationConstants;
import cn.iocoder.yudao.module.pms.integration.extsystem.dto.OaHealthDto;
import cn.iocoder.yudao.module.pms.integration.extsystem.entity.IntegrationLog;
import cn.iocoder.yudao.module.pms.integration.extsystem.model.oa.OaTodoRequest;
import cn.iocoder.yudao.module.pms.integration.extsystem.model.oa.OaTokenResponse;
import cn.iocoder.yudao.module.pms.integration.extsystem.oauth.OAuthTokenCache;
import cn.iocoder.yudao.module.pms.integration.extsystem.service.IIntegrationLogService;
import cn.iocoder.yudao.module.pms.integration.extsystem.service.OaIntegrationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of {@link OaIntegrationService}.
 *
 * <p>Uses a distributed OAuth2 token cache ({@link OAuthTokenCache} / Redis Hash +
 * ahead-of-expiry refresh + single-flight lock), shared with the D365 and FP
 * adapters. The cache auto-renews the token when less than 5 minutes remain
 * before expiry. Token 获取失败计数与告警由 {@link OAuthTokenCache} 统一管理。
 * All outbound calls are recorded in {@link IntegrationLog} with
 * {@code logType="OA"} for retry.</p>
 *
 * <p><b>Resilience4j 弹性保护</b>：{@link #pushTodo}、{@link #completeTodo}、
 * {@link #transferTask} 三个对外部 OA 的入口方法均通过
 * {@code @CircuitBreaker} + {@code @Bulkhead} + {@code @Retry} 三层保护。
 * 失败时（HTTP 错误 / token 获取失败）抛出 {@link IntegrationException}，
 * 触发熔断器记录失败；熔断 OPEN 时由 fallback 包装为 IntegrationException 上抛，
 * 由 GlobalExceptionHandler 统一返回 HTTP 503。
 * {@link #healthCheck()} 不加注解，避免熔断时健康端点无法探测恢复。</p>
 *
 * <p><b>异常语义变更</b>：原实现中 {@code execute} 方法捕获异常后返回 {@code false}，
 * 现改为抛出 {@link IntegrationException}。调用方（如 {@code OaTaskListener}）
 * 已通过 try-catch 吞掉异常，确保 OA 集成失败不影响主流程事务。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OaIntegrationServiceImpl implements OaIntegrationService {

    private final OaProperties oaProperties;
    private final RestTemplate integrationRestTemplate;
    private final IIntegrationLogService integrationLogService;
    private final ObjectMapper objectMapper;
    private final OAuthTokenCache oauthTokenCache;

    @Override
    public String getAccessToken() {
        return oauthTokenCache.getToken("oa", this::fetchOaToken);
    }

    /**
     * 调用 OA OAuth2 token 端点获取新 token，封装为 {@link OAuthTokenCache.TokenInfo}
     * 供分布式缓存使用。
     *
     * @return token 信息
     * @throws IntegrationException 当 token 端点调用失败或响应为空时
     */
    private OAuthTokenCache.TokenInfo fetchOaToken() {
        OaTokenResponse response = requestToken();
        if (response == null || response.getAccessToken() == null) {
            throw new IntegrationException("oa", "OA token response is empty");
        }
        long now = System.currentTimeMillis() / 1000;
        int expiresIn = response.getExpiresIn() == null ? 3600 : response.getExpiresIn();
        return OAuthTokenCache.TokenInfo.builder()
                .accessToken(response.getAccessToken())
                .expiresAt(now + expiresIn)
                .tokenType(response.getTokenType())
                .build();
    }

    @Override
    @CircuitBreaker(name = "oaCircuitBreaker", fallbackMethod = "pushTodoFallback")
    @Bulkhead(name = "oaBulkhead")
    @Retry(name = "oaRetry")
    public boolean pushTodo(OaTodoRequest request) {
        String url = oaProperties.getBaseUrl() + "/todo/push";
        String body = writeJson(request);
        IntegrationLog logRecord = IntegrationLog.builder()
                .logType(IntegrationConstants.LOG_TYPE_OA)
                .businessType(IntegrationConstants.BIZ_OA_TODO_PUSH)
                .businessId(request.getBusinessKey() != null
                        ? request.getBusinessKey()
                        : request.getProcessInstanceId())
                .requestUrl(url)
                .requestBody(body)
                .build();
        logRecord = integrationLogService.log(logRecord);
        return execute(HttpMethod.POST, logRecord, url, body);
    }

    @Override
    @CircuitBreaker(name = "oaCircuitBreaker", fallbackMethod = "completeTodoFallback")
    @Bulkhead(name = "oaBulkhead")
    @Retry(name = "oaRetry")
    public boolean completeTodo(String businessKey) {
        String url = oaProperties.getBaseUrl() + "/todo/complete";
        String body = writeJson(java.util.Map.of("businessKey", businessKey == null ? "" : businessKey));
        IntegrationLog logRecord = IntegrationLog.builder()
                .logType(IntegrationConstants.LOG_TYPE_OA)
                .businessType(IntegrationConstants.BIZ_OA_TODO_COMPLETE)
                .businessId(businessKey)
                .requestUrl(url)
                .requestBody(body)
                .build();
        logRecord = integrationLogService.log(logRecord);
        return execute(HttpMethod.PUT, logRecord, url, body);
    }

    @Override
    @CircuitBreaker(name = "oaCircuitBreaker", fallbackMethod = "transferTaskFallback")
    @Bulkhead(name = "oaBulkhead")
    @Retry(name = "oaRetry")
    public boolean transferTask(String businessKey, String newHandlerUserId) {
        String url = oaProperties.getBaseUrl() + "/todo/transfer";
        String body = writeJson(java.util.Map.of(
                "businessKey", businessKey == null ? "" : businessKey,
                "newHandlerUserId", newHandlerUserId == null ? "" : newHandlerUserId));
        IntegrationLog logRecord = IntegrationLog.builder()
                .logType(IntegrationConstants.LOG_TYPE_OA)
                .businessType(IntegrationConstants.BIZ_OA_TODO_TRANSFER)
                .businessId(businessKey)
                .requestUrl(url)
                .requestBody(body)
                .build();
        logRecord = integrationLogService.log(logRecord);
        return execute(HttpMethod.PUT, logRecord, url, body);
    }

    /**
     * 重试之前记录的 OA 调用：根据日志 ID 取出存储的请求 URL 与请求体，
     * 按 businessType 推断 HTTP method，重新发送请求并更新日志状态。
     *
     * <p>本方法不加 {@code @Retry}/@{@code CircuitBreaker}/@{@code Bulkhead} 注解，
     * 避免在调度重试（{@code RetryServiceImpl.scheduledRetry}）之上叠加额外的
     * Resilience4j 重试层。异常被 try-catch 吞掉（仅记录 WARN 日志），
     * 确保调度任务不会因单条日志重试失败而中断后续重试。</p>
     *
     * @param logId 集成日志 ID
     * @return 重试后的最新日志记录
     * @throws BusinessException 日志不存在时抛出
     */
    @Override
    public IntegrationLog retry(Long logId) {
        IntegrationLog logRecord = integrationLogService.getById(logId);
        if (logRecord == null) {
            throw new BusinessException("集成日志不存在");
        }
        HttpMethod method = resolveHttpMethod(logRecord.getBusinessType());
        try {
            execute(method, logRecord, logRecord.getRequestUrl(), logRecord.getRequestBody());
        } catch (Exception e) {
            // execute 内部已调用 markFailed 更新日志状态，此处仅记录告警
            log.warn("OA retry failed for log {}: {}", logId, e.getMessage());
        }
        return integrationLogService.getById(logId);
    }

    /**
     * 根据业务类型推断 HTTP method：
     * <ul>
     *   <li>{@code TODO_PUSH} → POST（创建待办）</li>
     *   <li>{@code TODO_COMPLETE} → PUT（完成待办）</li>
     *   <li>{@code TODO_TRANSFER} → PUT（转办待办）</li>
     *   <li>其他 / 未知 → POST（安全默认值）</li>
     * </ul>
     *
     * @param businessType 业务类型常量
     * @return 对应的 HTTP method
     */
    private HttpMethod resolveHttpMethod(String businessType) {
        if (IntegrationConstants.BIZ_OA_TODO_PUSH.equals(businessType)) {
            return HttpMethod.POST;
        }
        if (IntegrationConstants.BIZ_OA_TODO_COMPLETE.equals(businessType)
                || IntegrationConstants.BIZ_OA_TODO_TRANSFER.equals(businessType)) {
            return HttpMethod.PUT;
        }
        return HttpMethod.POST;
    }

    @Override
    public OaHealthDto healthCheck() {
        boolean tokenValid = false;
        boolean connected = false;
        try {
            getAccessToken();
            tokenValid = true;
            // A trivial GET against the base URL confirms reachability.
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            integrationRestTemplate.exchange(oaProperties.getBaseUrl(),
                    HttpMethod.GET, new HttpEntity<>(headers), String.class);
            connected = true;
        } catch (Exception e) {
            log.debug("OA health check failed: {}", e.getMessage());
        }
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        long pushCount = countLogs(IntegrationConstants.STATUS_SUCCESS, since);
        long failCount = countLogs(IntegrationConstants.STATUS_FAILED, since);
        List<IntegrationLog> recent = recentLogs(10);
        return OaHealthDto.builder()
                .connected(connected)
                .tokenValid(tokenValid)
                .recentPushCount((int) pushCount)
                .recentFailCount((int) failCount)
                .recentLogs(recent)
                .build();
    }

    // ---- Resilience4j fallback methods ----
    // 当熔断器 OPEN（CallNotPermittedException）或方法抛出 record-exceptions 中的异常时，
    // Resilience4j 调用对应 fallback；fallback 包装为 IntegrationException 上抛，
    // 由 GlobalExceptionHandler 统一返回 HTTP 503。

    /**
     * pushTodo 熔断 / 失败降级：记录日志并抛出 IntegrationException。
     *
     * @param request 原方法入参
     * @param t       触发降级的异常
     */
    private boolean pushTodoFallback(OaTodoRequest request, Throwable t) {
        log.error("OA pushTodo 熔断降级 businessKey={} err={}",
                request == null ? null : request.getBusinessKey(), t.getMessage());
        throw new IntegrationException("oa", "OA 待办推送服务暂不可用，请稍后重试", t);
    }

    /**
     * completeTodo 熔断 / 失败降级。
     */
    private boolean completeTodoFallback(String businessKey, Throwable t) {
        log.error("OA completeTodo 熔断降级 businessKey={} err={}", businessKey, t.getMessage());
        throw new IntegrationException("oa", "OA 待办完成服务暂不可用，请稍后重试", t);
    }

    /**
     * transferTask 熔断 / 失败降级。
     */
    private boolean transferTaskFallback(String businessKey, String newHandlerUserId, Throwable t) {
        log.error("OA transferTask 熔断降级 businessKey={} newHandler={} err={}",
                businessKey, newHandlerUserId, t.getMessage());
        throw new IntegrationException("oa", "OA 任务转办服务暂不可用，请稍后重试", t);
    }

    // ---- internals ----

    private boolean execute(HttpMethod method, IntegrationLog logRecord, String url, String body) {
        try {
            String token = getAccessToken();
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = integrationRestTemplate.exchange(
                    url, method, entity, String.class);
            integrationLogService.markSuccess(logRecord.getId(), response.getBody());
            // RestTemplate 默认对 4xx/5xx 抛出 HttpStatusCodeException，到达此处即 2xx 成功
            return response.getStatusCode().is2xxSuccessful();
        } catch (IntegrationException e) {
            // 已经是 IntegrationException（来自 token 获取失败），直接透传
            integrationLogService.markFailed(logRecord.getId(), e.getMessage());
            throw e;
        } catch (Exception e) {
            integrationLogService.markFailed(logRecord.getId(), e.getMessage());
            log.warn("OA integration call failed for log {}: {}", logRecord.getId(), e.getMessage());
            throw new IntegrationException("oa", "OA integration call failed: " + e.getMessage(), e);
        }
    }

    private OaTokenResponse requestToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", oaProperties.getClientId());
        form.add("client_secret", oaProperties.getClientSecret());
        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(form, headers);
        try {
            return integrationRestTemplate.postForObject(
                    oaProperties.getTokenUrl(), entity, OaTokenResponse.class);
        } catch (RestClientException e) {
            throw new IntegrationException("oa", "OA token request failed: " + e.getMessage(), e);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException("Failed to serialize OA request body: " + e.getMessage());
        }
    }

    private long countLogs(String status, LocalDateTime since) {
        LambdaQueryWrapper<IntegrationLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(IntegrationLog::getLogType, IntegrationConstants.LOG_TYPE_OA)
                .eq(IntegrationLog::getResponseStatus, status)
                .ge(IntegrationLog::getCreateTime, since);
        return integrationLogService.count(wrapper);
    }

    private List<IntegrationLog> recentLogs(int limit) {
        LambdaQueryWrapper<IntegrationLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(IntegrationLog::getLogType, IntegrationConstants.LOG_TYPE_OA)
                .orderByDesc(IntegrationLog::getCreateTime)
                .last("LIMIT " + Math.max(limit, 1));
        List<IntegrationLog> logs = integrationLogService.list(wrapper);
        return logs == null ? new ArrayList<>() : logs;
    }
}
