package cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.sitesurvey.entity.vo.SiteSurveyEntityPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.sitesurvey.entity.SiteSurveyEntityDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.SiteSurveyEntityMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.query.SiteSurveyEntityPageQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectAllScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SiteSurveyEntityPageScopeTest {
    private final ProjectScopeApi scopes = mock(ProjectScopeApi.class);
    private final SiteSurveyEntityMapper mapper = mock(SiteSurveyEntityMapper.class);
    private final SiteSurveyEntityDomainCommands service = new SiteSurveyEntityDomainCommands();

    @BeforeEach void setUp() {
        TenantContextHolder.setTenantId(7L);
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(8L).setTenantId(7L), new MockHttpServletRequest());
        ReflectionTestUtils.setField(service, "projectScopes", scopes);
        ReflectionTestUtils.setField(service, "siteSurveyEntityMapper", mapper);
    }

    @AfterEach void cleanUp() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test void specifiedProjectDoesNotEnumerateOtherTreesAndPreservesQueryAndRows() {
        when(scopes.resolveCurrent(current())).thenReturn(new ProjectScopeResult(10L, 2L, Set.of(10L, 11L), Set.of()));
        var row = new SiteSurveyEntityDO(); row.setId(22L); row.setProjectId(11L);
        var expected = new PageResult<>(List.of(row), 1L);
        when(mapper.selectPage(any(SiteSurveyEntityPageQuery.class))).thenReturn(expected);
        var request = request(11L); request.setName("survey"); request.setStatus(0); request.setPageNo(2);
        assertSame(expected, service.getSiteSurveyEntityPage(request));
        verify(scopes).resolveCurrent(current());
        verify(scopes, never()).resolveAllCurrent(any());
        verify(mapper).selectPage(argThat((SiteSurveyEntityPageQuery q) -> q.getTenantId().equals(7L)
                && q.getVisibleProjectIds().equals(Set.of(11L)) && q.getProjectId().equals(11L)
                && q.getName().equals("survey") && q.getStatus() == 0 && q.getPageNo() == 2));
    }

    @Test void placeholderOrRevokedProjectNeverExpandsToAnotherProject() {
        when(scopes.resolveCurrent(current())).thenReturn(new ProjectScopeResult(10L, 2L, Set.of(10L), Set.of(11L)));
        service.getSiteSurveyEntityPage(request(11L));
        verify(mapper).selectPage(argThat((SiteSurveyEntityPageQuery q) -> q.getVisibleProjectIds().isEmpty()));
        verify(scopes, never()).resolveAllCurrent(any());
    }

    @Test void everyReadRevalidatesScopeSoRevocationTakesEffect() {
        when(scopes.resolveCurrent(current()))
                .thenReturn(new ProjectScopeResult(10L, 2L, Set.of(11L), Set.of()))
                .thenReturn(new ProjectScopeResult(10L, 2L, Set.of(), Set.of()));
        service.getSiteSurveyEntityPage(request(11L));
        service.getSiteSurveyEntityPage(request(11L));
        verify(scopes, times(2)).resolveCurrent(current());
        verify(mapper).selectPage(argThat((SiteSurveyEntityPageQuery q) -> q.getVisibleProjectIds().equals(Set.of(11L))));
        verify(mapper).selectPage(argThat((SiteSurveyEntityPageQuery q) -> q.getVisibleProjectIds().isEmpty()));
    }

    @Test void standaloneListKeepsAllCurrentProjectScope() {
        when(scopes.resolveAllCurrent(all())).thenReturn(Set.of(10L, 11L));
        service.getSiteSurveyEntityPage(request(null));
        verify(scopes, never()).resolveCurrent(any());
        verify(mapper).selectPage(argThat((SiteSurveyEntityPageQuery q) -> q.getVisibleProjectIds().equals(Set.of(10L, 11L))));
    }

    @Test void unavailableScopeDoesNotReadSurveyOrFallBackToBroadEnumeration() {
        when(scopes.resolveCurrent(current())).thenThrow(new IllegalStateException("owner unavailable"));
        assertThrows(IllegalStateException.class, () -> service.getSiteSurveyEntityPage(request(11L)));
        verifyNoInteractions(mapper);
        verify(scopes, never()).resolveAllCurrent(any());
    }

    @Test void unavailableTreeKeepsOriginalAuthorizationForHistoricalProjectsAndMissingAnchors() {
        when(scopes.resolveCurrent(current())).thenThrow(new ServiceException(1, "no current tree"));
        when(scopes.resolveAllCurrent(all())).thenReturn(Set.of(11L));
        service.getSiteSurveyEntityPage(request(11L));
        verify(mapper).selectPage(argThat((SiteSurveyEntityPageQuery q) -> q.getVisibleProjectIds().equals(Set.of(11L))));
    }

    @Test void missingAnchorOrForeignTenantNeverGainsPermissionFromFallback() {
        when(scopes.resolveCurrent(current())).thenThrow(new ServiceException(1, "anchor unavailable"));
        when(scopes.resolveAllCurrent(all())).thenReturn(Set.of(10L));
        service.getSiteSurveyEntityPage(request(11L));
        verify(mapper).selectPage(argThat((SiteSurveyEntityPageQuery q) -> q.getVisibleProjectIds().isEmpty()));
    }

    @Test void missingScopeResultFailsClosedWithoutBroadFallback() {
        service.getSiteSurveyEntityPage(request(11L));
        verify(mapper).selectPage(argThat((SiteSurveyEntityPageQuery q) -> q.getVisibleProjectIds().isEmpty()));
        verify(scopes, never()).resolveAllCurrent(any());
    }

    private static ProjectCurrentScopeQuery current() { return new ProjectCurrentScopeQuery(7L, 8L, 11L, ProjectScopeApi.ACTION_VIEW); }
    private static ProjectAllScopeQuery all() { return new ProjectAllScopeQuery(7L, 8L, ProjectScopeApi.ACTION_VIEW); }
    private static SiteSurveyEntityPageReqVO request(Long projectId) {
        var request = new SiteSurveyEntityPageReqVO(); request.setProjectId(projectId); return request;
    }
}
