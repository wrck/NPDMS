package cn.iocoder.yudao.module.pms.project.service.projectauthorization;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.platform.api.authorization.AuthorizationGrantApi;
import cn.iocoder.yudao.module.pms.platform.api.authorization.dto.AuthorizationGrantCreateCommand;
import cn.iocoder.yudao.module.pms.platform.api.authorization.dto.AuthorizationGrantDTO;
import cn.iocoder.yudao.module.pms.platform.api.authorization.dto.AuthorizationGrantPageQuery;
import cn.iocoder.yudao.module.pms.platform.api.authorization.dto.AuthorizationGrantPageResult;
import cn.iocoder.yudao.module.pms.project.service.projectauthorization.ProjectAuthorizationGuard.Actor;
import cn.iocoder.yudao.module.pms.project.service.projectauthorization.ProjectAuthorizationGuard.ManagementBounds;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_AUTHORIZATION_NOT_FOUND;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_AUTHORIZATION_VERSION_CONFLICT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectAuthorizationApplicationServiceTest {

    @Mock AuthorizationGrantApi authorizationGrantApi;
    @Mock ProjectAuthorizationGuard guard;
    @Mock AdminUserApi adminUserApi;
    private ProjectAuthorizationApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ProjectAuthorizationApplicationService(authorizationGrantApi, guard, adminUserApi);
    }

    @Test
    void createUsesFixedPlatformContractAfterGuard() {
        AuthorizationGrantDTO created = grant(20L, "PROJECT_AND_DESCENDANTS", "ACTIVE", 0);
        when(authorizationGrantApi.create(any())).thenReturn(created);

        AuthorizationGrantDTO result = service.create(createCommand(), actor());

        assertEquals(created, result);
        verify(adminUserApi).validateUser(9L);
        verify(guard).assertCanCreate(actor(), 20L, "PROJECT_VIEW", "PROJECT_AND_DESCENDANTS");
        ArgumentCaptor<AuthorizationGrantCreateCommand> captor =
                ArgumentCaptor.forClass(AuthorizationGrantCreateCommand.class);
        verify(authorizationGrantApi).create(captor.capture());
        assertEquals("USER", captor.getValue().subjectTypeCode());
        assertEquals("PROJ", captor.getValue().resourceContextCode());
        assertEquals("PROJECT", captor.getValue().resourceTypeCode());
        assertTrue(captor.getValue().effectiveFrom() != null);
    }

    @Test
    void createGuardFailureDoesNotProbeTargetUserOrWritePlatformFact() {
        doThrow(new ServiceException(PROJECT_AUTHORIZATION_NOT_FOUND)).when(guard)
                .assertCanCreate(actor(), 20L, "PROJECT_VIEW", "PROJECT_AND_DESCENDANTS");

        assertThrows(ServiceException.class, () -> service.create(createCommand(), actor()));

        verify(adminUserApi, never()).validateUser(any());
        verify(authorizationGrantApi, never()).create(any());
    }

    @Test
    void pageWithoutDescendantBoundsForcesCurrentScope() {
        when(guard.assertCanQuery(actor(), 20L, false))
                .thenReturn(new ManagementBounds(false));
        when(authorizationGrantApi.page(any())).thenReturn(new AuthorizationGrantPageResult(List.of(), 0));

        service.page(new ProjectAuthorizationApplicationService.PageQuery(
                20L, null, null, null, null, null, 1, 20), actor());

        ArgumentCaptor<AuthorizationGrantPageQuery> captor =
                ArgumentCaptor.forClass(AuthorizationGrantPageQuery.class);
        verify(authorizationGrantApi).page(captor.capture());
        assertEquals("CURRENT_PROJECT", captor.getValue().scopeCode());
        assertEquals(20L, captor.getValue().resourceId());
    }

    @Test
    void descendantOnlyPageIsEmptyWhenCallerCannotManageWholeSubtree() {
        when(guard.assertCanQuery(actor(), 20L, false))
                .thenReturn(new ManagementBounds(false));

        AuthorizationGrantPageResult result = service.page(
                new ProjectAuthorizationApplicationService.PageQuery(
                        20L, null, null, "PROJECT_AND_DESCENDANTS", null, null, 1, 20), actor());

        assertEquals(0, result.total());
        verify(authorizationGrantApi, never()).page(any());
    }

    @Test
    void hiddenDetailRejectsGrantOutsideWholeSubtreeBounds() {
        when(authorizationGrantApi.get(0L, 30L))
                .thenReturn(grant(20L, "PROJECT_AND_DESCENDANTS", "ACTIVE", 0));
        when(guard.assertCanQuery(actor(), 20L, true))
                .thenReturn(new ManagementBounds(false));

        ServiceException failure = assertThrows(ServiceException.class,
                () -> service.get(30L, actor()));

        assertEquals(PROJECT_AUTHORIZATION_NOT_FOUND.getCode(), failure.getCode());
    }

    @Test
    void revokeMapsPlatformVersionConflict() {
        AuthorizationGrantDTO grant = grant(20L, "CURRENT_PROJECT", "ACTIVE", 2);
        when(authorizationGrantApi.get(0L, 30L)).thenReturn(grant);
        when(authorizationGrantApi.revoke(any()))
                .thenThrow(new IllegalStateException("AUTHORIZATION_GRANT_VERSION_CONFLICT"));

        ServiceException failure = assertThrows(ServiceException.class,
                () -> service.revoke(new ProjectAuthorizationApplicationService.RevokeCommand(
                        30L, 2, "范围调整", "revoke-key", "b".repeat(64)), actor()));

        assertEquals(PROJECT_AUTHORIZATION_VERSION_CONFLICT.getCode(), failure.getCode());
        verify(guard).assertCanRevoke(actor(), grant);
    }

    private ProjectAuthorizationApplicationService.CreateCommand createCommand() {
        return new ProjectAuthorizationApplicationService.CreateCommand(
                20L, 9L, "PROJECT_VIEW", "PROJECT_AND_DESCENDANTS", null,
                LocalDateTime.now().plusDays(1), "联合交付", "create-key", "a".repeat(64));
    }

    private AuthorizationGrantDTO grant(Long resourceId, String scopeCode, String statusCode, int version) {
        return new AuthorizationGrantDTO(30L, 0L, "USER", 9L, "PROJ", "PROJECT", resourceId,
                "PROJECT_VIEW", scopeCode, LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusDays(1), statusCode, "PROJ", "Project",
                String.valueOf(resourceId), 7L, LocalDateTime.now(), null, null, null, version);
    }

    private Actor actor() {
        return new Actor(0L, 7L);
    }
}
