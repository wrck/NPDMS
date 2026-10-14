package cn.iocoder.yudao.module.pms.commerce.service.scope;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.binding.ProjectContractScopeApi.*;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.outbox.CommerceOutboxEventDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.*;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.outbox.CommerceOutboxEventMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.*;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.ContractScopeOrderLineRow;
import cn.iocoder.yudao.module.pms.commerce.service.contract.ContractAccessService;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.acceptancescope.AcceptanceScopeGuardApi;
import cn.iocoder.yudao.module.pms.project.api.acceptancescope.dto.AcceptanceScopeGuardOutcome;
import cn.iocoder.yudao.module.pms.project.api.acceptancescope.dto.AcceptanceScopeGuardResult;
import cn.iocoder.yudao.module.pms.project.api.commerce.ProjectOfficeFactApi;
import cn.iocoder.yudao.module.pms.project.api.commerce.dto.ProjectFactOutcome;
import cn.iocoder.yudao.module.pms.project.api.commerce.dto.ProjectOfficeFact;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectContractScopeServiceTest {
    @Mock ProjectScopeApi projectScopeApi;
    @Mock ProjectOfficeFactApi officeApi;
    @Mock ContractAccessService accessService;
    @Mock ContractProjectScopeMapper sourceMapper;
    @Mock DeliveryScopeMapper scopeMapper;
    @Mock DeliveryScopeDetailMapper detailMapper;
    @Mock DeliveryScopeProjectVersionService versions;
    @Mock AcceptanceScopeGuardApi guardApi;
    @Mock AcceptanceStageBindingCoordinator binding;
    @Mock CommerceOutboxEventMapper outbox;
    @Mock OperationAuditApi audit;
    @Mock PlatformCommandExecutionApi commands;
    @Mock cn.iocoder.yudao.module.system.api.permission.PermissionApi permissionApi;
    @InjectMocks ProjectContractScopeService service;
    private final ScopeContext context = new ScopeContext(1L, 100L, 7L, 3L, 2L, 0L, "scope-sync-1");

    @BeforeEach
    void setup() {
        TenantContextHolder.setTenantId(1L);
        lenient().when(permissionApi.hasAnyPermissions(eq(7L), any(String[].class))).thenReturn(true);
        var tree = new ProjectScopeResult(100L, 2L, Set.of(100L), Set.of());
        lenient().when(projectScopeApi.lockAndRevalidate(any())).thenReturn(tree);
        lenient().when(projectScopeApi.resolveCurrent(any())).thenReturn(tree);
        lenient().when(officeApi.lockAndRevalidate(any())).thenReturn(
                new ProjectOfficeFact(ProjectFactOutcome.FOUND, 100L, 3L, "P-100", 20L, "OFF", "上海办", 1));
        var contract = new ContractDO(); contract.setId(10L); contract.setSourceVersion("contract-v1");
        lenient().when(sourceMapper.selectContractsForUpdate(any())).thenReturn(List.of(contract));
        lenient().when(sourceMapper.selectOrderLinesForUpdate(any())).thenReturn(List.of(line(201L, "ORDER-1", "10")));
        var version = new DeliveryScopeProjectVersionDO(); version.setScopeVersion(0L);
        lenient().when(versions.lock(any(), any(), anyString(), any())).thenReturn(version);
        AtomicLong ids = new AtomicLong(900L);
        lenient().doAnswer(call -> { call.<DeliveryScopeDO>getArgument(0).setId(ids.incrementAndGet()); return 1; })
                .when(scopeMapper).insert(any(DeliveryScopeDO.class));
        lenient().when(scopeMapper.updateById(any(DeliveryScopeDO.class))).thenReturn(1);
        lenient().when(commands.execute(any(), anyString(), eq(ScopeSynchronizationResult.class), any(), any()))
                .thenAnswer(call -> new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW,
                        call.<Supplier<ScopeSynchronizationResult>>getArgument(3).get()));
    }

    @AfterEach void cleanup() { TenantContextHolder.clear(); }

    @Test void initializationIncludesMultipleOrdersAndSameProductLinesWithOneVersionAdvance() {
        when(sourceMapper.selectOrderLinesForUpdate(any())).thenReturn(List.of(
                line(201L, "ORDER-1", "10"), line(202L, "ORDER-2", "6"), line(203L, "ORDER-2", "4")));
        service.initialize(context);
        var scopes = ArgumentCaptor.forClass(DeliveryScopeDO.class);
        verify(scopeMapper, times(3)).insert(scopes.capture());
        assertEquals(List.of(201L, 202L, 203L), scopes.getAllValues().stream().map(DeliveryScopeDO::getOrderLineId).toList());
        assertEquals(List.of(new BigDecimal("10"), new BigDecimal("6"), new BigDecimal("4")),
                scopes.getAllValues().stream().map(DeliveryScopeDO::getAllocatedQty).toList());
        assertTrue(scopes.getAllValues().stream().allMatch(scope -> scope.getAllocationVersion() == 1L));
        verify(versions).advance(any(), eq("CONTRACT_INITIALIZED"), eq("7"), any());
        var events = ArgumentCaptor.forClass(CommerceOutboxEventDO.class);
        verify(outbox, times(3)).insert(events.capture());
        assertEquals(3, events.getAllValues().stream().map(CommerceOutboxEventDO::getEventId).distinct().count());
    }

    @Test void previewThenSynchronizeKeepsChildrenAndAddsOnlyRootRemainder() {
        var root = scope(301L, 100L, "4"); var child = scope(302L, 101L, "6");
        when(scopeMapper.selectCurrentByOrderLineIdsForUpdate(any())).thenReturn(List.of(root, child));
        when(sourceMapper.selectOrderLinesForUpdate(any())).thenReturn(List.of(line(201L, "ORDER-1", "15"), line(202L, "ORDER-2", "3")));
        var preview = service.preview(context);
        assertEquals(new BigDecimal("9"), preview.lines().getFirst().proposedQuantity());
        verify(scopeMapper, never()).updateById(any(DeliveryScopeDO.class));
        var result = service.synchronize(context, preview.sourceDigest());
        assertEquals(2, result.changedLineCount()); assertEquals(1L, result.scopeVersion());
        verify(scopeMapper).updateById(same(root));
        verify(scopeMapper, never()).updateById(same(child));
        assertEquals("ACTIVE", child.getScopeStatus()); assertNull(child.getEffectiveTo());
        assertEquals(new BigDecimal("6"), child.getAllocatedQty());
        assertEquals("RELEASED", root.getScopeStatus()); assertEquals(new BigDecimal("4"), root.getAllocatedQty());
        verify(versions).advance(any(), eq("CONTRACT_SYNCHRONIZED"), anyString(), any());
    }

    @Test void unchangedScopeDoesNotCreateVersionsEventsOrBindings() {
        when(scopeMapper.selectCurrentByOrderLineIdsForUpdate(any())).thenReturn(List.of(scope(301L, 100L, "10")));
        var preview = service.preview(context);
        var result = service.synchronize(context, preview.sourceDigest());
        assertEquals(0, result.changedLineCount()); assertEquals(0L, result.scopeVersion());
        verify(versions, never()).lock(any(), any(), anyString(), any());
        verifyNoInteractions(outbox, audit, binding);
    }

    @Test void sourceChangeAfterPreviewRequiresNewPreviewWithoutWrites() {
        var preview = service.preview(context);
        when(sourceMapper.selectOrderLinesForUpdate(any())).thenReturn(List.of(line(201L, "ORDER-1", "11")));
        assertTrue(assertThrows(ServiceException.class, () -> service.synchronize(context, preview.sourceDigest()))
                .getMessage().contains("CONTRACT_SCOPE_SOURCE_CHANGED"));
        verify(scopeMapper, never()).insert(any(DeliveryScopeDO.class)); verifyNoInteractions(outbox, binding);
    }

    @Test void completedRetryReturnsStoredResultWithoutReapplyingOldVersions() {
        var result = new ScopeSynchronizationResult(100L, 1L, 2);
        when(commands.execute(any(), anyString(), eq(ScopeSynchronizationResult.class), any(), any()))
                .thenReturn(new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED, result));
        assertEquals(result, service.synchronize(context, "previous-source-digest"));
        verify(projectScopeApi).resolveCurrent(any());
        verifyNoInteractions(sourceMapper, scopeMapper, outbox, binding);
    }

    @Test void changedPayloadWithSameIdempotencyKeyIsRejected() {
        when(commands.execute(any(), anyString(), eq(ScopeSynchronizationResult.class), any(), any()))
                .thenReturn(new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.CONFLICT, null));
        assertThrows(ServiceException.class, () -> service.synchronize(context, "changed-digest"));
        verifyNoInteractions(scopeMapper, outbox);
    }

    @Test void orderReductionBelowChildAllocationIsBlockedAndChildrenAreUntouched() {
        var child = scope(302L, 101L, "11");
        when(scopeMapper.selectCurrentByOrderLineIdsForUpdate(any())).thenReturn(List.of(child));
        var preview = service.preview(context);
        assertFalse(preview.valid()); assertTrue(preview.lines().getFirst().errors().contains("ALLOCATED_SCOPE_EXCEEDS_ORDER"));
        assertThrows(ServiceException.class, () -> service.synchronize(context, preview.sourceDigest()));
        verify(scopeMapper, never()).updateById(any(DeliveryScopeDO.class)); verifyNoInteractions(outbox, audit);
    }

    @Test void frozenChildScopeIsNeverSilentlyUnfrozen() {
        var child = scope(302L, 101L, "3"); child.setScopeStatus("CONFLICT_FROZEN");
        when(scopeMapper.selectCurrentByOrderLineIdsForUpdate(any())).thenReturn(List.of(child));
        assertTrue(service.preview(context).lines().getFirst().errors().contains("CHILD_SCOPE_CONFLICT_FROZEN"));
    }

    @Test void acceptanceLockedReductionIsRejectedBeforeChanges() {
        var old = scope(301L, 100L, "12");
        when(scopeMapper.selectCurrentByOrderLineIdsForUpdate(any())).thenReturn(List.of(old));
        when(guardApi.checkReduction(any())).thenReturn(new AcceptanceScopeGuardResult(AcceptanceScopeGuardOutcome.LOCKED, 1, 99L, 301L, 1L));
        var preview = service.preview(context);
        assertFalse(preview.valid()); assertTrue(preview.lines().getFirst().errors().contains("ACCEPTANCE_SCOPE_LOCKED"));
        assertThrows(ServiceException.class, () -> service.synchronize(context, preview.sourceDigest()));
        assertEquals("ACTIVE", old.getScopeStatus()); verifyNoInteractions(outbox, audit);
    }

    @Test void reductionWithUnavailableAcceptanceOwnerFailsClosed() {
        when(scopeMapper.selectCurrentByOrderLineIdsForUpdate(any())).thenReturn(List.of(scope(301L, 100L, "12")));
        when(guardApi.checkReduction(any())).thenThrow(new IllegalStateException("owner unavailable"));
        assertTrue(service.preview(context).lines().getFirst().errors().contains("ACCEPTANCE_SCOPE_UNKNOWN"));
    }

    @Test void serialScopeCannotBeReducedAutomatically() {
        when(scopeMapper.selectCurrentByOrderLineIdsForUpdate(any())).thenReturn(List.of(scope(301L, 100L, "12")));
        var serial = new DeliveryScopeDetailDO(); serial.setDeliveryScopeId(301L); serial.setSerialNo("SN-1");
        when(detailMapper.selectByScopeIds(any())).thenReturn(List.of(serial));
        assertTrue(service.preview(context).lines().getFirst().errors().contains("SERIAL_SCOPE_REQUIRES_REVIEW"));
    }

    @Test void cancelledOrderReleasesOnlyUnlockedRootAndKeepsHistoricalQuantity() {
        var line = line(201L, "ORDER-1", "10"); line.setSourceLifecycleStatus("CANCELLED");
        var old = scope(301L, 100L, "10");
        when(sourceMapper.selectOrderLinesForUpdate(any())).thenReturn(List.of(line));
        when(scopeMapper.selectCurrentByOrderLineIdsForUpdate(any())).thenReturn(List.of(old));
        when(guardApi.checkReduction(any())).thenReturn(new AcceptanceScopeGuardResult(AcceptanceScopeGuardOutcome.UNLOCKED, 1, 99L, 301L, 1L));
        var preview = service.preview(context);
        assertTrue(preview.valid()); assertEquals("RELEASE", preview.lines().getFirst().changeType());
        service.synchronize(context, preview.sourceDigest());
        assertEquals("RELEASED", old.getScopeStatus()); assertEquals(new BigDecimal("10"), old.getAllocatedQty());
        verify(scopeMapper, never()).insert(any(DeliveryScopeDO.class)); verify(detailMapper, never()).insert(any(DeliveryScopeDetailDO.class));
    }

    @Test void missingContractOrRemovedOrderAssociationDoesNotCreateArbitraryScope() {
        when(sourceMapper.selectContractsForUpdate(any())).thenReturn(List.of());
        var line = line(201L, "ORDER-1", "10"); line.setContractAssociated(false);
        when(sourceMapper.selectOrderLinesForUpdate(any())).thenReturn(List.of(line));
        var preview = service.preview(context);
        assertFalse(preview.valid()); assertEquals(List.of("PROJECT_CONTRACT_REQUIRED"), preview.errors());
        assertTrue(preview.lines().getFirst().errors().contains("CONTRACT_ORDER_RELATION_CHANGED"));
        assertThrows(ServiceException.class, () -> service.initialize(context));
        verify(scopeMapper, never()).insert(any(DeliveryScopeDO.class));
    }

    @Test void noOrderOfflineContractDoesNotInventScopeOrAdvanceVersion() {
        when(sourceMapper.selectOrderLinesForUpdate(any())).thenReturn(List.of());
        service.initialize(context);
        verifyNoInteractions(scopeMapper, detailMapper, outbox, binding);
        verify(versions, never()).advance(any(), anyString(), anyString(), any());
    }

    @Test void foreignTenantAndChildProjectFailBeforeReadingCommerceSources() {
        TenantContextHolder.setTenantId(2L);
        assertThrows(ServiceException.class, () -> service.preview(context));
        TenantContextHolder.setTenantId(1L);
        when(projectScopeApi.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(99L, 2L, Set.of(100L), Set.of()));
        assertThrows(ServiceException.class, () -> service.preview(context));
        verifyNoInteractions(sourceMapper, scopeMapper);
    }

    @Test void revokedPermissionBlocksEvenIdempotentReplay() {
        when(projectScopeApi.resolveCurrent(any())).thenReturn(new ProjectScopeResult(100L, 2L, Set.of(), Set.of()));
        assertThrows(ServiceException.class, () -> service.synchronize(context, "previous-digest"));
        verifyNoInteractions(commands, sourceMapper);
    }

    @Test void staleTreeAndInactiveProjectAreRejectedBeforeSourceReads() {
        when(projectScopeApi.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(100L, 3L, Set.of(100L), Set.of()));
        assertThrows(ServiceException.class, () -> service.preview(context));
        when(projectScopeApi.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(100L, 2L, Set.of(100L), Set.of()));
        when(officeApi.lockAndRevalidate(any())).thenReturn(new ProjectOfficeFact(ProjectFactOutcome.INACTIVE, 100L, 3L, null, null, null, null, null));
        assertThrows(ServiceException.class, () -> service.preview(context));
        verifyNoInteractions(sourceMapper, scopeMapper);
    }

    @Test void projectManagementDoesNotGrantCommerceScopeWritePermissions() {
        when(permissionApi.hasAnyPermissions(eq(7L), any(String[].class))).thenReturn(false);
        assertThrows(ServiceException.class, () -> service.synchronize(context, "previous-digest"));
        verifyNoInteractions(commands, sourceMapper);
    }

    @Test void assignPermissionDoesNotPermitReduction() {
        when(scopeMapper.selectCurrentByOrderLineIdsForUpdate(any())).thenReturn(List.of(scope(301L, 100L, "12")));
        when(guardApi.checkReduction(any())).thenReturn(new AcceptanceScopeGuardResult(AcceptanceScopeGuardOutcome.UNLOCKED, 1, 99L, 301L, 1L));
        when(permissionApi.hasAnyPermissions(7L, "pms:commerce:scope:adjust")).thenReturn(false);
        var preview = service.preview(context);
        assertThrows(ServiceException.class, () -> service.synchronize(context, preview.sourceDigest()));
        verify(scopeMapper, never()).updateById(any(DeliveryScopeDO.class));
    }

    private ContractScopeOrderLineRow line(long id, String orderNo, String quantity) {
        var line = new ContractScopeOrderLineRow();
        line.setId(id); line.setTenantId(1L); line.setOrderId(id / 2); line.setOrderNo(orderNo); line.setLineNo(String.valueOf(id));
        line.setSourceSystem("ERP"); line.setSourceVersion("erp-v1"); line.setOrderType("0");
        line.setProductCode("SAME-PRODUCT"); line.setProductDesc("产品"); line.setOrderQty(new BigDecimal(quantity));
        line.setUnitScale(0); line.setQuantityStatus("CONFIRMED"); line.setStatus("ENABLED");
        line.setSourceLifecycleStatus("ACTIVE"); line.setContractAssociated(true);
        line.setOrderAuthorityStatus("CONFIRMED"); line.setOrderLifecycleStatus("ACTIVE"); line.setOrderStatus("ENABLED");
        return line;
    }

    private DeliveryScopeDO scope(long id, long project, String quantity) {
        var scope = new DeliveryScopeDO(); scope.setId(id); scope.setTenantId(1L); scope.setProjectId(project);
        scope.setOrderLineId(201L); scope.setAllocatedQty(new BigDecimal(quantity)); scope.setAllocationVersion(1L);
        scope.setScopeStatus("ACTIVE"); scope.setProductCode("SAME-PRODUCT"); scope.setProductDesc("产品");
        return scope;
    }
}
