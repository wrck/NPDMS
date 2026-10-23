package cn.iocoder.yudao.module.pms.service.framework;

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

/** Register physical PMS routes using the unchanged platform identity and tenant filters. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class InspectionWebConfiguration {
    private static boolean matches(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith("/api/v1/pms/inspection-")
                || path.equals("/api/v1/pms/service-issues") || path.startsWith("/api/v1/pms/service-issues/");
    }

    @Bean
    FilterRegistrationBean<OncePerRequestFilter> inspectionIdentityFilter() {
        var filter = new OncePerRequestFilter() {
            @Override protected boolean shouldNotFilter(HttpServletRequest request) { return !matches(request); }
            @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                                      FilterChain chain) throws ServletException, IOException {
                WebFrameworkUtils.setLoginUserType(request, UserTypeEnum.ADMIN.getValue());
                chain.doFilter(request, response);
            }
        };
        var registration = new FilterRegistrationBean<OncePerRequestFilter>(filter);
        registration.setName("inspectionIdentityFilter");
        registration.addUrlPatterns("/api/v1/pms/*");
        registration.setOrder(WebFilterOrderEnum.TENANT_CONTEXT_FILTER + 1);
        return registration;
    }

    @Bean
    @ConditionalOnProperty(prefix = "yudao.tenant", name = "enable", matchIfMissing = true)
    FilterRegistrationBean<TenantSecurityWebFilter> inspectionTenantFilter(TenantProperties tenantProperties,
            GlobalExceptionHandler exceptionHandler, TenantFrameworkService tenants) {
        var filter = new TenantSecurityWebFilter(properties(), tenantProperties, Set.of(), exceptionHandler, tenants) {
            @Override protected boolean shouldNotFilter(HttpServletRequest request) { return !matches(request); }
        };
        var registration = new FilterRegistrationBean<TenantSecurityWebFilter>(filter);
        registration.setName("inspectionTenantFilter");
        registration.addUrlPatterns("/api/v1/pms/*");
        registration.setOrder(WebFilterOrderEnum.TENANT_SECURITY_FILTER);
        return registration;
    }

    @Bean
    @ConditionalOnProperty(prefix = "yudao.access-log", name = "enable", matchIfMissing = true)
    FilterRegistrationBean<ApiAccessLogFilter> inspectionAccessLogFilter(
            @Value("${spring.application.name}") String applicationName, ApiAccessLogCommonApi accessLogs) {
        var filter = new ApiAccessLogFilter(properties(), applicationName, accessLogs) {
            @Override protected boolean shouldNotFilter(HttpServletRequest request) { return !matches(request); }
        };
        var registration = new FilterRegistrationBean<ApiAccessLogFilter>(filter);
        registration.setName("inspectionAccessLogFilter");
        registration.addUrlPatterns("/api/v1/pms/*");
        registration.setOrder(WebFilterOrderEnum.API_ACCESS_LOG_FILTER);
        return registration;
    }

    @Bean
    GroupedOpenApi inspectionBusinessOpenApi() {
        return GroupedOpenApi.builder().group("inspection-business")
                .packagesToScan("cn.iocoder.yudao.module.pms.service.controller.business")
                .pathsToMatch("/api/v1/pms/inspection-**", "/api/v1/pms/service-issues/**").build();
    }

    private static WebProperties properties() {
        var properties = new WebProperties();
        properties.setAdminApi(new WebProperties.Api("/api/v1/pms/",
                "cn.iocoder.yudao.module.pms.service.controller.business.**"));
        return properties;
    }
}
