package cn.iocoder.yudao.module.pms.acceptance.service.acceptance.application;

import cn.iocoder.yudao.module.pms.acceptance.api.deliverable.ProjectDeliverableInitializationApplicationService;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi.TemplateFrozenDefinition;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi.TemplateFrozenView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** ACC交付件初始化与改版（P06R）：写路径全部委托平台统一要求 API，ACC 只做编排与校验。 */
@ExtendWith(MockitoExtension.class)
class ProjectDeliverableInitializationApplicationServiceImplTest {

    @Mock
    private PlatformDeliveryRequirementApi platform;

    @InjectMocks
    private ProjectDeliverableInitializationApplicationServiceImpl service;

    @Test
    void implementationRequiresExistingTransaction() throws Exception {
        Method method = ProjectDeliverableInitializationApplicationServiceImpl.class.getMethod(
                "initialize", ProjectDeliverableInitializationApplicationService.InitializeProjectDeliverablesCommand.class);
        Transactional transactional = method.getAnnotation(Transactional.class);

        assertEquals(Propagation.MANDATORY, transactional.propagation());
    }

    @Test
    void initializeDelegatesDefinitionsToPlatformAndReportsCounts() {
        when(platform.instantiateTemplateFrozen(eq(100L), isNull(), any()))
                .thenReturn(List.of(501L, 502L));

        var result = service.initialize(commandWithTwoDeliverables());

        assertEquals(2, result.expectedCount());
        assertEquals(2, result.insertedCount());
        var captor = ArgumentCaptor.forClass(List.class);
        verify(platform).instantiateTemplateFrozen(eq(100L), isNull(), captor.capture());
        @SuppressWarnings("unchecked")
        List<TemplateFrozenDefinition> definitions = (List<TemplateFrozenDefinition>) captor.getValue();
        assertEquals(new TemplateFrozenDefinition("D-001", "项目计划", "S0", "T-001", true, 0, 11L, null),
                definitions.get(0));
        assertEquals(new TemplateFrozenDefinition("D-002", "启动纪要", "S0", null, true, 0, 12L, null),
                definitions.get(1));
    }

    @Test
    void incompleteCommandOrDefinitionIsRejectedBeforePlatformWrite() {
        assertThrows(IllegalArgumentException.class, () -> service.initialize(null));
        assertThrows(IllegalArgumentException.class, () -> service.initialize(
                new ProjectDeliverableInitializationApplicationService.InitializeProjectDeliverablesCommand(
                        100L, 200L, List.of(new ProjectDeliverableInitializationApplicationService.DeliverableDefinition(
                        "D-001", "项目计划", null, "T-001", true, 11L)))));
        verify(platform, never()).instantiateTemplateFrozen(any(), any(), any());
    }

    @Test
    void inspectionLocksPlatformRowsAndDerivesRetirementEligibilityFromHistory() {
        var clean = view(10L, "D-010");
        var history = view(11L, "D-011");
        when(platform.lockByProject(9L)).thenReturn(List.of(clean, history));
        when(platform.listMaterials(10L)).thenReturn(List.of());
        when(platform.listSubmissions(10L)).thenReturn(List.of());
        when(platform.listMaterials(11L)).thenReturn(List.of(material()));

        var state = service.inspectPlanDefinitions(9L);

        assertEquals(2, state.definitions().size());
        assertEquals(java.util.Set.of(10L), state.retirableIds());
        verify(platform, never()).retireUnhandled(any());
    }

    @Test
    void swapsCodesInTwoPhasesAndAppliesFinalDefinitionsWithOptimisticVersions() {
        service.applyPlanChanges(command(
                new ProjectDeliverableInitializationApplicationService.DeliverablePlanChange(20L, 3L, definition("D10")),
                new ProjectDeliverableInitializationApplicationService.DeliverablePlanChange(10L, 3L, definition("D20"))));

        var order = org.mockito.Mockito.inOrder(platform);
        order.verify(platform).stageTemplateFrozenCodes(List.of(10L, 20L));
        order.verify(platform).updateTemplateFrozen(eq(10L), eq(3),
                eq(new TemplateFrozenDefinition("D20", "现场工勘交付件", "PREP", "SURVEY", true, 0, null, null)));
        order.verify(platform).updateTemplateFrozen(eq(20L), eq(3),
                eq(new TemplateFrozenDefinition("D10", "现场工勘交付件", "PREP", "SURVEY", true, 0, null, null)));
        verify(platform, never()).instantiateTemplateFrozen(any(), any(), any());
        verify(platform, never()).retireUnhandled(any());
    }

    @Test
    void removesUnhandledInstanceBeforeAddingIndependentReplacementWithSameCode() {
        when(platform.retireUnhandled(List.of(10L))).thenReturn(1);
        when(platform.instantiateTemplateFrozen(eq(9L), isNull(), any())).thenReturn(List.of(30L));

        service.applyPlanChanges(command(
                new ProjectDeliverableInitializationApplicationService.DeliverablePlanChange(10L, 3L, null),
                new ProjectDeliverableInitializationApplicationService.DeliverablePlanChange(null, null, definition("D10"))));

        var order = org.mockito.Mockito.inOrder(platform);
        order.verify(platform).retireUnhandled(List.of(10L));
        order.verify(platform).instantiateTemplateFrozen(eq(9L), isNull(),
                eq(List.of(new TemplateFrozenDefinition("D10", "现场工勘交付件", "PREP", "SURVEY", true, 0, null, null))));
    }

    @Test
    void refusesDuplicateIdentityOrTargetCodeBeforeAnyWrite() {
        assertThrows(IllegalArgumentException.class, () -> service.applyPlanChanges(command(
                new ProjectDeliverableInitializationApplicationService.DeliverablePlanChange(10L, 3L, null),
                new ProjectDeliverableInitializationApplicationService.DeliverablePlanChange(10L, 3L, null))));
        assertThrows(IllegalArgumentException.class, () -> service.applyPlanChanges(command(
                new ProjectDeliverableInitializationApplicationService.DeliverablePlanChange(10L, 3L, definition("SAME")),
                new ProjectDeliverableInitializationApplicationService.DeliverablePlanChange(null, null, definition("SAME")))));
        verify(platform, never()).stageTemplateFrozenCodes(any());
    }

    @Test
    void refusedRetirementDoesNotContinueWithOtherChanges() {
        when(platform.retireUnhandled(List.of(10L))).thenReturn(0);

        var failure = assertThrows(IllegalStateException.class, () -> service.applyPlanChanges(command(
                new ProjectDeliverableInitializationApplicationService.DeliverablePlanChange(10L, 3L, null),
                new ProjectDeliverableInitializationApplicationService.DeliverablePlanChange(null, null, definition("D10")))));
        assertEquals("DELIVERABLE_PLAN_HANDLING_HISTORY_PROTECTED", failure.getMessage());
        verify(platform, never()).instantiateTemplateFrozen(any(), any(), any());
        verify(platform, never()).updateTemplateFrozen(any(), any(), any());
    }

    @Test
    void emptyChangeSetHasNoPersistenceSideEffects() {
        service.applyPlanChanges(command());
        verifyNoInteractions(platform);
    }

    private ProjectDeliverableInitializationApplicationService.ApplyDeliverablePlanChanges command(
            ProjectDeliverableInitializationApplicationService.DeliverablePlanChange... changes) {
        return new ProjectDeliverableInitializationApplicationService.ApplyDeliverablePlanChanges(9L, 7L, List.of(changes));
    }

    private ProjectDeliverableInitializationApplicationService.DeliverableDefinition definition(String code) {
        return new ProjectDeliverableInitializationApplicationService.DeliverableDefinition(
                code, "现场工勘交付件", "PREP", "SURVEY", true, null);
    }

    private static TemplateFrozenView view(Long id, String code) {
        return new TemplateFrozenView(id, 9L, code, "交付件", "S1", null, null, null, true, 1, null,
                "OPEN", "{}", 3);
    }

    private static PlatformDeliveryRequirementApi.TemplateFrozenMaterialView material() {
        return new PlatformDeliveryRequirementApi.TemplateFrozenMaterialView(9001L, 11L, "FILE", 50L,
                500L, 1, "a".repeat(64), "proof.pdf", null, null, null, "ACTIVE", "ARCHIVED", null, 0);
    }

    private ProjectDeliverableInitializationApplicationService.InitializeProjectDeliverablesCommand
    commandWithTwoDeliverables() {
        var first = new ProjectDeliverableInitializationApplicationService.DeliverableDefinition(
                "D-001", "项目计划", "S0", "T-001", true, 11L);
        var second = new ProjectDeliverableInitializationApplicationService.DeliverableDefinition(
                "D-002", "启动纪要", "S0", null, true, 12L);
        return new ProjectDeliverableInitializationApplicationService.InitializeProjectDeliverablesCommand(
                100L, 200L, List.of(first, second));
    }
}
