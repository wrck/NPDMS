package cn.iocoder.yudao.module.pms.integration.extsystem.config;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Integration module configuration. Registers the {@link RestTemplate} used by
 * the D365/FP/OA adapters and enables the {@code @ConfigurationProperties} beans.
 *
 * <p><b>Resilience4j 事件监听</b>：通过 {@link ApplicationRunner} 在启动后向
 * {@link CircuitBreakerRegistry} / {@link BulkheadRegistry} /
 * {@link RateLimiterRegistry} / {@link RetryRegistry} 中所有已注册实例
 * 挂载事件监听器，将弹性组件事件转为业务日志。
 * Micrometer 指标（resilience4j.circuitbreaker.calls / state、
 * resilience4j.bulkhead.* / resilience4j.ratelimiter.* / resilience4j.retry.calls）
 * 由 resilience4j-spring-boot3 starter 的自动装配直接绑定到 MeterRegistry，
 * 此处仅补充业务级日志（INFO/WARN），便于运维通过日志而非仅靠指标排查问题。</p>
 *
 * <p><b>状态转换告警</b>：CircuitBreaker 的 onStateTransition 事件以 WARN 级别
 * 记录（CLOSED→OPEN / OPEN→HALF_OPEN / HALF_OPEN→CLOSED 等），便于告警系统
 * 基于日志关键词触发告警。</p>
 */
@Slf4j
@Configuration
@EnableConfigurationProperties({D365Properties.class, FpProperties.class,
        IntegrationProperties.class, OaProperties.class})
public class IntegrationConfig {

    /**
     * Shared {@link RestTemplate} with sensible connect/read timeouts for
     * external system calls.
     */
    @Bean
    public RestTemplate integrationRestTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(30))
                .build();
    }

    // ---- CircuitBreaker 事件监听 ----

    /**
     * 启动后为所有 CircuitBreaker 实例（d365CircuitBreaker / fpCircuitBreaker / oaCircuitBreaker）
     * 注册事件监听器：成功 / 失败 / 熔断拒绝 / 忽略错误 / 状态转换。
     * 同时监听 Registry 的 EntryAdded 事件，为后续动态创建的 CircuitBreaker 也注册监听器。
     *
     * @param registry CircuitBreaker 注册表（由 Spring Boot 自动装配）
     */
    @Bean
    public ApplicationRunner circuitBreakerEventLogger(CircuitBreakerRegistry registry) {
        return args -> {
            registry.getAllCircuitBreakers().forEach(this::registerCircuitBreakerListeners);
            registry.getEventPublisher().onEntryAdded(event ->
                    registerCircuitBreakerListeners(event.getAddedEntry()));
            log.info("CircuitBreaker 事件监听器已注册: instances={}",
                    registry.getAllCircuitBreakers().stream()
                            .map(CircuitBreaker::getName).toList());
        };
    }

    private void registerCircuitBreakerListeners(CircuitBreaker circuitBreaker) {
        String name = circuitBreaker.getName();
        circuitBreaker.getEventPublisher()
                .onSuccess(event -> log.debug("CircuitBreaker[{}] 调用成功 duration={}ms",
                        name, event.getElapsedDuration().toMillis()))
                .onError(event -> log.warn("CircuitBreaker[{}] 调用失败 duration={}ms err={}",
                        name, event.getElapsedDuration().toMillis(),
                        event.getThrowable() == null ? "null" : event.getThrowable().getMessage()))
                .onCallNotPermitted(event -> log.warn("CircuitBreaker[{}] 调用被熔断拒绝 (circuit OPEN)",
                        name))
                .onIgnoredError(event -> log.debug("CircuitBreaker[{}] 忽略错误 err={}",
                        name, event.getThrowable() == null ? "null" : event.getThrowable().getMessage()))
                .onStateTransition(event -> log.warn("CircuitBreaker[{}] 状态转换: {} -> {}",
                        name,
                        event.getStateTransition().getFromState(),
                        event.getStateTransition().getToState()))
                .onReset(event -> log.info("CircuitBreaker[{}] 已重置", name));
    }

    // ---- Retry 事件监听 ----

    /**
     * 为所有 Retry 实例（d365Retry / fpRetry / oaRetry）注册重试事件监听器，
     * 记录每次重试的等待时间与尝试次数。
     */
    @Bean
    public ApplicationRunner retryEventLogger(RetryRegistry registry) {
        return args -> {
            registry.getAllRetries().forEach(this::registerRetryListeners);
            registry.getEventPublisher().onEntryAdded(event ->
                    registerRetryListeners(event.getAddedEntry()));
            log.info("Retry 事件监听器已注册: instances={}",
                    registry.getAllRetries().stream().map(Retry::getName).toList());
        };
    }

    private void registerRetryListeners(Retry retry) {
        String name = retry.getName();
        retry.getEventPublisher()
                .onRetry(event -> log.info("Retry[{}] 第 {} 次重试, 等待 {}ms",
                        name, event.getNumberOfRetryAttempts(), event.getWaitInterval().toMillis()))
                .onSuccess(event -> log.debug("Retry[{}] 重试成功, 总尝试 {} 次",
                        name, event.getNumberOfRetryAttempts()))
                .onError(event -> log.warn("Retry[{}] 重试耗尽, 总尝试 {} 次, err={}",
                        name, event.getNumberOfRetryAttempts(),
                        event.getLastThrowable() == null ? "null" : event.getLastThrowable().getMessage()))
                .onIgnoredError(event -> log.debug("Retry[{}] 忽略错误 err={}",
                        name, event.getLastThrowable() == null ? "null" : event.getLastThrowable().getMessage()));
    }

    // ---- Bulkhead 事件监听 ----

    /**
     * 为所有 Bulkhead 实例（d365Bulkhead / fpBulkhead / oaBulkhead）注册隔离事件监听器，
     * 当并发调用被拒绝时记录 WARN 日志（信号量已满）。
     */
    @Bean
    public ApplicationRunner bulkheadEventLogger(BulkheadRegistry registry) {
        return args -> {
            registry.getAllBulkheads().forEach(this::registerBulkheadListeners);
            registry.getEventPublisher().onEntryAdded(event ->
                    registerBulkheadListeners(event.getAddedEntry()));
            log.info("Bulkhead 事件监听器已注册: instances={}",
                    registry.getAllBulkheads().stream().map(Bulkhead::getName).toList());
        };
    }

    private void registerBulkheadListeners(Bulkhead bulkhead) {
        String name = bulkhead.getName();
        bulkhead.getEventPublisher()
                .onCallPermitted(event -> log.debug("Bulkhead[{}] 调用获准", name))
                .onCallRejected(event -> log.warn("Bulkhead[{}] 调用被拒 (并发已达上限 {})",
                        name, bulkhead.getBulkheadConfig().getMaxConcurrentCalls()))
                .onCallFinished(event -> log.debug("Bulkhead[{}] 调用完成", name));
    }

    // ---- RateLimiter 事件监听 ----

    /**
     * 为所有 RateLimiter 实例（d365RateLimiter / fpRateLimiter / oaRateLimiter）
     * 注册限流事件监听器，当限流触发时记录 WARN 日志。
     */
    @Bean
    public ApplicationRunner rateLimiterEventLogger(RateLimiterRegistry registry) {
        return args -> {
            registry.getAllRateLimiters().forEach(this::registerRateLimiterListeners);
            registry.getEventPublisher().onEntryAdded(event ->
                    registerRateLimiterListeners(event.getAddedEntry()));
            log.info("RateLimiter 事件监听器已注册: instances={}",
                    registry.getAllRateLimiters().stream().map(RateLimiter::getName).toList());
        };
    }

    private void registerRateLimiterListeners(RateLimiter rateLimiter) {
        String name = rateLimiter.getName();
        rateLimiter.getEventPublisher()
                .onSuccess(event -> log.debug("RateLimiter[{}] 请求获准", name))
                .onFailure(event -> log.warn("RateLimiter[{}] 请求被限流, 事件类型={}",
                        name, event.getEventType()));
    }
}
