package cn.iocoder.yudao.module.pms.commerce.service.scope;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.device.AssetDeviceScopeApi;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderLineDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.outbox.CommerceOutboxEventDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDetailDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.SalesOrderLineMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.outbox.CommerceOutboxEventMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.DeliveryScopeDetailMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.DeliveryScopeMapper;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.project.api.acceptancescope.AcceptanceScopeGuardApi;
import cn.iocoder.yudao.module.pms.project.api.acceptancescope.dto.AcceptanceScopeGuardOutcome;
import cn.iocoder.yudao.module.pms.project.api.acceptancescope.dto.AcceptanceScopeGuardResult;
import cn.iocoder.yudao.module.pms.project.api.commerce.ProjectOfficeFactApi;
import cn.iocoder.yudao.module.pms.project.api.commerce.dto.ProjectFactOutcome;
import cn.iocoder.yudao.module.pms.project.api.commerce.dto.ProjectOfficeFact;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommerceDeliveryScopeCommandServiceTest {

    @Mock private ProjectScopeApi projectScopeApi;
    @Mock private ProjectOfficeFactApi projectOfficeFactApi;
    @Mock private AcceptanceStageBindingCoordinator acceptanceBindingCoordinator;
    @Mock private AssetDeviceScopeApi assetDeviceScopeApi;
    @Mock private AcceptanceScopeGuardApi acceptanceScopeGuardApi;
    @Mock private SalesOrderLineMapper orderLineMapper;
    @Mock private DeliveryScopeMapper scopeMapper;
    @Mock private DeliveryScopeDetailMapper detailMapper;
    @Mock private CommerceOutboxEventMapper outboxMapper;
    @Mock private OperationAuditApi operationAuditApi;
    private CommerceDeliveryScopeCommandService service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        service = new CommerceDeliveryScopeCommandService(projectScopeApi, projectOfficeFactApi,
                acceptanceBindingCoordinator, assetDeviceScopeApi, acceptanceScopeGuardApi, orderLineMapper,
                scopeMapper, detailMapper, outboxMapper, operationAuditApi);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldAssignNoSerialScopeFromLockedOwnerFacts() {
        allowProject();
        when(orderLineMapper.selectByIdsForUpdate(any())).thenReturn(List.of(line()));
        when(scopeMapper.selectCurrentByOrderLineIdsForUpdate(any())).thenReturn(List.of());
        doAnswer(invocation -> {
            invocation.<DeliveryScopeDO>getArgument(0).setId(401L);
            return 1;
        }).when(scopeMapper).insert(any(DeliveryScopeDO.class));

        DeliveryScopeCommandResult result = service.assign(assignCommand());

        assertEquals(401L, result.deliveryScopeId());
        assertEquals(1L, result.allocationVersion());
        ArgumentCaptor<DeliveryScopeDO> scope = ArgumentCaptor.forClass(DeliveryScopeDO.class);
        verify(scopeMapper).insert(scope.capture());
        assertEquals("P-501", scope.getValue().getProjectCode());
        assertEquals("OFF-1", scope.getValue().getOfficeDepartmentCode());
        ArgumentCaptor<DeliveryScopeDetailDO> detail = ArgumentCaptor.forClass(DeliveryScopeDetailDO.class);
        verify(detailMapper).insert(detail.capture());
        assertEquals("ERP-PRODUCT-1", detail.getValue().getProductCode());
        assertEquals(new BigDecimal("10"), detail.getValue().getAllocatedQty());
        verify(outboxMapper).insert(argThat((CommerceOutboxEventDO event) ->
                "DeliveryScopeAssigned".equals(event.getEventType())));
        verify(operationAuditApi).record(eq(1L), eq(99L), eq("op-assign"), eq("COM_SCOPE_ASSIGN"),
                eq("DeliveryScope"), eq("401"), eq("SUCCESS"), any());
    }

    @Test
    void shouldPreviewAvailableQuantityAndOwnerSnapshotWithoutWrites() {
        when(projectScopeApi.lockAndRevalidate(any())).thenReturn(
                new ProjectScopeResult(501L, 12L, Set.of(501L), Set.of()));
        when(projectOfficeFactApi.lockAndRevalidate(any())).thenReturn(new ProjectOfficeFact(
                ProjectFactOutcome.FOUND, 501L, 3, "P-501", 601L, "OFF-1", "杭州办", 4));
        when(orderLineMapper.selectByIdsForUpdate(any())).thenReturn(List.of(line()));
        DeliveryScopeDO occupied = currentScope();
        occupied.setProjectId(502L);
        when(scopeMapper.selectCurrentByOrderLineIdsForUpdate(any())).thenReturn(List.of(occupied));

        DeliveryScopePreviewResult result = service.preview(new DeliveryScopePreviewCommand(
                1L, 99L, 501L, 3, 12L, 301L, "erp-v2", new BigDecimal("15"), List.of()));

        assertTrue(result.allowed());
        assertEquals(new BigDecimal("90"), result.availableQuantity());
        assertEquals("OFF-1", result.officeDepartmentCode());
        assertEquals(List.of(401L), result.occupiedScopes().stream()
                .map(DeliveryScopePreviewResult.OccupiedScope::deliveryScopeId).toList());
        verifyNoInteractions(detailMapper, acceptanceScopeGuardApi, operationAuditApi);
        verify(scopeMapper, never()).insert(any(DeliveryScopeDO.class));
        verify(scopeMapper, never()).updateById(any(DeliveryScopeDO.class));
        verify(outboxMapper, never()).insert(any(CommerceOutboxEventDO.class));
    }

    @Test
    void shouldBindNewScopeWhenOwnerReportsAcceptanceStage() {
        allowProject();
        var stage = new AcceptanceStageBindingCoordinator.StageContext(1L, 501L, 3, 701L, true);
        when(acceptanceBindingCoordinator.lockAndRead(1L, 501L, 3, "op-assign")).thenReturn(stage);
        when(orderLineMapper.selectByIdsForUpdate(any())).thenReturn(List.of(line()));
        when(scopeMapper.selectCurrentByOrderLineIdsForUpdate(any())).thenReturn(List.of());
        doAnswer(invocation -> {
            invocation.<DeliveryScopeDO>getArgument(0).setId(401L);
            return 1;
        }).when(scopeMapper).insert(any(DeliveryScopeDO.class));

        service.assign(assignCommand());

        verify(acceptanceBindingCoordinator).bindIfRequired(stage, 401L, 1L, "op-assign");
    }

    @Test
    void shouldRejectReductionWhenAcceptanceScopeIsLockedWithoutWrites() {
        DeliveryScopeDO current = currentScope();
        allowProject();
        when(scopeMapper.selectCurrentById(any())).thenReturn(current);
        when(orderLineMapper.selectByIdsForUpdate(any())).thenReturn(List.of(line()));
        when(scopeMapper.selectCurrentByOrderLineIdsForUpdate(any())).thenReturn(List.of(current));
        when(scopeMapper.selectCurrentByIdForUpdate(any())).thenReturn(current);
        when(acceptanceScopeGuardApi.checkReduction(any())).thenReturn(new AcceptanceScopeGuardResult(
                AcceptanceScopeGuardOutcome.LOCKED, 1, 700L, current.getId(), current.getAllocationVersion()));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.adjust(changeCommand(new BigDecimal("5"), "op-adjust")));

        assertEquals("ACCEPTANCE_SCOPE_LOCKED_OR_UNKNOWN", error.getMessage());
        verify(scopeMapper, never()).updateById(any(DeliveryScopeDO.class));
        verify(scopeMapper, never()).insert(any(DeliveryScopeDO.class));
        verifyNoInteractions(detailMapper, operationAuditApi);
        verify(outboxMapper, never()).insert(any(CommerceOutboxEventDO.class));
    }

    @Test
    void shouldReleaseUnlockedScopeByClosingCurrentInterval() {
        DeliveryScopeDO current = currentScope();
        allowProject();
        when(scopeMapper.selectCurrentById(any())).thenReturn(current);
        when(orderLineMapper.selectByIdsForUpdate(any())).thenReturn(List.of(line()));
        when(scopeMapper.selectCurrentByOrderLineIdsForUpdate(any())).thenReturn(List.of(current));
        when(scopeMapper.selectCurrentByIdForUpdate(any())).thenReturn(current);
        when(acceptanceScopeGuardApi.checkReduction(any())).thenReturn(new AcceptanceScopeGuardResult(
                AcceptanceScopeGuardOutcome.UNLOCKED, null, null,
                current.getId(), current.getAllocationVersion()));

        DeliveryScopeCommandResult result = service.release(changeCommand(null, "op-release"));

        assertEquals(current.getId(), result.deliveryScopeId());
        assertEquals("RELEASED", current.getScopeStatus());
        assertNotNull(current.getEffectiveTo());
        verify(scopeMapper).updateById(current);
        verify(scopeMapper, never()).insert(any(DeliveryScopeDO.class));
        verify(outboxMapper).insert(argThat((CommerceOutboxEventDO event) ->
                "DeliveryScopeReleased".equals(event.getEventType())));
    }

    @Test
    void shouldAdjustByClosingHistoryAndAppendingNewVersionWithTwoEvents() {
        DeliveryScopeDO current = currentScope();
        allowProject();
        when(scopeMapper.selectCurrentById(any())).thenReturn(current);
        when(orderLineMapper.selectByIdsForUpdate(any())).thenReturn(List.of(line()));
        when(scopeMapper.selectCurrentByOrderLineIdsForUpdate(any())).thenReturn(List.of(current));
        when(scopeMapper.selectCurrentByIdForUpdate(any())).thenReturn(current);
        doAnswer(invocation -> {
            invocation.<DeliveryScopeDO>getArgument(0).setId(402L);
            return 1;
        }).when(scopeMapper).insert(any(DeliveryScopeDO.class));

        DeliveryScopeCommandResult result = service.adjust(changeCommand(new BigDecimal("12"), "op-adjust"));

        assertEquals(402L, result.deliveryScopeId());
        assertEquals(8L, result.allocationVersion());
        assertEquals("RELEASED", current.getScopeStatus());
        assertNotNull(current.getEffectiveTo());
        verify(scopeMapper).updateById(current);
        verify(scopeMapper).insert(argThat((DeliveryScopeDO replacement) ->
                replacement.getId().equals(402L) && replacement.getAllocationVersion().equals(8L)
                        && replacement.getAllocatedQty().compareTo(new BigDecimal("12")) == 0));
        ArgumentCaptor<CommerceOutboxEventDO> events = ArgumentCaptor.forClass(CommerceOutboxEventDO.class);
        verify(outboxMapper, times(2)).insert(events.capture());
        assertEquals(Set.of("DeliveryScopeReleased", "DeliveryScopeAssigned"),
                events.getAllValues().stream().map(CommerceOutboxEventDO::getEventType)
                        .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void shouldRejectSameIdempotencyKeyWithDifferentPayloadBeforeOwnerCalls() {
        CommerceOutboxEventDO event = new CommerceOutboxEventDO();
        event.setAggregateKey("401");
        event.setScopeVersion(1L);
        event.setPayload("{\"requestKey\":\"different\"}");
        when(outboxMapper.selectByEventId(any())).thenReturn(event);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.assign(assignCommand()));

        assertEquals("IDEMPOTENCY_PAYLOAD_CONFLICT", error.getMessage());
        verifyNoInteractions(projectScopeApi, projectOfficeFactApi, orderLineMapper, scopeMapper, detailMapper);
    }

    @Test
    void shouldRejectIncreaseWhileOrderLineHasFrozenScopeWithoutWrites() {
        DeliveryScopeDO current = currentScope();
        DeliveryScopeDO frozen = currentScope();
        frozen.setId(402L);
        frozen.setProjectId(502L);
        frozen.setScopeStatus("CONFLICT_FROZEN");
        allowProject();
        when(scopeMapper.selectCurrentById(any())).thenReturn(current);
        when(orderLineMapper.selectByIdsForUpdate(any())).thenReturn(List.of(line()));
        when(scopeMapper.selectCurrentByOrderLineIdsForUpdate(any())).thenReturn(List.of(current, frozen));
        when(scopeMapper.selectCurrentByIdForUpdate(any())).thenReturn(current);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.adjust(changeCommand(new BigDecimal("12"), "op-adjust")));

        assertEquals("DELIVERY_SCOPE_CONFLICT_FROZEN", error.getMessage());
        verifyNoInteractions(acceptanceScopeGuardApi, detailMapper, operationAuditApi);
        verify(scopeMapper, never()).updateById(any(DeliveryScopeDO.class));
        verify(scopeMapper, never()).insert(any(DeliveryScopeDO.class));
        verify(outboxMapper, never()).insert(any(CommerceOutboxEventDO.class));
    }

    private void allowProject() {
        when(projectScopeApi.lockAndRevalidate(any())).thenReturn(
                new ProjectScopeResult(501L, 12L, Set.of(501L), Set.of()));
        when(projectOfficeFactApi.lockAndRevalidate(any())).thenReturn(new ProjectOfficeFact(
                ProjectFactOutcome.FOUND, 501L, 3, "P-501", 601L, "OFF-1", "杭州办", 4));
        when(acceptanceBindingCoordinator.lockAndRead(eq(1L), eq(501L), eq(3), any())).thenReturn(
                new AcceptanceStageBindingCoordinator.StageContext(1L, 501L, 3, null, false));
    }

    private DeliveryScopeAssignCommand assignCommand() {
        return new DeliveryScopeAssignCommand(1L, 99L, 501L, 3, 12L, 301L,
                "erp-v2", new BigDecimal("10"), List.of(), "首次分配", "op-assign");
    }

    private DeliveryScopeChangeCommand changeCommand(BigDecimal proposed, String operationId) {
        return new DeliveryScopeChangeCommand(1L, 99L, 401L, 501L, 3, 12L, 7L,
                "erp-v2", proposed, List.of(), "范围调整", operationId);
    }

    private SalesOrderLineDO line() {
        SalesOrderLineDO line = new SalesOrderLineDO();
        line.setId(301L);
        line.setSourceSystem("ERP");
        line.setSourceVersion("erp-v2");
        line.setCompanyCode("C01");
        line.setCompanyName("公司一");
        line.setOrderType("NORMAL");
        line.setOrderNo("SO-1");
        line.setLineNo("10");
        line.setItemCode("ITEM-1");
        line.setItemDesc("设备");
        line.setProductCode("ERP-PRODUCT-1");
        line.setOrderQty(new BigDecimal("100"));
        line.setUnitCode("EA");
        line.setUnitScale(0);
        line.setQuantityStatus("CONFIRMED");
        line.setStatus("ENABLED");
        return line;
    }

    private DeliveryScopeDO currentScope() {
        DeliveryScopeDO scope = new DeliveryScopeDO();
        scope.setId(401L);
        scope.setTenantId(1L);
        scope.setProjectId(501L);
        scope.setOrderLineId(301L);
        scope.setAllocatedQty(new BigDecimal("10"));
        scope.setAllocationVersion(7L);
        scope.setScopeStatus("ACTIVE");
        scope.setVersion(0);
        return scope;
    }
}
