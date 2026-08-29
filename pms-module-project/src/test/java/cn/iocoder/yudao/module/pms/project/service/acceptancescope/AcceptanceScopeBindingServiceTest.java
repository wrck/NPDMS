package cn.iocoder.yudao.module.pms.project.service.acceptancescope;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.scope.DeliveryScopeAcceptanceLockApi;
import cn.iocoder.yudao.module.pms.commerce.api.scope.dto.DeliveryScopeVersionFact;
import cn.iocoder.yudao.module.pms.project.api.acceptancescope.dto.AcceptanceScopeBindingResult;
import cn.iocoder.yudao.module.pms.project.api.acceptancescope.dto.AcceptanceScopeGuardOutcome;
import cn.iocoder.yudao.module.pms.project.api.acceptancescope.dto.AcceptanceScopeGuardQuery;
import cn.iocoder.yudao.module.pms.project.api.acceptancescope.dto.AcceptanceStageEntryBindingCommand;
import cn.iocoder.yudao.module.pms.project.api.acceptancescope.dto.EffectiveScopeBindingCommand;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptancescope.AcceptanceScopeBindingDO;
import cn.iocoder.yudao.module.pms.project.dal.repository.acceptancescope.AcceptanceScopeBindingRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AcceptanceScopeBindingServiceTest {

    private static final Long TENANT_ID = 1L;
    private static final Long PROJECT_ID = 101L;
    private static final Long SNAPSHOT_ID = 201L;

    @Mock
    private DeliveryScopeAcceptanceLockApi deliveryScopeLockApi;
    @Mock
    private AcceptanceScopeBindingRepository bindingRepository;

    private AcceptanceScopeBindingService service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
        service = new AcceptanceScopeBindingService(deliveryScopeLockApi, bindingRepository);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldBindAllLockedScopesForStageEntryWithoutAcceptanceReport() {
        when(deliveryScopeLockApi.lockCurrentByProject(any())).thenReturn(List.of(
                new DeliveryScopeVersionFact(301L, 4L),
                new DeliveryScopeVersionFact(302L, 7L)));
        when(bindingRepository.append(any())).thenAnswer(invocation -> {
            AcceptanceScopeBindingDO row = invocation.getArgument(0);
            row.setId(900L + row.getDeliveryScopeId());
            return 1;
        });

        AcceptanceScopeBindingResult result = service.bindForStageEntry(new AcceptanceStageEntryBindingCommand(
                TENANT_ID, PROJECT_ID, 9, SNAPSHOT_ID, "S4", "S5", "op-stage"));

        assertFalse(result.replayed());
        assertEquals(2, result.bindings().size());
        ArgumentCaptor<AcceptanceScopeBindingDO> rows = ArgumentCaptor.forClass(AcceptanceScopeBindingDO.class);
        verify(bindingRepository, org.mockito.Mockito.times(2)).append(rows.capture());
        assertEquals(List.of(301L, 302L), rows.getAllValues().stream()
                .map(AcceptanceScopeBindingDO::getDeliveryScopeId).toList());
        rows.getAllValues().forEach(row -> {
            assertEquals("PROJECT_STAGE_ENTRY", row.getBindingTrigger());
            assertEquals("LOCKED", row.getBindingStatus());
            assertNull(row.getEffectiveTo());
            assertEquals(1, row.getAcceptanceFactVersion());
        });
    }

    @Test
    void shouldReplaySameIdentityAndRejectDifferentTrigger() {
        AcceptanceScopeBindingDO existing = binding(301L, 4L, "SCOPE_VERSION_EFFECTIVE");
        when(bindingRepository.selectByIdentityForUpdate(any())).thenReturn(existing);

        AcceptanceScopeBindingResult replay = service.bindEffectiveScope(new EffectiveScopeBindingCommand(
                TENANT_ID, PROJECT_ID, SNAPSHOT_ID, 301L, 4L, "op-scope"));

        assertTrue(replay.replayed());
        assertEquals(existing.getId(), replay.bindings().getFirst().bindingId());
        verify(bindingRepository, never()).append(any());

        existing.setBindingTrigger("PROJECT_STAGE_ENTRY");
        assertThrows(RuntimeException.class, () -> service.bindEffectiveScope(new EffectiveScopeBindingCommand(
                TENANT_ID, PROJECT_ID, SNAPSHOT_ID, 301L, 4L, "op-conflict")));
    }

    @Test
    void shouldFailWhenCommerceReturnsUnstableLockOrder() {
        when(deliveryScopeLockApi.lockCurrentByProject(any())).thenReturn(List.of(
                new DeliveryScopeVersionFact(302L, 1L),
                new DeliveryScopeVersionFact(301L, 1L)));

        assertThrows(RuntimeException.class, () -> service.bindForStageEntry(new AcceptanceStageEntryBindingCommand(
                TENANT_ID, PROJECT_ID, 9, SNAPSHOT_ID, "S4", "S5", "op-order")));
        verify(bindingRepository, never()).append(any());
    }

    @Test
    void shouldFailClosedWhenCurrentBindingIsInconsistent() {
        AcceptanceScopeBindingDO existing = binding(301L, 3L, "PROJECT_STAGE_ENTRY");
        when(bindingRepository.selectCurrentByScopeForUpdate(any())).thenReturn(List.of(existing));

        var result = service.checkReduction(new AcceptanceScopeGuardQuery(
                TENANT_ID, PROJECT_ID, 301L, 4L, new BigDecimal("1.000000"), "op-guard"));

        assertEquals(AcceptanceScopeGuardOutcome.UNKNOWN, result.outcome());
    }

    @Test
    void shouldReturnLockedOrUnlockedFromIndependentBindingFact() {
        when(bindingRepository.selectCurrentByScopeForUpdate(any()))
                .thenReturn(List.of(binding(301L, 4L, "PROJECT_STAGE_ENTRY")))
                .thenReturn(List.of());

        var locked = service.checkReduction(new AcceptanceScopeGuardQuery(
                TENANT_ID, PROJECT_ID, 301L, 4L, new BigDecimal("1.000000"), "op-locked"));
        var unlocked = service.checkReduction(new AcceptanceScopeGuardQuery(
                TENANT_ID, PROJECT_ID, 302L, 1L, new BigDecimal("1.000000"), "op-unlocked"));

        assertEquals(AcceptanceScopeGuardOutcome.LOCKED, locked.outcome());
        assertEquals(AcceptanceScopeGuardOutcome.UNLOCKED, unlocked.outcome());
    }

    @Test
    void shouldRequireMandatoryCallerTransactionForAllProviderMethods() throws Exception {
        for (String method : List.of("bindForStageEntry", "bindEffectiveScope", "checkReduction")) {
            Class<?> argumentType = switch (method) {
                case "bindForStageEntry" -> AcceptanceStageEntryBindingCommand.class;
                case "bindEffectiveScope" -> EffectiveScopeBindingCommand.class;
                default -> AcceptanceScopeGuardQuery.class;
            };
            Transactional transactional = AcceptanceScopeBindingService.class
                    .getMethod(method, argumentType).getAnnotation(Transactional.class);
            assertEquals(Propagation.MANDATORY, transactional.propagation());
        }
    }

    private AcceptanceScopeBindingDO binding(Long scopeId, Long allocationVersion, String trigger) {
        AcceptanceScopeBindingDO row = new AcceptanceScopeBindingDO();
        row.setId(901L);
        row.setTenantId(TENANT_ID);
        row.setProjectId(PROJECT_ID);
        row.setProjectStageSnapshotId(SNAPSHOT_ID);
        row.setDeliveryScopeId(scopeId);
        row.setScopeAllocationVersion(allocationVersion);
        row.setBindingTrigger(trigger);
        row.setBindingStatus("LOCKED");
        row.setAcceptanceFactVersion(1);
        row.setVersion(0);
        return row;
    }
}
