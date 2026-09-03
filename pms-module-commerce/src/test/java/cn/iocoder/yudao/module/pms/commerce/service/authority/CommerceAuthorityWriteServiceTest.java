package cn.iocoder.yudao.module.pms.commerce.service.authority;

import cn.iocoder.yudao.module.pms.commerce.api.authority.CommerceAuthorityIngestApi;
import cn.iocoder.yudao.module.pms.commerce.api.authority.dto.*;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.ContractMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.SalesOrderLineMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.SalesOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommerceAuthorityWriteServiceTest {

    @Mock private CommerceAuthorityIngestApi ingestApi;
    @Mock private ContractMapper contractMapper;
    @Mock private SalesOrderMapper orderMapper;
    @Mock private SalesOrderLineMapper lineMapper;
    private CommerceAuthorityWriteService service;

    @BeforeEach
    void setUp() {
        service = new CommerceAuthorityWriteService(ingestApi, contractMapper, orderMapper, lineMapper);
    }

    @Test
    void shouldAdaptLegacyCommandToUnifiedBatchOwnerEntry() {
        SalesOrderDO current = new SalesOrderDO();
        current.setSourceVersion("v1");
        when(orderMapper.selectBySourceForUpdate(any())).thenReturn(current);
        when(ingestApi.ingestBatch(any())).thenReturn(new CommerceAuthorityBatchResult(
                "op-2", "batch-2", CommerceAuthorityBatchResult.Decision.ACCEPTED));

        AuthorityWriteResult result = service.apply(command("v2", "100"));

        assertFalse(result.replayed());
        assertEquals(1, result.salesOrderCount());
        assertEquals(1, result.salesOrderLineCount());
        ArgumentCaptor<CommerceAuthorityBatchCommand> batch =
                ArgumentCaptor.forClass(CommerceAuthorityBatchCommand.class);
        verify(ingestApi).ingestBatch(batch.capture());
        assertEquals("ERP", batch.getValue().sourceSystem());
        assertEquals("v1", batch.getValue().salesOrders().getFirst().expectedPreviousSourceVersion());
        assertEquals("ERP-PRODUCT-1", batch.getValue().orderLines().getFirst().productCode());
        assertEquals(new BigDecimal("100"), batch.getValue().orderLines().getFirst().openQuantity());
    }

    @Test
    void shouldExposeUnifiedReplayAsLegacyReplay() {
        when(ingestApi.ingestBatch(any())).thenReturn(new CommerceAuthorityBatchResult(
                "op-2", "batch-2", CommerceAuthorityBatchResult.Decision.ACCEPTED_NO_CHANGE));

        AuthorityWriteResult result = service.apply(command("v1", "100"));

        assertTrue(result.replayed());
    }

    @Test
    void shouldRejectMixedSourceSystemsBeforeOwnerEntry() {
        CommerceAuthorityWriteCommand source = command("v1", "100");
        CommerceAuthorityWriteCommand mixed = new CommerceAuthorityWriteCommand(
                source.tenantId(), source.sourceBatchId(), source.operationId(), source.contracts(),
                List.of(new CommerceAuthorityWriteCommand.SalesOrderSourceRecord(
                        "CRM", "ORDER-1", "v1", "C01", "NORMAL", "SO-1", "ENABLED", time())),
                source.salesOrderLines());

        assertThrows(IllegalArgumentException.class, () -> service.apply(mixed));
        verifyNoInteractions(ingestApi);
    }

    private CommerceAuthorityWriteCommand command(String version, String quantity) {
        return new CommerceAuthorityWriteCommand(1L, "batch-2", "op-2", List.of(),
                List.of(new CommerceAuthorityWriteCommand.SalesOrderSourceRecord(
                        "ERP", "ORDER-1", version, "C01", "NORMAL", "SO-1", "ENABLED", time())),
                List.of(new CommerceAuthorityWriteCommand.SalesOrderLineSourceRecord(
                        "ERP", "LINE-1", version, "ORDER-1", "10", "ITEM-1", "设备",
                        "ERP-PRODUCT-1", new BigDecimal(quantity), new BigDecimal(quantity),
                        BigDecimal.ZERO, "EA", 0, "CONFIRMED", "ENABLED", time())));
    }

    private LocalDateTime time() {
        return LocalDateTime.of(2026, 8, 29, 10, 20);
    }
}
