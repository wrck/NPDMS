package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.ProjectInstantiation;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.command.ManualProjectCreateCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Function;
import java.util.function.Supplier;

import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PMS_IDEMPOTENCY_KEY_CONFLICT;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PMS_IDEMPOTENCY_IN_PROGRESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectManualCreationApplicationServiceTest {

    @Mock
    private ProjectCreationPlatformFactService platformFactService;
    @Mock
    private ProjectManualCreationService projectCreationService;

    @InjectMocks
    private ProjectManualCreationApplicationService service;

    @Test
    @SuppressWarnings("unchecked")
    void applicationEntryBuildsResultInsidePlatformExecution() {
        ProjectMasterDO project = project();
        when(projectCreationService.createProject(any(), any(), any(), any(), any(), any())).thenReturn(project);
        when(projectCreationService.getInstances(100L)).thenReturn(new ProjectInstantiation());
        when(platformFactService.execute(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            Supplier<Object> operation = invocation.getArgument(3);
            Function<Object, ?> facts = invocation.getArgument(4);
            Object result = operation.get();
            facts.apply(result);
            return new ProjectCreationPlatformFactService.ExecutionResult<>(
                    ProjectCreationPlatformFactService.Decision.NEW, result);
        });

        var result = service.create(command(), actor());

        assertEquals(100L, result.id());
        assertEquals("ACTIVE", result.lifecycleStatus());
        assertEquals("S0", result.currentStage());
        assertEquals("UNASSIGNED", result.assignmentStatus());
        ArgumentCaptor<ProjectMasterDO> draftCaptor = ArgumentCaptor.forClass(ProjectMasterDO.class);
        verify(projectCreationService).createProject(draftCaptor.capture(), any(), any(), any(), any(), any());
        assertEquals(1L, draftCaptor.getValue().getTenantId());
    }

    @Test
    void conflictDecisionIsMappedToStableError() {
        when(platformFactService.execute(any(), any(), any(), any(), any())).thenReturn(
                new ProjectCreationPlatformFactService.ExecutionResult<>(
                        ProjectCreationPlatformFactService.Decision.CONFLICT, null));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.create(command(), actor()));

        assertEquals(PMS_IDEMPOTENCY_KEY_CONFLICT.getCode(), exception.getCode());
    }

    @Test
    void rootCreationRequiresCandidateWatermark() {
        ManualProjectCreateCommand base = command();
        ManualProjectCreateCommand invalid = new ManualProjectCreateCommand(
                base.draft(), null, null, base.templateRevisionId(),
                null, null, base.idempotencyKey(), base.requestDigest());

        assertThrows(IllegalArgumentException.class, () -> service.create(invalid, actor()));

        verifyNoInteractions(platformFactService);
    }

    @Test
    void childCreationMayInheritTemplateWithoutCandidateWatermark() {
        ManualProjectCreateCommand base = command();
        base.draft().setParentId(100L);
        ManualProjectCreateCommand child = new ManualProjectCreateCommand(base.draft(), null, null, null,
                null, null, base.idempotencyKey(), base.requestDigest());
        when(platformFactService.execute(any(), any(), any(), any(), any())).thenReturn(
                new ProjectCreationPlatformFactService.ExecutionResult<>(
                        ProjectCreationPlatformFactService.Decision.IN_PROGRESS, null));

        ServiceException exception = assertThrows(ServiceException.class, () -> service.create(child, actor()));

        assertEquals(PMS_IDEMPOTENCY_IN_PROGRESS.getCode(), exception.getCode());
    }

    private ManualProjectCreateCommand command() {
        ProjectMasterDO draft = new ProjectMasterDO();
        draft.setCreationReason("业务立项");
        return new ManualProjectCreateCommand(draft, null, null, 9002L, "candidate-watermark-v1", null,
                "key-1", "a".repeat(64));
    }

    private ProjectManualCreationApplicationService.Actor actor() {
        return new ProjectManualCreationApplicationService.Actor(1L, 7L, "correlation-1");
    }

    private ProjectMasterDO project() {
        ProjectMasterDO project = new ProjectMasterDO();
        project.setId(100L);
        project.setProjectCode("PJT2026000100");
        project.setStatus("S0");
        project.setLifecycleStatus("ACTIVE");
        project.setCurrentStage("S0");
        project.setAssignmentStatus("UNASSIGNED");
        project.setVersion(0);
        project.setLifecycleTemplateId(9L);
        project.setLifecycleTemplateRevisionNo(2);
        return project;
    }
}
