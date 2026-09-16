package cn.iocoder.yudao.module.pms.integration.extsystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.iocoder.yudao.module.pms.integration.extsystem.exception.BusinessException;
import cn.iocoder.yudao.module.pms.integration.extsystem.exception.IntegrationException;
import cn.iocoder.yudao.module.pms.integration.extsystem.config.FpProperties;
import cn.iocoder.yudao.module.pms.integration.extsystem.constant.IntegrationConstants;
import cn.iocoder.yudao.module.pms.integration.extsystem.d365.entity.D365Invoice;
import cn.iocoder.yudao.module.pms.integration.extsystem.d365.mapper.D365InvoiceMapper;
import cn.iocoder.yudao.module.pms.integration.extsystem.dto.FpHealthDto;
import cn.iocoder.yudao.module.pms.integration.extsystem.dto.InvoiceOcrResult;
import cn.iocoder.yudao.module.pms.integration.extsystem.dto.PaymentCallbackDto;
import cn.iocoder.yudao.module.pms.integration.extsystem.entity.IntegrationLog;
import cn.iocoder.yudao.module.pms.integration.extsystem.model.fp.FpResponse;
import cn.iocoder.yudao.module.pms.integration.extsystem.model.fp.FpTokenResponse;
import cn.iocoder.yudao.module.pms.integration.extsystem.model.fp.SettlementPushRequest;
import cn.iocoder.yudao.module.pms.integration.extsystem.oauth.OAuthTokenCache;
import cn.iocoder.yudao.module.pms.integration.extsystem.service.FpIntegrationService;
import cn.iocoder.yudao.module.pms.integration.extsystem.service.IIntegrationLogService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of {@link FpIntegrationService}.
 *
 * <p>Performs a real OAuth2 {@code client_credentials} flow with a distributed
 * token cache ({@link OAuthTokenCache} / Redis Hash + ahead-of-expiry refresh +
 * single-flight lock). Token 获取失败计数与告警由 {@link OAuthTokenCache} 统一管理。
 * Settlement push uses exponential backoff retry (1/2/4/8/16 min, up to 5 retries)
 * driven by a daemon {@link ScheduledExecutorService}; every attempt is logged to
 * {@link IntegrationLog} with full request/response. Invoice OCR and payment
 * callbacks are also fully logged.</p>
 *
 * <p><b>Resilience4j 弹性保护</b>：{@link #pushSettlement}、{@link #ocrInvoice}、
 * {@link #handlePaymentCallback} 三个对外部 FP 的入口方法均通过
 * {@code @CircuitBreaker} + {@code @Bulkhead} + {@code @Retry} 三层保护。
 * {@link #pushSettlementOnce} 作为内部方法被 {@link #pushSettlement} 和
 * 后台重试调度器调用，不单独标注（避免双重代理）。
 * {@link #healthCheck()} 不加注解，避免熔断时健康端点无法探测恢复。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FpIntegrationServiceImpl implements FpIntegrationService {

    /** Exponential backoff delays in minutes for settlement push retries. */
    private static final long[] BACKOFF_MINUTES = {1L, 2L, 4L, 8L, 16L};

    /** Max retry attempts (after the initial synchronous attempt). */
    private static final int MAX_RETRIES = 5;

    private final FpProperties fpProperties;
    private final RestTemplate integrationRestTemplate;
    private final IIntegrationLogService integrationLogService;
    private final ObjectMapper objectMapper;
    private final ApplicationContext applicationContext;
    private final D365InvoiceMapper d365InvoiceMapper;
    private final OAuthTokenCache oauthTokenCache;

    /** Daemon scheduler for settlement push retries. */
    private final ScheduledExecutorService retryScheduler =
            Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "fp-settlement-retry");
                t.setDaemon(true);
                return t;
            });

    @PreDestroy
    public void shutdown() {
        retryScheduler.shutdownNow();
    }

    @Override
    public String getAccessToken() {
        return oauthTokenCache.getToken("fp", this::fetchFpToken);
    }

    /**
     * 调用 FP OAuth2 token 端点获取新 token，封装为 {@link OAuthTokenCache.TokenInfo}
     * 供分布式缓存使用。
     *
     * @return token 信息
     * @throws IntegrationException 当 token 端点调用失败或响应为空时
     */
    private OAuthTokenCache.TokenInfo fetchFpToken() {
        FpTokenResponse response = requestToken();
        if (response == null || response.getAccessToken() == null) {
            throw new IntegrationException("fp", "FP token response is empty");
        }
        long now = System.currentTimeMillis() / 1000;
        int expiresIn = response.getExpiresIn() == null ? 3600 : response.getExpiresIn();
        return OAuthTokenCache.TokenInfo.builder()
                .accessToken(response.getAccessToken())
                .expiresAt(now + expiresIn)
                .build();
    }

    @Override
    @CircuitBreaker(name = "fpCircuitBreaker", fallbackMethod = "pushSettlementFallback")
    @Bulkhead(name = "fpBulkhead")
    @Retry(name = "fpRetry")
    public FpResponse<String> pushSettlement(SettlementPushRequest request) {
        FpResponse<String> response;
        try {
            response = pushSettlementOnce(request);
        } catch (Exception e) {
            // First attempt failed; schedule background retries and surface
            // the failure to the caller.
            scheduleRetry(request, 0);
            throw e instanceof IntegrationException ie ? ie
                    : new IntegrationException("fp", "FP settlement push failed: " + e.getMessage(), e);
        }
        if (response == null || !response.isSuccess()) {
            scheduleRetry(request, 0);
        }
        return response;
    }

    @Override
    public FpResponse<String> pushSettlementOnce(SettlementPushRequest request) {
        String url = fpProperties.getBaseUrl() + "/settlements/push";
        String body = writeJson(request);
        IntegrationLog logRecord = IntegrationLog.builder()
                .logType(IntegrationConstants.LOG_TYPE_FP)
                .businessType(IntegrationConstants.BIZ_SETTLEMENT)
                .businessId(request.getSettlementNo())
                .requestUrl(url)
                .requestBody(body)
                .build();
        logRecord = integrationLogService.log(logRecord);
        return executePost(logRecord, url, body);
    }

    /**
     * Schedule a retry of the settlement push with exponential backoff.
     *
     * @param request    the original settlement push request
     * @param retryIndex 0-based retry index (0 = first retry)
     */
    private void scheduleRetry(SettlementPushRequest request, int retryIndex) {
        if (retryIndex >= MAX_RETRIES || retryIndex >= BACKOFF_MINUTES.length) {
            log.error("FP settlement push for {} exhausted all {} retries; giving up.",
                    request.getSettlementNo(), MAX_RETRIES);
            return;
        }
        long delayMin = BACKOFF_MINUTES[retryIndex];
        log.warn("FP settlement push for {} failed; scheduling retry {}/{} in {} min",
                request.getSettlementNo(), retryIndex + 1, MAX_RETRIES, delayMin);
        retryScheduler.schedule(() -> {
            try {
                FpResponse<String> response = pushSettlementOnce(request);
                if (response != null && response.isSuccess()) {
                    return;
                }
                scheduleRetry(request, retryIndex + 1);
            } catch (Exception e) {
                log.warn("FP settlement retry {}/{} for {} failed: {}",
                        retryIndex + 1, MAX_RETRIES, request.getSettlementNo(), e.getMessage());
                scheduleRetry(request, retryIndex + 1);
            }
        }, delayMin, TimeUnit.MINUTES);
    }

    @Override
    @CircuitBreaker(name = "fpCircuitBreaker", fallbackMethod = "ocrInvoiceFallback")
    @Bulkhead(name = "fpBulkhead")
    @Retry(name = "fpRetry")
    public D365Invoice ocrInvoice(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("发票图片不能为空");
        }
        String url = fpProperties.getBaseUrl() + "/ocr/invoice";
        IntegrationLog logRecord = IntegrationLog.builder()
                .logType(IntegrationConstants.LOG_TYPE_FP)
                .businessType(IntegrationConstants.BIZ_OCR_INVOICE)
                .businessId(file.getOriginalFilename())
                .requestUrl(url)
                .requestBody("[multipart file: " + file.getOriginalFilename() + "]")
                .build();
        logRecord = integrationLogService.log(logRecord);
        try {
            String token = getAccessToken();
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("file", new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename() != null ? file.getOriginalFilename() : "invoice.bin";
                }
            }).contentType(MediaType.APPLICATION_OCTET_STREAM);

            HttpEntity<MultiValueMap<String, HttpEntity<?>>> entity =
                    new HttpEntity<>(builder.build(), headers);
            ResponseEntity<String> response = integrationRestTemplate.postForEntity(url, entity, String.class);
            String responseBody = response.getBody();
            integrationLogService.markSuccess(logRecord.getId(), responseBody);

            InvoiceOcrResult ocr = parseOcrResponse(responseBody);
            if (ocr == null || ocr.getInvoiceNo() == null) {
                throw new BusinessException("FP OCR 未识别到发票号");
            }
            return applyOcrResult(ocr);
        } catch (IntegrationException e) {
            // 已经是 IntegrationException（来自 token / 响应解析），直接透传
            integrationLogService.markFailed(logRecord.getId(), e.getMessage());
            throw e;
        } catch (BusinessException e) {
            integrationLogService.markFailed(logRecord.getId(), e.getMessage());
            throw e;
        } catch (Exception e) {
            integrationLogService.markFailed(logRecord.getId(), e.getMessage());
            throw new IntegrationException("fp", "FP OCR call failed: " + e.getMessage(), e);
        }
    }

    @Override
    @CircuitBreaker(name = "fpCircuitBreaker", fallbackMethod = "handlePaymentCallbackFallback")
    @Bulkhead(name = "fpBulkhead")
    @Retry(name = "fpRetry")
    public void handlePaymentCallback(PaymentCallbackDto callback) {
        if (callback == null || callback.getSettlementNo() == null) {
            throw new BusinessException("支付回调缺少结算单号");
        }
        String url = fpProperties.getBaseUrl() + "/payment-callback";
        String body = writeJson(callback);
        IntegrationLog logRecord = IntegrationLog.builder()
                .logType(IntegrationConstants.LOG_TYPE_FP)
                .businessType(IntegrationConstants.BIZ_PAYMENT_CALLBACK)
                .businessId(callback.getSettlementNo())
                .requestUrl(url)
                .requestBody(body)
                .build();
        logRecord = integrationLogService.log(logRecord);
        try {
            BaseMapper<?> settlementMapper = lookupMapper("settlementMapper");
            if (settlementMapper == null) {
                integrationLogService.markFailed(logRecord.getId(),
                        "SettlementMapper bean not available");
                throw new IntegrationException("fp", "SettlementMapper bean not available");
            }
            UpdateWrapper wrapper = new UpdateWrapper();
            wrapper.eq("settlement_no", callback.getSettlementNo());
            wrapper.set("payment_status", callback.getPaymentStatus());
            int rows = settlementMapper.update(null, wrapper);
            integrationLogService.markSuccess(logRecord.getId(),
                    "updated rows=" + rows + ", paymentStatus=" + callback.getPaymentStatus());
        } catch (IntegrationException e) {
            // 已经是 IntegrationException，直接透传（日志已标记失败）
            throw e;
        } catch (Exception e) {
            integrationLogService.markFailed(logRecord.getId(), e.getMessage());
            throw new IntegrationException("fp", "FP payment callback failed: " + e.getMessage(), e);
        }
    }

    @Override
    public IntegrationLog retry(Long logId) {
        IntegrationLog logRecord = integrationLogService.getById(logId);
        if (logRecord == null) {
            throw new BusinessException("集成日志不存在");
        }
        try {
            executePost(logRecord, logRecord.getRequestUrl(), logRecord.getRequestBody());
        } catch (Exception e) {
            log.warn("FP retry failed for log {}: {}", logId, e.getMessage());
        }
        return integrationLogService.getById(logId);
    }

    @Override
    public FpHealthDto healthCheck() {
        boolean tokenValid = false;
        boolean connected = false;
        try {
            getAccessToken();
            tokenValid = true;
            integrationRestTemplate.exchange(fpProperties.getBaseUrl(),
                    HttpMethod.GET, new HttpEntity<>(jsonHeaders()), String.class);
            connected = true;
        } catch (Exception e) {
            log.debug("FP health check failed: {}", e.getMessage());
        }
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        long pushCount = countLogs(IntegrationConstants.STATUS_SUCCESS, since);
        long failCount = countLogs(IntegrationConstants.STATUS_FAILED, since);
        List<IntegrationLog> recent = recentLogs(10);
        return FpHealthDto.builder()
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
     * pushSettlement 熔断 / 失败降级：记录日志并抛出 IntegrationException。
     * 注意：即使熔断，后台重试调度器仍会在 circuit 外运行（scheduleRetry 已在
     * 抛异常前调用），后续重试会通过 pushSettlementOnce 直接执行，不再走熔断器。
     *
     * @param request 原方法入参
     * @param t       触发降级的异常
     */
    private FpResponse<String> pushSettlementFallback(SettlementPushRequest request, Throwable t) {
        log.error("FP pushSettlement 熔断降级 settlementNo={} err={}",
                request == null ? null : request.getSettlementNo(), t.getMessage());
        throw new IntegrationException("fp", "FP 结算推送服务暂不可用，请稍后重试", t);
    }

    /**
     * ocrInvoice 熔断 / 失败降级。
     */
    private D365Invoice ocrInvoiceFallback(MultipartFile file, Throwable t) {
        log.error("FP ocrInvoice 熔断降级 file={} err={}",
                file == null ? null : file.getOriginalFilename(), t.getMessage());
        throw new IntegrationException("fp", "FP 发票 OCR 服务暂不可用，请稍后重试", t);
    }

    /**
     * handlePaymentCallback 熔断 / 失败降级。
     */
    private void handlePaymentCallbackFallback(PaymentCallbackDto callback, Throwable t) {
        log.error("FP handlePaymentCallback 熔断降级 settlementNo={} err={}",
                callback == null ? null : callback.getSettlementNo(), t.getMessage());
        throw new IntegrationException("fp", "FP 付款回调处理服务暂不可用，请稍后重试", t);
    }

    // ---- internals ----

    @SuppressWarnings({"unchecked", "rawtypes"})
    private FpResponse<String> executePost(IntegrationLog logRecord, String url, String body) {
        try {
            String token = getAccessToken();
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            ResponseEntity<FpResponse> response = integrationRestTemplate.postForEntity(
                    url, entity, FpResponse.class);
            FpResponse<String> fpResponse = (FpResponse<String>) response.getBody();
            integrationLogService.markSuccess(logRecord.getId(), writeJson(fpResponse));
            return fpResponse;
        } catch (IntegrationException e) {
            // 已经是 IntegrationException（来自 token 获取失败），直接透传
            integrationLogService.markFailed(logRecord.getId(), e.getMessage());
            throw e;
        } catch (Exception e) {
            integrationLogService.markFailed(logRecord.getId(), e.getMessage());
            throw new IntegrationException("fp", "FP integration call failed: " + e.getMessage(), e);
        }
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    private FpTokenResponse requestToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", fpProperties.getClientId());
        form.add("client_secret", fpProperties.getClientSecret());
        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(form, headers);
        try {
            return integrationRestTemplate.postForObject(
                    fpProperties.getTokenUrl(), entity, FpTokenResponse.class);
        } catch (RestClientException e) {
            throw new IntegrationException("fp", "FP token request failed: " + e.getMessage(), e);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException("Failed to serialize FP request body: " + e.getMessage());
        }
    }

    private InvoiceOcrResult parseOcrResponse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body == null ? "{}" : body);
            JsonNode data = root.has("data") && root.get("data").isObject()
                    ? root.get("data") : root;
            return InvoiceOcrResult.builder()
                    .invoiceNo(textOrNull(data, "invoiceNo"))
                    .amount(decimalOrNull(data, "amount"))
                    .taxAmount(decimalOrNull(data, "taxAmount"))
                    .totalAmount(decimalOrNull(data, "totalAmount"))
                    .vendorName(textOrNull(data, "vendorName"))
                    .rawResponse(body)
                    .build();
        } catch (Exception e) {
            throw new IntegrationException("fp", "Failed to parse FP OCR response: " + e.getMessage(), e);
        }
    }

    private D365Invoice applyOcrResult(InvoiceOcrResult ocr) {
        D365Invoice existing = findInvoiceByNo(ocr.getInvoiceNo());
        D365Invoice record = existing == null
                ? D365Invoice.builder().invoiceNo(ocr.getInvoiceNo()).build()
                : existing;
        record.setAmount(ocr.getAmount());
        record.setTaxAmount(ocr.getTaxAmount());
        record.setTotalAmount(ocr.getTotalAmount());
        record.setVendorName(ocr.getVendorName());
        record.setOcrStatus(IntegrationConstants.OCR_STATUS_RECOGNIZED);
        if (existing == null) {
            d365InvoiceMapper.insert(record);
        } else {
            d365InvoiceMapper.updateById(record);
        }
        return record;
    }

    private D365Invoice findInvoiceByNo(String invoiceNo) {
        LambdaQueryWrapper<D365Invoice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(D365Invoice::getInvoiceNo, invoiceNo).last("LIMIT 1");
        return d365InvoiceMapper.selectOne(wrapper);
    }

    @SuppressWarnings("rawtypes")
    private BaseMapper<?> lookupMapper(String beanName) {
        try {
            Object bean = applicationContext.getBean(beanName);
            return bean instanceof BaseMapper ? (BaseMapper) bean : null;
        } catch (Exception e) {
            log.debug("Mapper bean '{}' not available: {}", beanName, e.getMessage());
            return null;
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        return child != null && !child.isNull() ? child.asText() : null;
    }

    private static java.math.BigDecimal decimalOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull() || !child.isNumber()) {
            return null;
        }
        return child.decimalValue();
    }

    private long countLogs(String status, LocalDateTime since) {
        LambdaQueryWrapper<IntegrationLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(IntegrationLog::getLogType, IntegrationConstants.LOG_TYPE_FP)
                .eq(IntegrationLog::getResponseStatus, status)
                .ge(IntegrationLog::getCreateTime, since);
        return integrationLogService.count(wrapper);
    }

    private List<IntegrationLog> recentLogs(int limit) {
        LambdaQueryWrapper<IntegrationLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(IntegrationLog::getLogType, IntegrationConstants.LOG_TYPE_FP)
                .orderByDesc(IntegrationLog::getCreateTime)
                .last("LIMIT " + Math.max(limit, 1));
        List<IntegrationLog> logs = integrationLogService.list(wrapper);
        return logs == null ? new ArrayList<>() : logs;
    }
}
