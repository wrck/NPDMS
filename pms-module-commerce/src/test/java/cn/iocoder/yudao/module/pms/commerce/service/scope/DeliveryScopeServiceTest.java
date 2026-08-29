package cn.iocoder.yudao.module.pms.commerce.service.scope;

import cn.iocoder.yudao.module.pms.commerce.api.scope.dto.SplitScopeApplyCommand;
import cn.iocoder.yudao.module.pms.commerce.api.scope.dto.SplitScopeApplyResult;
import cn.iocoder.yudao.module.pms.commerce.api.scope.dto.SplitScopePreviewCommand;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.outbox.CommerceOutboxEventDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDetailDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.OrderLineDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.outbox.CommerceOutboxEventMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.DeliveryScopeDetailMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.DeliveryScopeMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.OrderLineMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryScopeServiceTest {

    @Mock private OrderLineMapper orderLineMapper;
    @Mock private DeliveryScopeMapper deliveryScopeMapper;
    @Mock private DeliveryScopeDetailMapper detailMapper;
    @Mock private CommerceOutboxEventMapper outboxMapper;

    private DeliveryScopeService service;

    @BeforeEach
    void setUp() {
        service = new DeliveryScopeService(orderLineMapper, deliveryScopeMapper, detailMapper, outboxMapper);
    }

    @Test
    void shouldPreviewExactAndPartialAllocation() {
        when(orderLineMapper.selectBatchIds(anyCollection())).thenReturn(List.of(line(10L, "10")));
        when(deliveryScopeMapper.selectActiveByProjectId(100L))
                .thenReturn(List.of(scope(10L, "10", 2L)));

        SplitScopeApplyResult result = service.previewSplit(preview(2L,
                allocation("A", 10L, "2", "OFF-01", List.of()),
                allocation("B", 10L, "5", "OFF-02", List.of())));

        assertTrue(result.valid());
        assertEquals(2L, result.scopeVersion());
        assertEquals(2, result.scopes().size());
    }

    @Test
    void shouldRejectOverAllocationAndStaleVersion() {
        when(orderLineMapper.selectBatchIds(anyCollection())).thenReturn(List.of(line(10L, "10")));
        when(deliveryScopeMapper.selectActiveByProjectId(100L))
                .thenReturn(List.of(scope(10L, "4", 3L)));

        SplitScopeApplyResult result = service.previewSplit(preview(2L,
                allocation("A", 10L, "7", null, List.of())));

        assertFalse(result.valid());
        assertTrue(result.errors().contains("SCOPE_VERSION_CONFLICT"));
        assertTrue(result.errors().contains("OVER_ALLOCATION:10"));
    }

    @Test
    void shouldRejectFractionalQuantityForIntegerUnit() {
        when(orderLineMapper.selectBatchIds(anyCollection())).thenReturn(List.of(line(10L, "10")));
        when(deliveryScopeMapper.selectActiveByProjectId(100L))
                .thenReturn(List.of(scope(10L, "10", 1L)));

        SplitScopeApplyResult result = service.previewSplit(preview(1L,
                allocation("A", 10L, "1.5", null, List.of())));

        assertFalse(result.valid());
        assertTrue(result.errors().contains("UNIT_PRECISION_INVALID:10"));
    }

    @Test
    void shouldRejectDuplicateSerialBeforeReadingCommerceFacts() {
        SplitScopeApplyResult result = service.previewSplit(preview(0L,
                allocation("A", 10L, "1", null, List.of("SN-1")),
                allocation("B", 11L, "1", null, List.of("SN-1"))));

        assertFalse(result.valid());
        assertTrue(result.errors().contains("DUPLICATE_SERIAL:SN-1"));
        verifyNoInteractions(orderLineMapper, deliveryScopeMapper);
    }

    @Test
    void shouldApplyAllFactsAndOutboxWithOneNewVersion() {
        when(outboxMapper.selectByEventId(any())).thenReturn(null);
        when(orderLineMapper.selectByIdForUpdate(10L)).thenReturn(line(10L, "5"));
        DeliveryScopeDO parentScope = scope(10L, "2", 0L);
        parentScope.setId(400L);
        parentScope.setProjectId(100L);
        parentScope.setScopeStatus("ACTIVE");
        when(deliveryScopeMapper.selectActiveByProjectIdForUpdate(any())).thenReturn(List.of(parentScope));
        doAnswer(invocation -> {
            DeliveryScopeDO scope = invocation.getArgument(0);
            scope.setId(500L);
            return 1;
        }).when(deliveryScopeMapper).insert(any(DeliveryScopeDO.class));

        SplitScopeApplyCommand command = new SplitScopeApplyCommand(1L, 100L, 0, 0L, "idem-1",
                Map.of("A", 101L), Map.of("A", 0), List.of(new SplitScopeApplyCommand.Allocation(
                "A", 10L, new BigDecimal("2"), "OFF-01", List.of("SN-1", "SN-2"))));
        SplitScopeApplyResult result = service.applySplit(command);

        assertTrue(result.valid());
        assertFalse(result.replayed());
        assertEquals(1L, result.scopeVersion());
        assertEquals(500L, result.scopes().getFirst().scopeId());
        verify(detailMapper, times(2)).insert(any(DeliveryScopeDetailDO.class));
        verify(outboxMapper, times(2)).insert(any(CommerceOutboxEventDO.class));
        verify(deliveryScopeMapper).updateById(parentScope);
    }

    @Test
    void shouldReleaseParentAndCreateRemainderForPartialSplit() {
        when(outboxMapper.selectByEventId(any())).thenReturn(null);
        when(orderLineMapper.selectByIdForUpdate(10L)).thenReturn(line(10L, "5"));
        DeliveryScopeDO parentScope = scope(10L, "5", 4L);
        parentScope.setId(400L);
        parentScope.setProjectId(100L);
        parentScope.setScopeStatus("ACTIVE");
        when(deliveryScopeMapper.selectActiveByProjectIdForUpdate(any())).thenReturn(List.of(parentScope));
        AtomicLong ids = new AtomicLong(500L);
        doAnswer(invocation -> {
            ((DeliveryScopeDO) invocation.getArgument(0)).setId(ids.getAndIncrement());
            return 1;
        }).when(deliveryScopeMapper).insert(any(DeliveryScopeDO.class));

        SplitScopeApplyResult result = service.applySplit(new SplitScopeApplyCommand(1L, 100L, 0, 4L, "idem-2",
                Map.of("A", 101L), Map.of("A", 0), List.of(new SplitScopeApplyCommand.Allocation(
                "A", 10L, new BigDecimal("2"), null, List.of()))));

        assertTrue(result.valid());
        assertEquals(5L, result.scopeVersion());
        ArgumentCaptor<DeliveryScopeDO> captor = ArgumentCaptor.forClass(DeliveryScopeDO.class);
        verify(deliveryScopeMapper, times(2)).insert(captor.capture());
        assertEquals(new BigDecimal("3"), captor.getAllValues().get(0).getAllocatedQty());
        assertEquals(100L, captor.getAllValues().get(0).getProjectId());
        assertEquals(new BigDecimal("2"), captor.getAllValues().get(1).getAllocatedQty());
        assertEquals(101L, captor.getAllValues().get(1).getProjectId());
        verify(outboxMapper, times(2)).insert(any(CommerceOutboxEventDO.class));
    }

    @Test
    void shouldReplayWithoutDuplicateWrites() {
        CommerceOutboxEventDO replay = new CommerceOutboxEventDO();
        replay.setScopeVersion(8L);
        when(outboxMapper.selectByEventId(any())).thenReturn(replay);
        DeliveryScopeDO replayedScope = scope(10L, "1", 8L);
        replayedScope.setId(700L);
        replayedScope.setProjectId(101L);
        replayedScope.setSourceEvidence("F-PROJ-002:idem-1:A");
        when(deliveryScopeMapper.selectBySourceEvidencePrefix(1L, "F-PROJ-002:idem-1:"))
                .thenReturn(List.of(replayedScope));
        SplitScopeApplyCommand command = new SplitScopeApplyCommand(1L, 100L, 0, 0L, "idem-1",
                Map.of("A", 101L), Map.of("A", 0), List.of(new SplitScopeApplyCommand.Allocation(
                "A", 10L, BigDecimal.ONE, null, List.of())));

        SplitScopeApplyResult result = service.applySplit(command);

        assertTrue(result.valid());
        assertTrue(result.replayed());
        assertEquals(8L, result.scopeVersion());
        assertEquals(new SplitScopeApplyResult.AppliedScope("A", 101L, 700L), result.scopes().getFirst());
        verifyNoInteractions(orderLineMapper, detailMapper);
        verify(deliveryScopeMapper, never()).updateById(any(DeliveryScopeDO.class));
        verify(deliveryScopeMapper, never()).insert(any(DeliveryScopeDO.class));
        verify(outboxMapper, never()).insert(any(CommerceOutboxEventDO.class));
    }

    @Test
    void shouldRejectStaleApplyAfterAcquiringParentRangeLock() {
        when(outboxMapper.selectByEventId(any())).thenReturn(null);
        when(orderLineMapper.selectByIdForUpdate(10L)).thenReturn(line(10L, "5"));
        DeliveryScopeDO parentScope = scope(10L, "5", 3L);
        parentScope.setId(400L);
        parentScope.setProjectId(100L);
        parentScope.setScopeStatus("ACTIVE");
        when(deliveryScopeMapper.selectActiveByProjectIdForUpdate(any())).thenReturn(List.of(parentScope));

        SplitScopeApplyResult result = service.applySplit(new SplitScopeApplyCommand(1L, 100L, 0, 2L, "idem-3",
                Map.of("A", 101L), Map.of("A", 0), List.of(new SplitScopeApplyCommand.Allocation(
                "A", 10L, BigDecimal.ONE, null, List.of()))));

        assertFalse(result.valid());
        assertTrue(result.errors().contains("SCOPE_VERSION_CONFLICT"));
        verify(deliveryScopeMapper, never()).updateById(any(DeliveryScopeDO.class));
        verify(deliveryScopeMapper, never()).insert(any(DeliveryScopeDO.class));
        verify(outboxMapper, never()).insert(any(CommerceOutboxEventDO.class));
    }

    private SplitScopePreviewCommand preview(Long version, SplitScopePreviewCommand.Allocation... allocations) {
        return new SplitScopePreviewCommand(1L, 100L, version, List.of(allocations));
    }

    private SplitScopePreviewCommand.Allocation allocation(String key, Long lineId, String quantity,
                                                            String office, List<String> serials) {
        return new SplitScopePreviewCommand.Allocation(key, lineId, new BigDecimal(quantity), office, serials);
    }

    private OrderLineDO line(Long id, String quantity) {
        OrderLineDO line = new OrderLineDO();
        line.setId(id);
        line.setTenantId(1L);
        line.setQuantity(new BigDecimal(quantity));
        line.setQuantityStatus("CONFIRMED");
        line.setUnitCode("EA");
        return line;
    }

    private DeliveryScopeDO scope(Long orderLineId, String quantity, Long allocationVersion) {
        DeliveryScopeDO scope = new DeliveryScopeDO();
        scope.setOrderLineId(orderLineId);
        scope.setAllocatedQty(new BigDecimal(quantity));
        scope.setAllocationVersion(allocationVersion);
        return scope;
    }
}
