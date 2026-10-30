package cn.iocoder.yudao.module.pms.cutover.framework.config;

import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiAccessLogCommonApi;
import cn.iocoder.yudao.framework.common.biz.infra.logger.dto.ApiAccessLogCreateReqDTO;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCheckRespDTO;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.config.SecurityProperties;
import cn.iocoder.yudao.framework.security.core.filter.TokenAuthenticationFilter;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.yudao.framework.web.config.WebProperties;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.framework.web.core.util.WebFrameworkUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CutoverWebConfigurationTest {
    private final CutoverWebConfiguration configuration = new CutoverWebConfiguration();
    private final OAuth2TokenCommonApi tokens = mock(OAuth2TokenCommonApi.class);
    private final GlobalExceptionHandler errors = mock(GlobalExceptionHandler.class);
    private final TenantFrameworkService tenants = mock(TenantFrameworkService.class);

    @BeforeEach void setup() {
        JsonUtils.init(JsonMapper.builder().findAndAddModules().build());
        new WebFrameworkUtils(new WebProperties());
        when(errors.allExceptionHandler(any(), any())).thenAnswer(call -> CommonResult.error(403, "denied"));
    }
    @AfterEach void clear() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }

    @Test void canonicalPathRequiresAdminTokenIdentityBeforeTenantValidation() throws Exception {
        var request = request("/api/v1/pms/cutover-tasks/statistics");
        request.addHeader("Authorization", "Bearer controlled-admin-token");
        when(tokens.checkAccessToken("controlled-admin-token")).thenReturn(new OAuth2AccessTokenCheckRespDTO()
                .setUserId(8L).setUserType(UserTypeEnum.ADMIN.getValue()).setTenantId(1L));
        var reached = new java.util.concurrent.atomic.AtomicBoolean();
        var response = new MockHttpServletResponse();
        configuration.cutoverApiIdentityFilter().getFilter().doFilter(request, response, (req, res) ->
                new TokenAuthenticationFilter(new SecurityProperties(), errors, tokens).doFilter(req, res, (authenticated, authenticatedResponse) ->
                        configuration.cutoverApiTenantFilter(new TenantProperties(), errors, tenants).getFilter()
                                .doFilter(authenticated, authenticatedResponse, (checked, checkedResponse) -> {
                                    assertThat(SecurityFrameworkUtils.getLoginUserId()).isEqualTo(8L);
                                    assertThat(TenantContextHolder.getRequiredTenantId()).isEqualTo(1L);
                                    reached.set(true);
                                })));
        assertThat(reached).isTrue();
        verify(tenants).validTenant(1L);
    }

    @Test void memberTokenCannotUseTheBusinessApiNamespace() throws Exception {
        var request = request("/api/v1/pms/cutover-configurations/revisions");
        request.addHeader("Authorization", "Bearer controlled-member-token");
        when(tokens.checkAccessToken("controlled-member-token")).thenReturn(new OAuth2AccessTokenCheckRespDTO()
                .setUserId(8L).setUserType(UserTypeEnum.MEMBER.getValue()).setTenantId(1L));
        var response = new MockHttpServletResponse();
        var reached = new java.util.concurrent.atomic.AtomicBoolean();
        configuration.cutoverApiIdentityFilter().getFilter().doFilter(request, response, (req, res) ->
                new TokenAuthenticationFilter(new SecurityProperties(), errors, tokens).doFilter(req, res, (a, b) -> reached.set(true)));
        assertThat(reached).isFalse();
        assertThat(SecurityFrameworkUtils.getLoginUser()).isNull();
        assertThat(response.getContentAsString()).contains("403");
    }

    @Test void canonicalTenantFilterRejectsMissingAndMismatchedTenant() throws Exception {
        var filter = configuration.cutoverApiTenantFilter(new TenantProperties(), errors, tenants).getFilter();
        var reached = new java.util.concurrent.atomic.AtomicBoolean();
        var request = request("/api/v1/pms/cutover-tasks");
        var missing = new MockHttpServletResponse();
        filter.doFilter(request, missing, (a, b) -> reached.set(true));
        assertThat(reached).isFalse();
        assertThat(missing.getContentAsString()).contains("400");
        SecurityFrameworkUtils.setLoginUser(new cn.iocoder.yudao.framework.security.core.LoginUser()
                .setId(8L).setUserType(UserTypeEnum.ADMIN.getValue()).setTenantId(1L), request);
        TenantContextHolder.setTenantId(2L);
        var wrong = new MockHttpServletResponse();
        filter.doFilter(request, wrong, (a, b) -> reached.set(true));
        assertThat(reached).isFalse();
        assertThat(wrong.getContentAsString()).contains("403");
        verifyNoInteractions(tenants);
    }

    @Test void canonicalAccessLogsKeepAdminIdentityAndSanitizeCredentialsWithoutTouchingOtherPmsApis() throws Exception {
        var access = mock(ApiAccessLogCommonApi.class);
        var request = request("/api/v1/pms/cutover-tasks");
        request.addParameter("token", "synthetic-sensitive-value");
        request.addParameter("projectId", "901");
        configuration.cutoverApiIdentityFilter().getFilter().doFilter(request, new MockHttpServletResponse(), (req, res) ->
                configuration.cutoverApiAccessLogFilter("cutover-test", access).getFilter().doFilter(req, res, (a, b) -> { }));
        var logged = ArgumentCaptor.forClass(ApiAccessLogCreateReqDTO.class);
        verify(access).createApiAccessLogAsync(logged.capture());
        assertThat(logged.getValue().getUserType()).isEqualTo(UserTypeEnum.ADMIN.getValue());
        assertThat(logged.getValue().getRequestUrl()).isEqualTo("/api/v1/pms/cutover-tasks");
        assertThat(logged.getValue().getRequestParams()).contains("901").doesNotContain("synthetic-sensitive-value");
        clearInvocations(access);
        var unrelated = request("/api/v1/pms/project-tasks");
        configuration.cutoverApiIdentityFilter().getFilter().doFilter(unrelated, new MockHttpServletResponse(), (req, res) ->
                configuration.cutoverApiAccessLogFilter("cutover-test", access).getFilter().doFilter(req, res, (a, b) -> { }));
        assertThat(WebFrameworkUtils.getLoginUserType(unrelated)).isNull();
        verifyNoInteractions(access);
    }

    private static MockHttpServletRequest request(String path) {
        var request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        return request;
    }
}
