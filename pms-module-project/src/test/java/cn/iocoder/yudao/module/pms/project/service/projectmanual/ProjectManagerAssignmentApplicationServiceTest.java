package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.command.AssignServiceManagerCommand;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.command.AssignServiceManagerResult;
import cn.iocoder.yudao.module.pms.project.service.projectauthorization.ProjectAuthorizationGuard;
import cn.iocoder.yudao.module.pms.asset.api.location.AssetLocationApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectSiteDO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.type.TypeReference;

import java.time.LocalDateTime;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PMS_IDEMPOTENCY_KEY_CONFLICT;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_ORGANIZATION_SCOPE_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ProjectManagerAssignmentApplicationServiceTest {

    @Mock
    private PlatformCommandExecutionApi platformFactService;
    @Mock
    private ProjectManualCreationService projectService;
    @Mock
    private ProjectMasterMapper projectMapper;
    @Mock
    private ProjectCreationAuthorizationService authorizationService;
    @Mock private ProjectServiceManagerCandidateValidator managerCandidateValidator;
    @Mock private AssetLocationApi assetLocationApi;
    @Mock private ProjectSiteApplicationService projectSiteService;
    @Mock private ProjectAuthorizationGuard projectAuthorizationGuard;

    @InjectMocks
    private ProjectManagerAssignmentApplicationService service;

    @BeforeEach
    void setUpScope() {
        ProjectMasterDO project = new ProjectMasterDO();
        project.setId(1L); project.setTenantId(1L); project.setCompanyId(10L);
        lenient().when(projectMapper.selectById(1L)).thenReturn(project);
        ProjectSiteDO site = new ProjectSiteDO(); site.setSiteId(30L);
        lenient().when(projectSiteService.getActiveSites(1L)).thenReturn(List.of(site));
    }

    @Test
    @SuppressWarnings("unchecked")
    void authorizedAssignmentRunsInsidePlatformFactBoundary() {
        LocalDateTime effectiveFrom = LocalDateTime.of(2026, 8, 25, 8, 30);
        AssignServiceManagerResult assigned = new AssignServiceManagerResult(
                1L, 8L, 3, "UNASSIGNED", effectiveFrom, 65L, 66L);
        AtomicReference<PlatformCommandExecutionApi.SuccessFacts> capturedFacts = new AtomicReference<>();
        when(projectService.assignServiceManager(any())).thenReturn(assigned);
        when(platformFactService.execute(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            Supplier<Object> operation = invocation.getArgument(3);
            Function<Object, ?> facts = invocation.getArgument(4);
            Object result = operation.get();
            capturedFacts.set((PlatformCommandExecutionApi.SuccessFacts) facts.apply(result));
            return new PlatformCommandExecutionApi.ExecutionResult<>(
                    PlatformCommandExecutionApi.Decision.NEW, result);
        });

        AssignServiceManagerResult result = service.assign(command(), actor());

        assertEquals(8L, result.assignmentId());
        assertEquals(3, result.version());
        verify(authorizationService).assertCanAssign(7L);
        verify(projectAuthorizationGuard).assertCanAssign(new ProjectAuthorizationGuard.Actor(1L, 7L), 1L);
        verify(projectService).assignServiceManager(any());
        Map<String, Object> eventPayload = JsonUtils.parseObject(capturedFacts.get().eventPayload(),
                new TypeReference<>() {});
        assertEquals(Set.of("assignmentId", "projectId", "recipientUserId", "templateCode",
                "templateParamsSnapshot", "assignmentType", "levelCode", "effectiveFrom"),
                eventPayload.keySet());
        assertEquals("PRIMARY", eventPayload.get("assignmentType"));
        assertFalse(eventPayload.containsKey("version"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void unresolvedProjectAllowsAuthorizedDepartmentAssignmentWithoutSite() {
        when(projectSiteService.getActiveSites(1L)).thenReturn(List.of());
        AssignServiceManagerResult assigned = new AssignServiceManagerResult(
                1L, 9L, 3, "UNASSIGNED", LocalDateTime.now(), null, 66L);
        when(projectService.assignServiceManager(any())).thenReturn(assigned);
        when(platformFactService.execute(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            Supplier<Object> operation = invocation.getArgument(3);
            Object result = operation.get();
            return new PlatformCommandExecutionApi.ExecutionResult<>(
                    PlatformCommandExecutionApi.Decision.NEW, result);
        });
        AssignServiceManagerCommand command = new AssignServiceManagerCommand(1L, 2,
                "L1", 66L, null, "PRIMARY", 20L, "DEP-01", "人工指派",
                "fallback-assign-key", "c".repeat(64));

        AssignServiceManagerResult result = service.assign(command, actor());

        assertEquals(9L, result.assignmentId());
        verify(assetLocationApi, never()).getSite(any(), any());
        verify(projectService).assignServiceManager(command);
    }

    @Test
    void levelTwoAssignmentRequiresSiteWithinProjectScope() {
        AssignServiceManagerCommand command = new AssignServiceManagerCommand(1L, 2,
                "L2", 66L, null, "PRIMARY", 20L, "DEP-01", "人工指派",
                "missing-site-key", "d".repeat(64));

        assertThrows(IllegalArgumentException.class, () -> service.assign(command, actor()));

        verifyNoInteractions(platformFactService);
    }

    @Test
    void permissionFailureStopsBeforeIdempotencyClaim() {
        doThrow(new ServiceException(FORBIDDEN))
                .when(authorizationService).assertCanAssign(7L);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.assign(command(), actor()));

        assertEquals(FORBIDDEN.getCode(), exception.getCode());
        verifyNoInteractions(platformFactService);
        verify(projectService, never()).assignServiceManager(any());
    }

    @Test
    void projectScopeFailureStopsBeforeBusinessValidationAndIdempotencyClaim() {
        doThrow(new ServiceException(FORBIDDEN)).when(projectAuthorizationGuard)
                .assertCanAssign(new ProjectAuthorizationGuard.Actor(1L, 7L), 1L);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.assign(command(), actor()));

        assertEquals(FORBIDDEN.getCode(), exception.getCode());
        verifyNoInteractions(platformFactService);
        verifyNoInteractions(managerCandidateValidator);
        verify(projectService, never()).assignServiceManager(any());
    }

    @Test
    void organizationScopeFailureStopsBeforeIdempotencyClaimAndAssignment() {
        doThrow(new ServiceException(PROJECT_ORGANIZATION_SCOPE_INVALID))
                .when(managerCandidateValidator).validate(66L, 10L, 20L, "DEP-01");

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.assign(command(), actor()));

        assertEquals(PROJECT_ORGANIZATION_SCOPE_INVALID.getCode(), exception.getCode());
        verifyNoInteractions(platformFactService);
        verify(projectService, never()).assignServiceManager(any());
    }

    @Test
    void idempotencyConflictIsMappedToStableError() {
        when(platformFactService.execute(any(), any(), any(), any(), any())).thenReturn(
                new PlatformCommandExecutionApi.ExecutionResult<>(
                        PlatformCommandExecutionApi.Decision.CONFLICT, null));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.assign(command(), actor()));

        assertEquals(PMS_IDEMPOTENCY_KEY_CONFLICT.getCode(), exception.getCode());
        verify(projectService, never()).assignServiceManager(any());
    }

    private AssignServiceManagerCommand command() {
        return new AssignServiceManagerCommand(1L, 2, "L1", 66L, 30L,
                "PRIMARY", 20L, "DEP-01", "人工指派", "assign-key", "b".repeat(64));
    }

    private ProjectManagerAssignmentApplicationService.Actor actor() {
        return new ProjectManagerAssignmentApplicationService.Actor(1L, 7L, "correlation-1");
    }
}
