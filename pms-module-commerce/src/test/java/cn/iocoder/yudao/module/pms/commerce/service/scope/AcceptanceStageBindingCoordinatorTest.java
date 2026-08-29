package cn.iocoder.yudao.module.pms.commerce.service.scope;

import cn.iocoder.yudao.module.pms.project.api.acceptancescope.AcceptanceScopeBindingApi;
import cn.iocoder.yudao.module.pms.project.api.acceptancescope.dto.AcceptanceScopeBindingFact;
import cn.iocoder.yudao.module.pms.project.api.acceptancescope.dto.AcceptanceScopeBindingResult;
import cn.iocoder.yudao.module.pms.project.api.commerce.ProjectAcceptanceStageFactApi;
import cn.iocoder.yudao.module.pms.project.api.commerce.dto.ProjectAcceptanceStageFact;
import cn.iocoder.yudao.module.pms.project.api.commerce.dto.ProjectFactOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AcceptanceStageBindingCoordinatorTest {

    @Mock private ProjectAcceptanceStageFactApi stageFactApi;
    @Mock private AcceptanceScopeBindingApi bindingApi;
    private AcceptanceStageBindingCoordinator coordinator;

    @BeforeEach
    void setUp() {
        coordinator = new AcceptanceStageBindingCoordinator(stageFactApi, bindingApi);
    }

    @Test
    void shouldBindExactNewScopeWhenProjectIsInAcceptanceStage() {
        when(stageFactApi.lockAndRead(any())).thenReturn(new ProjectAcceptanceStageFact(
                ProjectFactOutcome.FOUND, 501L, 3, "S5", "S5", 701L));
        when(bindingApi.bindEffectiveScope(any())).thenReturn(new AcceptanceScopeBindingResult(false, 1,
                List.of(new AcceptanceScopeBindingFact(801L, 701L, 401L, 7L,
                        "SCOPE_VERSION_EFFECTIVE", 1))));

        AcceptanceStageBindingCoordinator.StageContext context = coordinator.lockAndRead(
                1L, 501L, 3, "op-1");
        coordinator.bindIfRequired(context, 401L, 7L, "op-1");

        assertTrue(context.acceptanceStage());
        verify(bindingApi).bindEffectiveScope(any());
    }

    @Test
    void shouldNotBindBeforeConfiguredAcceptanceStage() {
        when(stageFactApi.lockAndRead(any())).thenReturn(new ProjectAcceptanceStageFact(
                ProjectFactOutcome.FOUND, 501L, 3, "S4", "S5", null));

        AcceptanceStageBindingCoordinator.StageContext context = coordinator.lockAndRead(
                1L, 501L, 3, "op-1");
        coordinator.bindIfRequired(context, 401L, 7L, "op-1");

        assertFalse(context.acceptanceStage());
        verify(bindingApi, never()).bindEffectiveScope(any());
    }

    @Test
    void shouldRejectAcceptanceStageWithoutOwnerSnapshot() {
        when(stageFactApi.lockAndRead(any())).thenReturn(new ProjectAcceptanceStageFact(
                ProjectFactOutcome.FOUND, 501L, 3, "S5", "S5", null));

        assertThrows(IllegalStateException.class,
                () -> coordinator.lockAndRead(1L, 501L, 3, "op-1"));
        verify(bindingApi, never()).bindEffectiveScope(any());
    }

    @Test
    void shouldRejectMismatchedBindingResult() {
        var context = new AcceptanceStageBindingCoordinator.StageContext(1L, 501L, 3, 701L, true);
        when(bindingApi.bindEffectiveScope(any())).thenReturn(new AcceptanceScopeBindingResult(false, 1,
                List.of(new AcceptanceScopeBindingFact(801L, 701L, 999L, 7L,
                        "SCOPE_VERSION_EFFECTIVE", 1))));

        assertThrows(IllegalStateException.class,
                () -> coordinator.bindIfRequired(context, 401L, 7L, "op-1"));
    }
}
