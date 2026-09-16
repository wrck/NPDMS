package cn.iocoder.yudao.server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * PMS 集成域 Jackson 2 {@link ObjectMapper} 与 Resilience4j 注册表装配。
 * <p>
 * 原样引入的 pms-module-integration 基线（D365/FP/OA 适配器）通过构造器注入
 * Jackson 2 的 {@code com.fasterxml.jackson.databind.ObjectMapper}（仅使用
 * {@code writeValueAsString / readTree / createArrayNode} 默认 API）。
 * Yudao 基线（Spring Boot + Jackson 3）自动装配的是 {@code tools.jackson.databind.ObjectMapper}，
 * 两者类型不同，Jackson 2 侧无人注册。
 * <p>
 * Resilience4j 侧：Spring Boot 4 下 {@code resilience4j-spring-boot3} starter 自动装配不可用
 * （pms-module-integration/pom.xml 迁移说明），集成基线的 {@code IntegrationConfig}
 * 通过 ApplicationRunner 向四个注册表挂载事件监听器，装配层补注册默认配置的注册表，
 * 不修改集成模块基线源码。Jackson 3 与 Jackson 2、Resilience4j 与 Yudao 基线互不影响。
 */
@Configuration(proxyBeanMethods = false)
public class PmsIntegrationConfiguration {

    @Bean
    public ObjectMapper integrationObjectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public CircuitBreakerRegistry integrationCircuitBreakerRegistry() {
        return CircuitBreakerRegistry.ofDefaults();
    }

    @Bean
    public RetryRegistry integrationRetryRegistry() {
        return RetryRegistry.ofDefaults();
    }

    @Bean
    public BulkheadRegistry integrationBulkheadRegistry() {
        return BulkheadRegistry.ofDefaults();
    }

    @Bean
    public RateLimiterRegistry integrationRateLimiterRegistry() {
        return RateLimiterRegistry.ofDefaults();
    }
}

