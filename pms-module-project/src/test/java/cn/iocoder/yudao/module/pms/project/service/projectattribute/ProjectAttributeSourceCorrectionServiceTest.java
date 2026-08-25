package cn.iocoder.yudao.module.pms.project.service.projectattribute;

import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateRevisionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.domain.projectattribute.TemplateMatchDecision;
import cn.iocoder.yudao.module.pms.project.domain.projectattribute.TemplateMatchDecisionRules;
import cn.iocoder.yudao.module.pms.project.service.projectattribute.command.ImpactMatchHistoryCommand;
import cn.iocoder.yudao.module.pms.project.service.projectattribute.command.ProjectAttributeSourceCorrectionCommand;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectTemplateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectAttributeSourceCorrectionServiceTest {

    @Mock private TrustedProjectServicePrincipalRegistry principalRegistry;
    @Mock private PlatformCommandExecutionApi commandExecutionApi;
    @Mock private ProjectMasterMapper projectMapper;
    @Mock private ProjectAttributeResolutionService resolutionService;
    @Mock private ProjectTemplateMatchHistoryService historyService;
    @Mock private ProjectTemplateService templateService;
    @InjectMocks private ProjectAttributeSourceCorrectionService service;

    @Test
    @SuppressWarnings("unchecked")
    void trustedSourceKeepsProjectCategoryAndWritesSourceEvidence() {
        when(principalRegistry.resolve("int-crm-sync")).thenReturn(91L);
        when(projectMapper.selectByIdForUpdate(100L)).thenReturn(project());
        when(resolutionService.evaluateImpact(any())).thenReturn(decision());
        when(templateService.getRevisionList(9L)).thenReturn(List.of(revision()));
        when(projectMapper.updateBusinessAttributesIfMatch(any())).thenReturn(1);
        when(commandExecutionApi.execute(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            Supplier<Object> operation = invocation.getArgument(3);
            Object result = operation.get();
            return new PlatformCommandExecutionApi.ExecutionResult<>(
                    PlatformCommandExecutionApi.Decision.NEW, result);
        });

        var result = service.correct(command(), 1L, "op-source-1");

        assertEquals(5, result.version());
        ArgumentCaptor<ImpactMatchHistoryCommand> history =
                ArgumentCaptor.forClass(ImpactMatchHistoryCommand.class);
        verify(historyService).appendImpact(history.capture());
        assertEquals("GENERAL", history.getValue().attributes().projectCategory());
        assertEquals("A", history.getValue().attributes().majorProjectLevel());
        assertEquals(91L, history.getValue().operatorId());
        assertEquals("CRM", history.getValue().source().sourceOwner());
        assertEquals(TemplateMatchDecisionRules.TRIGGER_SOURCE, history.getValue().triggerType());
        assertEquals(TemplateMatchDecisionRules.operationId(
                100L, TemplateMatchDecisionRules.TRIGGER_SOURCE, "source-key-1"), result.operationId());
        assertEquals("op-source-1", history.getValue().traceId());
    }

    @Test
    void unregisteredServiceIdentityStopsBeforeProjectWrite() {
        when(principalRegistry.resolve("int-crm-sync"))
                .thenThrow(new IllegalArgumentException("服务身份未注册或未受信任"));

        assertThrows(IllegalArgumentException.class,
                () -> service.correct(command(), 1L, "op-source-1"));

        verifyNoInteractions(projectMapper, historyService);
    }

    private ProjectAttributeSourceCorrectionCommand command() {
        return new ProjectAttributeSourceCorrectionCommand(100L, 4, "CRM_SIGN", "CRM_MODE", "A",
                "CRM", "CRM", "CRM-PROJ-1", "event-2", "v2",
                LocalDateTime.of(2026, 8, 25, 12, 0), "b".repeat(64), "map-v2",
                " 来源修正 ", "source-key-1", "c".repeat(64), "int-crm-sync");
    }

    private ProjectMasterDO project() {
        ProjectMasterDO project = new ProjectMasterDO();
        project.setId(100L);
        project.setTenantId(1L);
        project.setVersion(4);
        project.setSigningMethod("DIRECT");
        project.setProjectCategory("GENERAL");
        project.setImplementationMode("DIRECT_SERVICE");
        project.setLifecycleTemplateId(9L);
        project.setLifecycleTemplateRevisionNo(1);
        return project;
    }

    private TemplateMatchDecision decision() {
        return new TemplateMatchDecision(TemplateMatchDecisionRules.MATCH_NO_MATCH, "digest",
                TemplateMatchDecisionRules.MATCHER_VERSION, null, null, null, null);
    }

    private ProjectTemplateRevisionDO revision() {
        ProjectTemplateRevisionDO revision = new ProjectTemplateRevisionDO();
        revision.setId(11L);
        revision.setRevisionNo(1);
        return revision;
    }
}
