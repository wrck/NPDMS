package cn.iocoder.yudao.module.pms.cutover.framework.config;

import cn.iocoder.yudao.framework.apilog.core.filter.ApiAccessLogFilter;
import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiAccessLogCommonApi;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.enums.WebFilterOrderEnum;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.security.TenantSecurityWebFilter;
import cn.iocoder.yudao.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.yudao.framework.web.config.WebProperties;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.framework.web.core.util.WebFrameworkUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/** 割接 Business API 使用正式物理路径，复用平台认证、租户校验与日志实现。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CutoverWebConfiguration {
    static final String RESOURCE_PREFIX = "/api/v1/pms/cutover-";
    private static final String SERVLET_PATTERN = "/api/v1/pms/*";

    @Bean
    FilterRegistrationBean<OncePerRequestFilter> cutoverApiIdentityFilter() {
        var filter = new OncePerRequestFilter() {
            @Override
            protected boolean shouldNotFilter(HttpServletRequest request) {
                return !request.getRequestURI().substring(request.getContextPath().length())
                        .startsWith(RESOURCE_PREFIX);
            }

            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                            FilterChain chain) throws ServletException, IOException {
                WebFrameworkUtils.setLoginUserType(request, UserTypeEnum.ADMIN.getValue());
                chain.doFilter(request, response);
            }
        };
        FilterRegistrationBean<OncePerRequestFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setName("cutoverApiIdentityFilter");
        registration.addUrlPatterns(SERVLET_PATTERN);
        // 在 Spring Security 的 TokenAuthenticationFilter 之前提供可信用户类型。
        registration.setOrder(WebFilterOrderEnum.TENANT_CONTEXT_FILTER + 1);
        return registration;
    }

    @Bean
    @ConditionalOnProperty(prefix = "yudao.tenant", name = "enable", matchIfMissing = true)
    FilterRegistrationBean<TenantSecurityWebFilter> cutoverApiTenantFilter(
            TenantProperties tenantProperties, GlobalExceptionHandler exceptionHandler,
            TenantFrameworkService tenants) {
        var filter = new TenantSecurityWebFilter(cutoverWebProperties(), tenantProperties,
                Set.of(), exceptionHandler, tenants);
        var registration = new FilterRegistrationBean<>(filter);
        registration.setName("cutoverApiTenantFilter");
        registration.addUrlPatterns(SERVLET_PATTERN);
        registration.setOrder(WebFilterOrderEnum.TENANT_SECURITY_FILTER);
        return registration;
    }

    @Bean
    @ConditionalOnProperty(prefix = "yudao.access-log", name = "enable", matchIfMissing = true)
    FilterRegistrationBean<ApiAccessLogFilter> cutoverApiAccessLogFilter(
            @Value("${spring.application.name}") String applicationName, ApiAccessLogCommonApi accessLogs) {
        var filter = new ApiAccessLogFilter(cutoverWebProperties(), applicationName, accessLogs);
        var registration = new FilterRegistrationBean<>(filter);
        registration.setName("cutoverApiAccessLogFilter");
        registration.addUrlPatterns(SERVLET_PATTERN);
        registration.setOrder(WebFilterOrderEnum.API_ACCESS_LOG_FILTER);
        return registration;
    }

    @Bean
    GroupedOpenApi cutoverBusinessOpenApi() {
        return GroupedOpenApi.builder().group("cutover-business")
                .packagesToScan("cn.iocoder.yudao.module.pms.cutover.controller.business")
                .pathsToMatch(RESOURCE_PREFIX + "**").build();
    }

    private static WebProperties cutoverWebProperties() {
        var properties = new WebProperties();
        properties.setAdminApi(new WebProperties.Api(RESOURCE_PREFIX,
                "cn.iocoder.yudao.module.pms.cutover.controller.business.**"));
        return properties;
    }
}
