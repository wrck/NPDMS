package cn.iocoder.yudao.module.pms.commerce.service.authority;

import cn.iocoder.yudao.module.pms.commerce.api.authority.dto.AuthorityWriteResult;
import cn.iocoder.yudao.module.pms.commerce.api.authority.dto.CommerceAuthorityWriteCommand;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderLineDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.outbox.CommerceOutboxEventDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.ContractMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.SalesOrderLineMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.outbox.CommerceOutboxEventMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.DeliveryScopeMapper;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommerceAuthorityWriteServiceTest {

    @Mock private ContractMapper contractMapper;
    @Mock private SalesOrderMapper orderMapper;
    @Mock private SalesOrderLineMapper lineMapper;
    @Mock private DeliveryScopeMapper scopeMapper;
    @Mock private CommerceOutboxEventMapper outboxMapper;
    @Mock private ProjectParticipantFactApi participantFactApi;
    private CommerceAuthorityWriteService service;

    @BeforeEach
    void setUp() {
        service = new CommerceAuthorityWriteService(contractMapper, orderMapper, lineMapper,
                scopeMapper, outboxMapper, participantFactApi);
    }

    @Test
    void shouldCreateAuthorityCopiesAndResolveLineParentFromSameBatch() {
        doAnswer(invocation -> { ((ContractDO) invocation.getArgument(0)).setId(11L); return 1; })
                .when(contractMapper).insert(any(ContractDO.class));
        doAnswer(invocation -> { ((SalesOrderDO) invocation.getArgument(0)).setId(21L); return 1; })
                .when(orderMapper).insert(any(SalesOrderDO.class));

        AuthorityWriteResult result = service.apply(command("v1", time(10), "100"));

        assertFalse(result.replayed());
        assertEquals(1, result.contractCount());
        assertEquals(1, result.salesOrderCount());
        assertEquals(1, result.salesOrderLineCount());
        verify(lineMapper).insert(argThat((SalesOrderLineDO line) -> line.getOrderId().equals(21L)
                && line.getProductCode().equals("ERP-PRODUCT-1")
                && line.getOrderQty().compareTo(new BigDecimal("100")) == 0));
    }

    @Test
    void shouldReplayOlderSourceWithoutOverwritingCurrentFact() {
        ContractDO current = new ContractDO();
        current.setId(11L);
        current.setSourceVersion("v2");
        current.setSourceUpdatedAt(time(20));
        current.setCompanyCode("C01");
        current.setContractNo("CT-1");
        current.setContractName("合同一");
        current.setStatus("ENABLED");
        when(contractMapper.selectBySourceForUpdate(any())).thenReturn(current);

        AuthorityWriteResult result = service.apply(new CommerceAuthorityWriteCommand(
                1L, "batch-old", "op-old", List.of(contract("v1", time(10))), List.of(), List.of()));

        assertTrue(result.replayed());
        verify(contractMapper, never()).updateById(any(ContractDO.class));
    }

    @Test
    void shouldRejectSameVersionWithDifferentPayload() {
        ContractDO current = new ContractDO();
        current.setId(11L);
        current.setSourceVersion("v1");
        current.setSourceUpdatedAt(time(10));
        current.setCompanyCode("C01");
        current.setContractNo("CT-1");
        current.setContractName("不同名称");
        current.setStatus("ENABLED");
        when(contractMapper.selectBySourceForUpdate(any())).thenReturn(current);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.apply(new CommerceAuthorityWriteCommand(
                        1L, "batch-conflict", "op-conflict", List.of(contract("v1", time(10))),
                        List.of(), List.of())));

        assertEquals("COMMERCE_AUTHORITY_SAME_VERSION_CONFLICT", error.getMessage());
        verify(contractMapper, never()).updateById(any(ContractDO.class));
    }

    @Test
    void shouldTreatEquivalentDecimalScaleAsSamePayload() {
        SalesOrderDO order = new SalesOrderDO();
        order.setId(21L);
        order.setSourceVersion("v1");
        order.setSourceUpdatedAt(time(10));
        order.setCompanyCode("C01");
        order.setOrderType("NORMAL");
        order.setOrderNo("SO-1");
        order.setStatus("ENABLED");
        when(orderMapper.selectBySourceForUpdate(any())).thenReturn(order);
        SalesOrderLineDO line = new SalesOrderLineDO();
        line.setId(31L);
        line.setOrderId(21L);
        line.setSourceVersion("v1");
        line.setSourceUpdatedAt(time(10));
        line.setLineNo("10");
        line.setItemCode("ITEM-1");
        line.setItemDesc("设备");
        line.setProductCode("ERP-PRODUCT-1");
        line.setOrderQty(new BigDecimal("100.000000"));
        line.setOpenQty(new BigDecimal("100.000000"));
        line.setDeliveredQty(new BigDecimal("0.000000"));
        line.setUnitCode("EA");
        line.setUnitScale(0);
        line.setQuantityStatus("CONFIRMED");
        line.setStatus("ENABLED");
        when(lineMapper.selectBySourceForUpdate(any())).thenReturn(line);

        CommerceAuthorityWriteCommand source = command("v1", time(10), "100");
        AuthorityWriteResult result = service.apply(new CommerceAuthorityWriteCommand(
                1L, source.sourceBatchId(), source.operationId(), List.of(),
                source.salesOrders(), source.salesOrderLines()));

        assertTrue(result.replayed());
        verify(lineMapper, never()).updateById(any(SalesOrderLineDO.class));
    }

    @Test
    void shouldRejectSameVersionWithDifferentProductCode() {
        SalesOrderDO order = new SalesOrderDO();
        order.setId(21L);
        order.setSourceVersion("v1");
        order.setSourceUpdatedAt(time(10));
        order.setCompanyCode("C01");
        order.setOrderType("NORMAL");
        order.setOrderNo("SO-1");
        order.setStatus("ENABLED");
        when(orderMapper.selectBySourceForUpdate(any())).thenReturn(order);
        SalesOrderLineDO line = new SalesOrderLineDO();
        line.setId(31L);
        line.setOrderId(21L);
        line.setSourceVersion("v1");
        line.setSourceUpdatedAt(time(10));
        line.setLineNo("10");
        line.setItemCode("ITEM-1");
        line.setItemDesc("设备");
        line.setProductCode("ERP-PRODUCT-OTHER");
        line.setOrderQty(new BigDecimal("100"));
        line.setOpenQty(new BigDecimal("100"));
        line.setDeliveredQty(BigDecimal.ZERO);
        line.setUnitCode("EA");
        line.setUnitScale(0);
        line.setQuantityStatus("CONFIRMED");
        line.setStatus("ENABLED");
        when(lineMapper.selectBySourceForUpdate(any())).thenReturn(line);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.apply(command("v1", time(10), "100")));

        assertEquals("COMMERCE_AUTHORITY_SAME_VERSION_CONFLICT", error.getMessage());
        verify(lineMapper, never()).updateById(any(SalesOrderLineDO.class));
    }

    @Test
    void shouldRejectNewerVersionThatChangesStableBusinessIdentity() {
        ContractDO current = new ContractDO();
        current.setId(11L);
        current.setSourceVersion("v1");
        current.setSourceUpdatedAt(time(10));
        current.setCompanyCode("C01");
        current.setContractNo("CT-OLD");
        when(contractMapper.selectBySourceForUpdate(any())).thenReturn(current);

        CommerceAuthorityWriteCommand.ContractSourceRecord changed =
                new CommerceAuthorityWriteCommand.ContractSourceRecord(
                        "ERP", "CONTRACT-1", "v2", "C01", "CT-NEW", "合同一", "ENABLED", time(20));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.apply(new CommerceAuthorityWriteCommand(
                        1L, "batch-2", "op-2", List.of(changed), List.of(), List.of())));
        assertEquals("COMMERCE_AUTHORITY_IDENTITY_CONFLICT", error.getMessage());
        verify(contractMapper, never()).updateById(any(ContractDO.class));
    }

    @Test
    void shouldFreezeActiveScopesAndRequestProjectManagerNotificationOnReduction() {
        SalesOrderDO order = currentOrder();
        SalesOrderLineDO line = currentLine(order.getId(), "v1", "100");
        when(orderMapper.selectBySourceForUpdate(any())).thenReturn(order);
        when(lineMapper.selectBySourceForUpdate(any())).thenReturn(line);
        DeliveryScopeDO first = scope(41L, 501L, line.getId(), "30", 7L);
        DeliveryScopeDO second = scope(42L, 502L, line.getId(), "30", 8L);
        when(scopeMapper.selectActiveByOrderLineIdsForUpdate(any())).thenReturn(List.of(first, second));
        when(participantFactApi.inspect(any())).thenAnswer(invocation -> {
            Long projectId = invocation.<ProjectParticipantFactQuery>getArgument(0).projectId();
            return new ProjectParticipantFact(projectId, projectId + 1000,
                    Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER), "PRIMARY", "ACTIVE", "S1", 3, 3L);
        });

        AuthorityWriteResult result = service.apply(lineOnlyCommand("v2", time(20), "40"));

        assertFalse(result.replayed());
        assertEquals("CONFLICT_FROZEN", first.getScopeStatus());
        assertEquals("CONFLICT_FROZEN", second.getScopeStatus());
        verify(scopeMapper).updateById(first);
        verify(scopeMapper).updateById(second);
        var events = org.mockito.ArgumentCaptor.forClass(CommerceOutboxEventDO.class);
        verify(outboxMapper, times(2)).insert(events.capture());
        assertTrue(events.getAllValues().stream().allMatch(event ->
                "NotificationRequested".equals(event.getEventType())
                        && event.getPayload().contains("DELIVERY_SCOPE_CONFLICT_FROZEN")
                        && event.getPayload().contains("recipientUserId")
                        && event.getPayload().contains("\"erpSourceVersion\":\"v2\"")));
    }

    @Test
    void shouldPersistRetryableRoleRecipientWhenProjectManagerFactUnavailable() {
        SalesOrderDO order = currentOrder();
        SalesOrderLineDO line = currentLine(order.getId(), "v1", "100");
        when(orderMapper.selectBySourceForUpdate(any())).thenReturn(order);
        when(lineMapper.selectBySourceForUpdate(any())).thenReturn(line);
        DeliveryScopeDO scope = scope(41L, 501L, line.getId(), "60", 7L);
        when(scopeMapper.selectActiveByOrderLineIdsForUpdate(any())).thenReturn(List.of(scope));
        when(participantFactApi.inspect(any())).thenThrow(new IllegalStateException("PROJ_UNAVAILABLE"));

        service.apply(lineOnlyCommand("v2", time(20), "40"));

        assertEquals("CONFLICT_FROZEN", scope.getScopeStatus());
        verify(outboxMapper).insert(argThat((CommerceOutboxEventDO event) ->
                event.getPayload().contains("\"recipientRole\":\"PROJECT_MANAGER\"")
                        && event.getPayload().contains("\"recipientResolution\":\"RETRYABLE_ROLE\"")
                        && !event.getPayload().contains("recipientUserId")));
    }

    @Test
    void shouldNotFreezeWhenNewEffectiveQuantityStillCoversAllocations() {
        SalesOrderDO order = currentOrder();
        SalesOrderLineDO line = currentLine(order.getId(), "v1", "100");
        when(orderMapper.selectBySourceForUpdate(any())).thenReturn(order);
        when(lineMapper.selectBySourceForUpdate(any())).thenReturn(line);
        when(scopeMapper.selectActiveByOrderLineIdsForUpdate(any()))
                .thenReturn(List.of(scope(41L, 501L, line.getId(), "30", 7L)));

        service.apply(lineOnlyCommand("v2", time(20), "40"));

        verify(scopeMapper, never()).updateById(any(DeliveryScopeDO.class));
        verify(outboxMapper, never()).insert(any(CommerceOutboxEventDO.class));
        verifyNoInteractions(participantFactApi);
    }

    private CommerceAuthorityWriteCommand command(String version, LocalDateTime updatedAt, String quantity) {
        return new CommerceAuthorityWriteCommand(1L, "batch-1", "op-1",
                List.of(contract(version, updatedAt)),
                List.of(new CommerceAuthorityWriteCommand.SalesOrderSourceRecord(
                        "ERP", "ORDER-1", version, "C01", "NORMAL", "SO-1", "ENABLED", updatedAt)),
                List.of(new CommerceAuthorityWriteCommand.SalesOrderLineSourceRecord(
                        "ERP", "LINE-1", version, "ORDER-1", "10", "ITEM-1", "设备",
                        "ERP-PRODUCT-1",
                        new BigDecimal(quantity), new BigDecimal(quantity), BigDecimal.ZERO,
                        "EA", 0, "CONFIRMED", "ENABLED", updatedAt)));
    }

    private CommerceAuthorityWriteCommand lineOnlyCommand(String version, LocalDateTime updatedAt,
                                                           String quantity) {
        return new CommerceAuthorityWriteCommand(1L, "batch-" + version, "op-" + version, List.of(),
                List.of(new CommerceAuthorityWriteCommand.SalesOrderSourceRecord(
                        "ERP", "ORDER-1", version, "C01", "NORMAL", "SO-1", "ENABLED", updatedAt)),
                List.of(new CommerceAuthorityWriteCommand.SalesOrderLineSourceRecord(
                        "ERP", "LINE-1", version, "ORDER-1", "10", "ITEM-1", "设备",
                        "ERP-PRODUCT-1", new BigDecimal(quantity), new BigDecimal(quantity), BigDecimal.ZERO,
                        "EA", 0, "CONFIRMED", "ENABLED", updatedAt)));
    }

    private SalesOrderDO currentOrder() {
        SalesOrderDO order = new SalesOrderDO();
        order.setId(21L);
        order.setSourceVersion("v1");
        order.setSourceUpdatedAt(time(10));
        order.setCompanyCode("C01");
        order.setOrderType("NORMAL");
        order.setOrderNo("SO-1");
        order.setStatus("ENABLED");
        return order;
    }

    private SalesOrderLineDO currentLine(Long orderId, String sourceVersion, String quantity) {
        SalesOrderLineDO line = new SalesOrderLineDO();
        line.setId(31L);
        line.setOrderId(orderId);
        line.setSourceVersion(sourceVersion);
        line.setSourceUpdatedAt(time(10));
        line.setLineNo("10");
        line.setItemCode("ITEM-1");
        line.setItemDesc("设备");
        line.setProductCode("ERP-PRODUCT-1");
        line.setOrderQty(new BigDecimal(quantity));
        line.setOpenQty(new BigDecimal(quantity));
        line.setDeliveredQty(BigDecimal.ZERO);
        line.setUnitCode("EA");
        line.setUnitScale(0);
        line.setQuantityStatus("CONFIRMED");
        line.setStatus("ENABLED");
        return line;
    }

    private DeliveryScopeDO scope(Long id, Long projectId, Long orderLineId, String quantity,
                                  Long allocationVersion) {
        DeliveryScopeDO scope = new DeliveryScopeDO();
        scope.setId(id);
        scope.setProjectId(projectId);
        scope.setOrderLineId(orderLineId);
        scope.setAllocatedQty(new BigDecimal(quantity));
        scope.setAllocationVersion(allocationVersion);
        scope.setScopeStatus("ACTIVE");
        scope.setVersion(0);
        return scope;
    }

    private CommerceAuthorityWriteCommand.ContractSourceRecord contract(String version, LocalDateTime updatedAt) {
        return new CommerceAuthorityWriteCommand.ContractSourceRecord(
                "ERP", "CONTRACT-1", version, "C01", "CT-1", "合同一", "ENABLED", updatedAt);
    }

    private LocalDateTime time(int minute) {
        return LocalDateTime.of(2026, 8, 29, 10, minute);
    }
}
