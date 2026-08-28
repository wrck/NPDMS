package cn.iocoder.yudao.module.pms.project.service.projectattribute;

import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateRevisionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.domain.projectattribute.TemplateMatchDecision;
import cn.iocoder.yudao.module.pms.project.domain.projectattribute.TemplateMatchDecisionRules;
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.ProjectRules;
import cn.iocoder.yudao.module.pms.project.service.projectattribute.command.ImpactMatchHistoryCommand;
import cn.iocoder.yudao.module.pms.project.service.projectattribute.command.ManualProjectAttributeAdjustmentCommand;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectTemplateService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TREE_SCOPE_FORBIDDEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectAttributeClassificationApplicationServiceTest {

    @Mock private PlatformCommandExecutionApi commandExecutionApi;
    @Mock private PermissionApi permissionApi;
    @Mock private ProjectManualCreationService projectService;
    @Mock private ProjectMasterMapper projectMapper;
    @Mock private ProjectAttributeResolutionService resolutionService;
    @Mock private ProjectTemplateMatchHistoryService historyService;
    @Mock private ProjectTemplateService templateService;
    @InjectMocks private ProjectAttributeClassificationApplicationService service;

    @Test
    @SuppressWarnings("unchecked")
    void adjustsOnlyManualDimensionsAndDoesNotCreateOutbox() {
        ProjectMasterDO current = project();
        TemplateMatchDecision decision = decision();
        when(permissionApi.hasAnyPermissions(7L,
                ProjectAttributeClassificationApplicationService.PERMISSION_CLASSIFY)).thenReturn(true);
        when(projectMapper.selectByIdForUpdate(100L)).thenReturn(current);
        when(resolutionService.evaluateImpact(any())).thenReturn(decision);
        when(templateService.getRevisionList(9L)).thenReturn(List.of(revision()));
        when(projectMapper.updateBusinessAttributesIfMatch(any())).thenReturn(1);
        when(commandExecutionApi.execute(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            Supplier<Object> operation = invocation.getArgument(3);
            Function<Object, PlatformCommandExecutionApi.SuccessFacts> factsFactory = invocation.getArgument(4);
            Object result = operation.get();
            PlatformCommandExecutionApi.SuccessFacts facts = factsFactory.apply(result);
            assertNull(facts.eventType());
            assertNull(facts.eventPayload());
            return new PlatformCommandExecutionApi.ExecutionResult<>(
                    PlatformCommandExecutionApi.Decision.NEW, result);
        });

        var result = service.adjust(command(), new ProjectAttributeClassificationApplicationService.Actor(
                1L, 7L, "op-1"));

        assertEquals(4, result.version());
        assertEquals(TemplateMatchDecisionRules.IMPACT_NONE, result.impactResult());
        assertEquals(TemplateMatchDecisionRules.operationId(
                100L, TemplateMatchDecisionRules.TRIGGER_MANUAL, "key-1"), result.operationId());
        ArgumentCaptor<ImpactMatchHistoryCommand> history =
                ArgumentCaptor.forClass(ImpactMatchHistoryCommand.class);
        verify(historyService).appendImpact(history.capture());
        assertEquals("IMPLEMENT", history.getValue().attributes().signingMethod());
        assertEquals("GENERAL", history.getValue().attributes().projectCategory());
        assertNull(history.getValue().attributes().majorProjectLevel());
        assertEquals(TemplateMatchDecisionRules.TRIGGER_MANUAL, history.getValue().triggerType());
        assertEquals("op-1", history.getValue().traceId());
        verify(projectService).getProjectForManage(100L,
                new ProjectManualCreationService.ProjectAccessActor(1L, 7L));
    }

    @Test
    void rejectsMissingClassifyPermissionBeforeWrite() {
        when(permissionApi.hasAnyPermissions(7L,
                ProjectAttributeClassificationApplicationService.PERMISSION_CLASSIFY)).thenReturn(false);

        var error = assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.adjust(command(),
                        new ProjectAttributeClassificationApplicationService.Actor(1L, 7L, "op-1")));

        assertEquals(FORBIDDEN.getCode(), error.getCode());
        verifyNoInteractions(projectMapper, historyService);
    }

    @Test
    void rejectsViewOnlyProjectScopeBeforeStartingCommand() {
        when(permissionApi.hasAnyPermissions(7L,
                ProjectAttributeClassificationApplicationService.PERMISSION_CLASSIFY)).thenReturn(true);
        when(projectService.getProjectForManage(100L,
                new ProjectManualCreationService.ProjectAccessActor(1L, 7L)))
                .thenThrow(exception(PROJECT_TREE_SCOPE_FORBIDDEN));

        var error = assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.adjust(command(),
                        new ProjectAttributeClassificationApplicationService.Actor(1L, 7L, "op-1")));

        assertEquals(PROJECT_TREE_SCOPE_FORBIDDEN.getCode(), error.getCode());
        verifyNoInteractions(commandExecutionApi, projectMapper, historyService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void versionConflictDoesNotUpdateAttributesOrAppendHistory() {
        ProjectMasterDO current = project();
        current.setVersion(4);
        when(permissionApi.hasAnyPermissions(7L,
                ProjectAttributeClassificationApplicationService.PERMISSION_CLASSIFY)).thenReturn(true);
        when(projectMapper.selectByIdForUpdate(100L)).thenReturn(current);
        when(commandExecutionApi.execute(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            Supplier<Object> operation = invocation.getArgument(3);
            return new PlatformCommandExecutionApi.ExecutionResult<>(
                    PlatformCommandExecutionApi.Decision.NEW, operation.get());
        });

        var error = assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.adjust(command(),
                        new ProjectAttributeClassificationApplicationService.Actor(1L, 7L, "op-1")));

        assertEquals(cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_VERSION_CONFLICT.getCode(),
                error.getCode());
        verify(projectMapper, never()).updateBusinessAttributesIfMatch(any());
        verifyNoInteractions(historyService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void nonManualProjectCannotOverrideCrmOwnerFields() {
        ProjectMasterDO current = project();
        current.setSourceType(ProjectRules.SOURCE_TYPE_ORDER);
        when(permissionApi.hasAnyPermissions(7L,
                ProjectAttributeClassificationApplicationService.PERMISSION_CLASSIFY)).thenReturn(true);
        when(projectMapper.selectByIdForUpdate(100L)).thenReturn(current);
        when(commandExecutionApi.execute(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            Supplier<Object> operation = invocation.getArgument(3);
            return new PlatformCommandExecutionApi.ExecutionResult<>(
                    PlatformCommandExecutionApi.Decision.NEW, operation.get());
        });

        var error = assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.adjust(command(),
                        new ProjectAttributeClassificationApplicationService.Actor(1L, 7L, "op-1")));

        assertEquals(FORBIDDEN.getCode(), error.getCode());
        verify(projectMapper, never()).updateBusinessAttributesIfMatch(any());
        verifyNoInteractions(historyService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void nonManualProjectCanClassifyCategoryWithoutChangingCrmOwners() {
        ProjectMasterDO current = project();
        current.setSourceType(ProjectRules.SOURCE_TYPE_ORDER);
        when(permissionApi.hasAnyPermissions(7L,
                ProjectAttributeClassificationApplicationService.PERMISSION_CLASSIFY)).thenReturn(true);
        when(projectMapper.selectByIdForUpdate(100L)).thenReturn(current);
        when(resolutionService.evaluateImpact(any())).thenReturn(decision());
        when(templateService.getRevisionList(9L)).thenReturn(List.of(revision()));
        when(projectMapper.updateBusinessAttributesIfMatch(any())).thenReturn(1);
        when(commandExecutionApi.execute(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            Supplier<Object> operation = invocation.getArgument(3);
            return new PlatformCommandExecutionApi.ExecutionResult<>(
                    PlatformCommandExecutionApi.Decision.NEW, operation.get());
        });
        var command = new ManualProjectAttributeAdjustmentCommand(100L, 3, "DIRECT", "ENGINEERING",
                "DIRECT_SERVICE", " 分类修正 ", "key-2", "b".repeat(64));

        service.adjust(command, new ProjectAttributeClassificationApplicationService.Actor(
                1L, 7L, "op-2"));

        ArgumentCaptor<ImpactMatchHistoryCommand> history =
                ArgumentCaptor.forClass(ImpactMatchHistoryCommand.class);
        verify(historyService).appendImpact(history.capture());
        assertEquals("CRM", history.getValue().attributeOwners().signingMethodOwner());
        assertEquals("PROJ", history.getValue().attributeOwners().projectCategoryOwner());
        assertEquals("CRM", history.getValue().attributeOwners().implementationModeOwner());
    }

    private ManualProjectAttributeAdjustmentCommand command() {
        return new ManualProjectAttributeAdjustmentCommand(100L, 3, "IMPLEMENT", "GENERAL",
                "DIRECT_SERVICE", " 调整依据 ", "key-1", "a".repeat(64));
    }

    private ProjectMasterDO project() {
        ProjectMasterDO project = new ProjectMasterDO();
        project.setId(100L);
        project.setTenantId(1L);
        project.setVersion(3);
        project.setSigningMethod("DIRECT");
        project.setProjectCategory("GENERAL");
        project.setImplementationMode("DIRECT_SERVICE");
        project.setLifecycleTemplateId(9L);
        project.setLifecycleTemplateRevisionNo(1);
        project.setSourceType(ProjectRules.SOURCE_TYPE_MANUAL);
        return project;
    }

    private TemplateMatchDecision decision() {
        return new TemplateMatchDecision(TemplateMatchDecisionRules.MATCH_UNIQUE, "digest",
                TemplateMatchDecisionRules.MATCHER_VERSION, null, 9L, 11L, 1);
    }

    private ProjectTemplateRevisionDO revision() {
        ProjectTemplateRevisionDO revision = new ProjectTemplateRevisionDO();
        revision.setId(11L);
        revision.setRevisionNo(1);
        return revision;
    }
}
