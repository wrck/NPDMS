package cn.iocoder.yudao.module.pms.integration.extsystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.iocoder.yudao.module.pms.integration.extsystem.exception.BusinessException;
import cn.iocoder.yudao.module.pms.integration.extsystem.exception.IntegrationException;
import cn.iocoder.yudao.module.pms.integration.extsystem.config.D365Properties;
import cn.iocoder.yudao.module.pms.integration.extsystem.constant.IntegrationConstants;
import cn.iocoder.yudao.module.pms.integration.extsystem.d365.entity.D365Invoice;
import cn.iocoder.yudao.module.pms.integration.extsystem.d365.entity.D365PurchaseReceipt;
import cn.iocoder.yudao.module.pms.integration.extsystem.d365.mapper.D365InvoiceMapper;
import cn.iocoder.yudao.module.pms.integration.extsystem.d365.mapper.D365PurchaseReceiptMapper;
import cn.iocoder.yudao.module.pms.integration.extsystem.dto.D365HealthDto;
import cn.iocoder.yudao.module.pms.integration.extsystem.entity.IntegrationLog;
import cn.iocoder.yudao.module.pms.integration.extsystem.model.d365.PurchaseHeader;
import cn.iocoder.yudao.module.pms.integration.extsystem.model.d365.PurchaseReceiptHeader;
import cn.iocoder.yudao.module.pms.integration.extsystem.model.d365.TokenResponse;
import cn.iocoder.yudao.module.pms.integration.extsystem.oauth.OAuthTokenCache;
import cn.iocoder.yudao.module.pms.integration.extsystem.service.D365IntegrationService;
import cn.iocoder.yudao.module.pms.integration.extsystem.service.IIntegrationLogService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
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
 * Implementation of {@link D365IntegrationService}.
 *
 * <p>Performs a real OAuth2 {@code client_credentials} flow with a distributed
 * token cache ({@link OAuthTokenCache} / Redis Hash + ahead-of-expiry refresh +
 * single-flight lock). Token 获取失败计数与告警由 {@link OAuthTokenCache} 统一管理
 * （连续 3 次失败记录 ERROR + 递增 {@code pms_oauth_failure_total} 指标）。All
 * sync endpoints and the token endpoint are configurable via
 * {@link D365Properties} (bound from {@code d365.*}).</p>
 *
 * <p><b>Resilience4j 弹性保护</b>：所有对外部 D365 的写 / 同步调用均通过
 * {@code @CircuitBreaker} + {@code @Bulkhead} + {@code @Retry} 三层保护：
 * <ul>
 *   <li>{@link CircuitBreaker}（{@code d365CircuitBreaker}）：失败率 ≥50% 触发熔断，
 *       30s 后自动转半开，半开态允许 5 次试探。</li>
 *   <li>{@link Bulkhead}（{@code d365Bulkhead}）：信号量隔离，最大并发 10。</li>
 *   <li>{@link Retry}（{@code d365Retry}）：对 IOException / TimeoutException 重试，
 *       最多 3 次，指数退避。</li>
 * </ul>
 * 熔断 OPEN 时由 fallback 方法抛出 {@link IntegrationException}，
 * 由 {@code GlobalExceptionHandler} 统一返回 HTTP 503。
 * {@link #healthCheck()} 不加注解，避免熔断时健康端点无法探测恢复。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class D365IntegrationServiceImpl implements D365IntegrationService {

    private final D365Properties d365Properties;
    private final RestTemplate integrationRestTemplate;
    private final IIntegrationLogService integrationLogService;
    private final ObjectMapper objectMapper;
    private final ApplicationContext applicationContext;
    private final OAuthTokenCache oauthTokenCache;

    private final D365PurchaseReceiptMapper d365PurchaseReceiptMapper;
    private final D365InvoiceMapper d365InvoiceMapper;

    @Override
    public String getAccessToken() {
        return oauthTokenCache.getToken("d365", this::fetchD365Token);
    }

    /**
     * 调用 D365 OAuth2 token 端点获取新 token，封装为 {@link OAuthTokenCache.TokenInfo}
     * 供分布式缓存使用。
     *
     * @return token 信息
     * @throws IntegrationException 当 token 端点调用失败或响应为空时
     */
    private OAuthTokenCache.TokenInfo fetchD365Token() {
        TokenResponse response = requestToken();
        if (response == null || response.getAccessToken() == null) {
            throw new IntegrationException("d365", "D365 token response is empty");
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
    @CircuitBreaker(name = "d365CircuitBreaker", fallbackMethod = "pushPurchaseReceiptFallback")
    @Bulkhead(name = "d365Bulkhead")
    @Retry(name = "d365Retry")
    public String pushPurchaseReceipt(PurchaseReceiptHeader header) {
        String url = d365Properties.getBaseUrl() + "/purchase-receipts";
        String body = writeJson(header);
        IntegrationLog logRecord = IntegrationLog.builder()
                .logType(IntegrationConstants.LOG_TYPE_D365)
                .businessType(IntegrationConstants.BIZ_PURCHASE_RECEIPT)
                .businessId(header.getPurchaseOrderId())
                .requestUrl(url)
                .requestBody(body)
                .build();
        logRecord = integrationLogService.log(logRecord);
        return executePost(logRecord, url, body);
    }

    @Override
    @CircuitBreaker(name = "d365CircuitBreaker", fallbackMethod = "pushPurchaseOrderFallback")
    @Bulkhead(name = "d365Bulkhead")
    @Retry(name = "d365Retry")
    public String pushPurchaseOrder(PurchaseHeader header) {
        String url = d365Properties.getBaseUrl() + "/purchase-orders";
        String body = writeJson(header);
        IntegrationLog logRecord = IntegrationLog.builder()
                .logType(IntegrationConstants.LOG_TYPE_D365)
                .businessType(IntegrationConstants.BIZ_PURCHASE_ORDER)
                .businessId(header.getPurchaseOrderId())
                .requestUrl(url)
                .requestBody(body)
                .build();
        logRecord = integrationLogService.log(logRecord);
        return executePost(logRecord, url, body);
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
            // The log has already been marked failed inside executePost.
            log.warn("D365 retry failed for log {}: {}", logId, e.getMessage());
        }
        return integrationLogService.getById(logId);
    }

    @Override
    @CircuitBreaker(name = "d365CircuitBreaker", fallbackMethod = "syncPurchaseOrdersFallback")
    @Bulkhead(name = "d365Bulkhead")
    @Retry(name = "d365Retry")
    public int syncPurchaseOrders() {
        String url = d365Properties.getBaseUrl() + "/purchase-orders";
        String response = executeGet(url, IntegrationConstants.BIZ_PURCHASE_ORDER, "SYNC_PO");
        int count = 0;
        JsonNode value = extractValueArray(response);
        for (JsonNode node : value) {
            String poNo = textOrNull(node, "purchaseOrderId");
            if (poNo == null) {
                continue;
            }
            // Upsert a pending receipt record seeded from the PO so the
            // receipt-sync step can later backfill it.
            D365PurchaseReceipt existing = findReceiptByPoNo(poNo);
            D365PurchaseReceipt.D365PurchaseReceiptBuilder builder = D365PurchaseReceipt.builder()
                    .poNo(poNo)
                    .pushStatus(IntegrationConstants.PUSH_STATUS_PENDING);
            if (node.has("vendorAccountNumber")) {
                builder.sn(textOrNull(node, "vendorAccountNumber"));
            }
            if (existing == null) {
                d365PurchaseReceiptMapper.insert(builder.build());
            } else {
                D365PurchaseReceipt update = builder.build();
                update.setId(existing.getId());
                d365PurchaseReceiptMapper.updateById(update);
            }
            count++;
        }
        log.info("D365 syncPurchaseOrders synced {} record(s)", count);
        return count;
    }

    @Override
    @CircuitBreaker(name = "d365CircuitBreaker", fallbackMethod = "syncPurchaseReceiptsFallback")
    @Bulkhead(name = "d365Bulkhead")
    @Retry(name = "d365Retry")
    public int syncPurchaseReceipts() {
        String url = d365Properties.getBaseUrl() + "/purchase-receipts";
        String response = executeGet(url, IntegrationConstants.BIZ_PURCHASE_RECEIPT, "SYNC_RECEIPT");
        int count = 0;
        JsonNode value = extractValueArray(response);
        LocalDateTime now = LocalDateTime.now();
        for (JsonNode node : value) {
            String receiptNo = textOrNull(node, "receiptNo");
            String poNo = textOrNull(node, "poNo");
            if (receiptNo == null && poNo == null) {
                continue;
            }
            D365PurchaseReceipt record = D365PurchaseReceipt.builder()
                    .receiptNo(receiptNo)
                    .poNo(poNo)
                    .sn(textOrNull(node, "sn"))
                    .d365ReceiptId(textOrNull(node, "d365ReceiptId"))
                    .pushStatus(IntegrationConstants.PUSH_STATUS_PUSHED)
                    .pushedAt(now)
                    .build();
            D365PurchaseReceipt existing = receiptNo != null
                    ? findReceiptByReceiptNo(receiptNo)
                    : findReceiptByPoNo(poNo);
            if (existing == null) {
                d365PurchaseReceiptMapper.insert(record);
            } else {
                record.setId(existing.getId());
                d365PurchaseReceiptMapper.updateById(record);
            }
            count++;
        }
        log.info("D365 syncPurchaseReceipts synced {} record(s)", count);
        return count;
    }

    @Override
    @CircuitBreaker(name = "d365CircuitBreaker", fallbackMethod = "syncAssetSerialNumbersFallback")
    @Bulkhead(name = "d365Bulkhead")
    @Retry(name = "d365Retry")
    public int syncAssetSerialNumbers() {
        String url = d365Properties.getBaseUrl() + "/asset-serial-numbers";
        String response = executeGet(url, IntegrationConstants.BIZ_PURCHASE_ORDER, "SYNC_SN");
        int count = 0;
        JsonNode value = extractValueArray(response);
        BaseMapper<?> assetMapper = lookupMapper("assetMapper");
        if (assetMapper == null) {
            log.warn("AssetMapper bean not available; skipping D365 SN sync");
            return 0;
        }
        for (JsonNode node : value) {
            Long assetId = longOrNull(node, "assetId");
            String sn = textOrNull(node, "sn");
            if (assetId == null || sn == null) {
                continue;
            }
            UpdateWrapper wrapper = new UpdateWrapper();
            wrapper.eq("id", assetId);
            wrapper.set("serial_no", sn);
            int rows = assetMapper.update(null, wrapper);
            if (rows > 0) {
                count++;
            }
        }
        log.info("D365 syncAssetSerialNumbers synced {} record(s)", count);
        return count;
    }

    @Override
    @CircuitBreaker(name = "d365CircuitBreaker", fallbackMethod = "syncInvoicesFallback")
    @Bulkhead(name = "d365Bulkhead")
    @Retry(name = "d365Retry")
    public int syncInvoices() {
        String url = d365Properties.getBaseUrl() + "/invoices";
        String response = executeGet(url, IntegrationConstants.BIZ_INVOICE, "SYNC_INVOICE");
        int count = 0;
        JsonNode value = extractValueArray(response);
        LocalDateTime now = LocalDateTime.now();
        for (JsonNode node : value) {
            String invoiceNo = textOrNull(node, "invoiceNo");
            String settlementNo = textOrNull(node, "settlementNo");
            if (invoiceNo == null) {
                continue;
            }
            D365Invoice record = D365Invoice.builder()
                    .invoiceNo(invoiceNo)
                    .settlementNo(settlementNo)
                    .amount(decimalOrNull(node, "amount"))
                    .taxAmount(decimalOrNull(node, "taxAmount"))
                    .totalAmount(decimalOrNull(node, "totalAmount"))
                    .vendorName(textOrNull(node, "vendorName"))
                    .d365InvoiceId(textOrNull(node, "d365InvoiceId"))
                    .pushStatus(IntegrationConstants.PUSH_STATUS_PUSHED)
                    .pushedAt(now)
                    .build();
            if (node.has("invoiceDate")) {
                record.setInvoiceDate(LocalDateTime.now());
            }
            D365Invoice existing = findInvoiceByNo(invoiceNo);
            if (existing == null) {
                d365InvoiceMapper.insert(record);
            } else {
                record.setId(existing.getId());
                d365InvoiceMapper.updateById(record);
            }
            // Reflectively update the local settlement's invoice_no column.
            if (settlementNo != null) {
                updateSettlementInvoiceNo(settlementNo, invoiceNo);
            }
            count++;
        }
        log.info("D365 syncInvoices synced {} record(s)", count);
        return count;
    }

    @Override
    public D365HealthDto healthCheck() {
        boolean tokenValid = false;
        boolean connected = false;
        try {
            String token = getAccessToken();
            tokenValid = true;
            // A trivial authenticated GET against the base URL confirms
            // reachability. Intentionally NOT logged to avoid polluting the
            // integration log with health-check entries.
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            integrationRestTemplate.exchange(d365Properties.getBaseUrl(),
                    HttpMethod.GET, new HttpEntity<>(headers), String.class);
            connected = true;
        } catch (Exception e) {
            log.debug("D365 health check failed: {}", e.getMessage());
        }
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        long pushCount = countLogs(IntegrationConstants.STATUS_SUCCESS, since);
        long failCount = countLogs(IntegrationConstants.STATUS_FAILED, since);
        List<IntegrationLog> recent = recentLogs(10);
        return D365HealthDto.builder()
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
     * pushPurchaseReceipt 熔断 / 失败降级：记录日志并抛出 IntegrationException。
     *
     * @param header 原方法入参
     * @param t      触发降级的异常（CallNotPermittedException 或 IntegrationException）
     */
    private String pushPurchaseReceiptFallback(PurchaseReceiptHeader header, Throwable t) {
        log.error("D365 pushPurchaseReceipt 熔断降级 poId={} err={}",
                header == null ? null : header.getPurchaseOrderId(), t.getMessage());
        throw new IntegrationException("d365", "D365 采购收货推送服务暂不可用，请稍后重试", t);
    }

    /**
     * pushPurchaseOrder 熔断 / 失败降级。
     */
    private String pushPurchaseOrderFallback(PurchaseHeader header, Throwable t) {
        log.error("D365 pushPurchaseOrder 熔断降级 poId={} err={}",
                header == null ? null : header.getPurchaseOrderId(), t.getMessage());
        throw new IntegrationException("d365", "D365 采购单推送服务暂不可用，请稍后重试", t);
    }

    /**
     * syncPurchaseOrders 熔断 / 失败降级。
     */
    private int syncPurchaseOrdersFallback(Throwable t) {
        log.error("D365 syncPurchaseOrders 熔断降级 err={}", t.getMessage());
        throw new IntegrationException("d365", "D365 采购单同步服务暂不可用，请稍后重试", t);
    }

    /**
     * syncPurchaseReceipts 熔断 / 失败降级。
     */
    private int syncPurchaseReceiptsFallback(Throwable t) {
        log.error("D365 syncPurchaseReceipts 熔断降级 err={}", t.getMessage());
        throw new IntegrationException("d365", "D365 采购收货同步服务暂不可用，请稍后重试", t);
    }

    /**
     * syncAssetSerialNumbers 熔断 / 失败降级。
     */
    private int syncAssetSerialNumbersFallback(Throwable t) {
        log.error("D365 syncAssetSerialNumbers 熔断降级 err={}", t.getMessage());
        throw new IntegrationException("d365", "D365 资产序列号同步服务暂不可用，请稍后重试", t);
    }

    /**
     * syncInvoices 熔断 / 失败降级。
     */
    private int syncInvoicesFallback(Throwable t) {
        log.error("D365 syncInvoices 熔断降级 err={}", t.getMessage());
        throw new IntegrationException("d365", "D365 发票同步服务暂不可用，请稍后重试", t);
    }

    /**
     * Perform the actual POST against D365, then mark the log success/failed.
     *
     * @throws IntegrationException when the HTTP call fails (the log is already
     *                              marked failed before rethrowing).
     */
    private String executePost(IntegrationLog logRecord, String url, String body) {
        try {
            String token = getAccessToken();
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = integrationRestTemplate.postForEntity(url, entity, String.class);
            String responseBody = response.getBody();
            integrationLogService.markSuccess(logRecord.getId(), responseBody);
            return responseBody;
        } catch (IntegrationException e) {
            // 已经是 IntegrationException（来自 token 获取失败），直接透传
            integrationLogService.markFailed(logRecord.getId(), e.getMessage());
            throw e;
        } catch (Exception e) {
            integrationLogService.markFailed(logRecord.getId(), e.getMessage());
            throw new IntegrationException("d365", "D365 integration call failed: " + e.getMessage(), e);
        }
    }

    /**
     * Authenticated GET against D365, logged to IntegrationLog. Returns the
     * raw response body.
     */
    private String executeGet(String url, String businessType, String businessId) {
        String body = "{}";
        IntegrationLog logRecord = IntegrationLog.builder()
                .logType(IntegrationConstants.LOG_TYPE_D365)
                .businessType(businessType)
                .businessId(businessId)
                .requestUrl(url)
                .requestBody(body)
                .build();
        logRecord = integrationLogService.log(logRecord);
        try {
            String token = getAccessToken();
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = integrationRestTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);
            String responseBody = response.getBody();
            integrationLogService.markSuccess(logRecord.getId(), responseBody);
            return responseBody;
        } catch (IntegrationException e) {
            // 已经是 IntegrationException（来自 token 获取失败），直接透传
            integrationLogService.markFailed(logRecord.getId(), e.getMessage());
            throw e;
        } catch (Exception e) {
            integrationLogService.markFailed(logRecord.getId(), e.getMessage());
            throw new IntegrationException("d365", "D365 GET failed: " + e.getMessage(), e);
        }
    }

    private TokenResponse requestToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", d365Properties.getGrantType());
        form.add("client_id", d365Properties.getClientId());
        form.add("client_secret", d365Properties.getClientSecret());
        if (d365Properties.getScope() != null && !d365Properties.getScope().isBlank()) {
            form.add("scope", d365Properties.getScope());
        }
        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(form, headers);
        try {
            return integrationRestTemplate.postForObject(
                    d365Properties.getTokenUrl(), entity, TokenResponse.class);
        } catch (RestClientException e) {
            // 失败计数与告警由 RedisOAuthTokenCache 统一管理，此处仅抛出异常
            throw new IntegrationException("d365", "D365 token request failed: " + e.getMessage(), e);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException("Failed to serialize D365 request body: " + e.getMessage());
        }
    }

    // ---- JSON helpers ----

    private JsonNode extractValueArray(String json) {
        try {
            JsonNode root = objectMapper.readTree(json == null ? "{}" : json);
            JsonNode value = root.path("value");
            if (value.isArray()) {
                return value;
            }
            if (root.isArray()) {
                return root;
            }
            return objectMapper.createArrayNode();
        } catch (Exception e) {
            throw new IntegrationException("d365", "Failed to parse D365 response: " + e.getMessage(), e);
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        return child != null && !child.isNull() ? child.asText() : null;
    }

    private static Long longOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull()) {
            return null;
        }
        try {
            return child.asLong();
        } catch (Exception e) {
            return null;
        }
    }

    private static java.math.BigDecimal decimalOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull() || !child.isNumber()) {
            return null;
        }
        return child.decimalValue();
    }

    // ---- Local persistence helpers ----

    private D365PurchaseReceipt findReceiptByPoNo(String poNo) {
        LambdaQueryWrapper<D365PurchaseReceipt> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(D365PurchaseReceipt::getPoNo, poNo).last("LIMIT 1");
        return d365PurchaseReceiptMapper.selectOne(wrapper);
    }

    private D365PurchaseReceipt findReceiptByReceiptNo(String receiptNo) {
        LambdaQueryWrapper<D365PurchaseReceipt> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(D365PurchaseReceipt::getReceiptNo, receiptNo).last("LIMIT 1");
        return d365PurchaseReceiptMapper.selectOne(wrapper);
    }

    private D365Invoice findInvoiceByNo(String invoiceNo) {
        LambdaQueryWrapper<D365Invoice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(D365Invoice::getInvoiceNo, invoiceNo).last("LIMIT 1");
        return d365InvoiceMapper.selectOne(wrapper);
    }

    /**
     * Look up a MyBatis-Plus mapper bean by name without a compile-time
     * dependency on its module (avoids the integration → asset/implementation
     * → workflow → integration cycle).
     */
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

    /**
     * Reflectively update {@code pms_settlement.invoice_no} for the given
     * settlement number via the SettlementMapper bean (looked up at runtime
     * to avoid a module cycle).
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void updateSettlementInvoiceNo(String settlementNo, String invoiceNo) {
        BaseMapper<?> settlementMapper = lookupMapper("settlementMapper");
        if (settlementMapper == null) {
            log.warn("SettlementMapper bean not available; cannot update invoice_no for {}", settlementNo);
            return;
        }
        UpdateWrapper wrapper = new UpdateWrapper();
        wrapper.eq("settlement_no", settlementNo);
        wrapper.set("invoice_no", invoiceNo);
        settlementMapper.update(null, wrapper);
    }

    // ---- Health query helpers ----

    private long countLogs(String status, LocalDateTime since) {
        LambdaQueryWrapper<IntegrationLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(IntegrationLog::getLogType, IntegrationConstants.LOG_TYPE_D365)
                .eq(IntegrationLog::getResponseStatus, status)
                .ge(IntegrationLog::getCreateTime, since);
        return integrationLogService.count(wrapper);
    }

    private List<IntegrationLog> recentLogs(int limit) {
        LambdaQueryWrapper<IntegrationLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(IntegrationLog::getLogType, IntegrationConstants.LOG_TYPE_D365)
                .orderByDesc(IntegrationLog::getCreateTime)
                .last("LIMIT " + Math.max(limit, 1));
        List<IntegrationLog> logs = integrationLogService.list(wrapper);
        return logs == null ? new ArrayList<>() : logs;
    }
}
