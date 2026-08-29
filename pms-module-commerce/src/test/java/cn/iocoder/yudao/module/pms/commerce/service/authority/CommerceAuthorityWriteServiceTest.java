package cn.iocoder.yudao.module.pms.commerce.service.authority;

import cn.iocoder.yudao.module.pms.commerce.api.authority.dto.AuthorityWriteResult;
import cn.iocoder.yudao.module.pms.commerce.api.authority.dto.CommerceAuthorityWriteCommand;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderLineDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.ContractMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.SalesOrderLineMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.SalesOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    @Mock private ContractMapper contractMapper;
    @Mock private SalesOrderMapper orderMapper;
    @Mock private SalesOrderLineMapper lineMapper;
    private CommerceAuthorityWriteService service;

    @BeforeEach
    void setUp() {
        service = new CommerceAuthorityWriteService(contractMapper, orderMapper, lineMapper);
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

    private CommerceAuthorityWriteCommand command(String version, LocalDateTime updatedAt, String quantity) {
        return new CommerceAuthorityWriteCommand(1L, "batch-1", "op-1",
                List.of(contract(version, updatedAt)),
                List.of(new CommerceAuthorityWriteCommand.SalesOrderSourceRecord(
                        "ERP", "ORDER-1", version, "C01", "NORMAL", "SO-1", "ENABLED", updatedAt)),
                List.of(new CommerceAuthorityWriteCommand.SalesOrderLineSourceRecord(
                        "ERP", "LINE-1", version, "ORDER-1", "10", "ITEM-1", "设备",
                        new BigDecimal(quantity), new BigDecimal(quantity), BigDecimal.ZERO,
                        "EA", 0, "CONFIRMED", "ENABLED", updatedAt)));
    }

    private CommerceAuthorityWriteCommand.ContractSourceRecord contract(String version, LocalDateTime updatedAt) {
        return new CommerceAuthorityWriteCommand.ContractSourceRecord(
                "ERP", "CONTRACT-1", version, "C01", "CT-1", "合同一", "ENABLED", updatedAt);
    }

    private LocalDateTime time(int minute) {
        return LocalDateTime.of(2026, 8, 29, 10, minute);
    }
}
