package cn.iocoder.yudao.module.pms.commerce.service.contract;

import cn.iocoder.yudao.module.pms.commerce.controller.admin.contract.vo.ContractCreationSourceRespVO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.executionorder.CrmExecutionOrderDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderDO;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CreationSourceRelationshipTest {
    @Test void fieldsComeFromTheOrdersExecutionEvenWhenAnotherExecutionIsNewer() {
        var order = order();
        var related = execution(11L, "EX-A", "SMS", 1);
        var unrelated = execution(12L, "EX-B", "SMS", 2);
        var result = CreationSourceResolver.resolve(List.of(order), List.of(related, unrelated));
        assertSame(related, result.execution());
        assertEquals("EX-A name", result.projectName());
    }

    @Test void missingRelationshipNeverBorrowsAnUnrelatedExecution() {
        var order = order(); order.setExecutionNo(null);
        var result = CreationSourceResolver.resolve(List.of(order), List.of(execution(11L, "EX-A", "SMS", 1)));
        assertNull(result.execution());
        assertEquals("Order name", result.projectName());
    }

    @Test void ambiguousExecutionNumberAcrossSourcesFailsClosed() {
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class, () -> CreationSourceResolver.resolve(List.of(order()),
                List.of(execution(11L, "EX-A", "SMS", 1), execution(12L, "EX-A", "CRM", 2))));
    }

    @Test void creationPreviewRedactsAllAmountsWithoutSensitivePermission() {
        var contract = new ContractDO(); contract.setId(1L); contract.setCompanyCode("C1"); contract.setContractAmount(BigDecimal.TEN);
        var execution = execution(11L, "EX-A", "SMS", 1); execution.setProjectAmount(BigDecimal.TEN);
        var detail = new ContractAccessService.CreationSourceDetail(contract, List.of(order()), List.of(execution), List.of());
        var hidden = ContractCreationSourceRespVO.from(detail, false);
        assertNull(hidden.contract().contractAmount());
        assertNull(hidden.orders().getFirst().orderAmount());
        assertNull(hidden.executionOrders().getFirst().projectAmount());
        var visible = ContractCreationSourceRespVO.from(detail, true);
        assertEquals(BigDecimal.TEN, visible.contract().contractAmount());
        assertEquals(BigDecimal.TEN, visible.orders().getFirst().orderAmount());
        assertEquals(BigDecimal.TEN, visible.executionOrders().getFirst().projectAmount());
    }

    @Test void foreignCompanyExecutionCannotResolveOrAppearInMultiOrderPreview() {
        var foreign = execution(11L, "EX-A", "CRM", 1); foreign.setCompanyCode("OTHER");
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class, () -> CreationSourceResolver.resolve(List.of(order()), List.of(foreign)));
        var contract = new ContractDO(); contract.setCompanyCode("C1");
        var second = order(); second.setId(2L); second.setExecutionNo(null);
        var detail = new ContractAccessService.CreationSourceDetail(contract, List.of(order(), second), List.of(foreign), List.of());
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class, () -> ContractCreationSourceRespVO.from(detail, false));
    }

    @Test void conflictingCompanyIdentityOrMissingSourceIdentityFailsClosed() {
        var order = order(); order.setCompanyId(7L);
        var execution = execution(11L, "EX-A", "CRM", 1); execution.setCompanyId(8L);
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class, () -> CreationSourceResolver.resolve(List.of(order), List.of(execution)));
        execution.setCompanyId(7L); execution.setCompanyCode(null);
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class, () -> CreationSourceResolver.resolve(List.of(order), List.of(execution)));
        execution.setCompanyCode("C1"); execution.setSourceSystem(null);
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class, () -> CreationSourceResolver.resolve(List.of(order), List.of(execution)));
    }

    private SalesOrderDO order() {
        var value = new SalesOrderDO(); value.setId(1L); value.setOrderNo("SO-1");
        value.setExecutionNo("EX-A"); value.setSalesType("02"); value.setSourceProjectName("Order name");
        value.setTenantId(1L); value.setCompanyCode("C1"); value.setSourceSystem("SAP"); value.setOrderAmount(BigDecimal.TEN); return value;
    }
    private CrmExecutionOrderDO execution(Long id, String number, String source, int day) {
        var value = new CrmExecutionOrderDO(); value.setId(id); value.setExecutionNo(number);
        value.setSourceSystem(source); value.setProjectName(number + " name");
        value.setTenantId(1L); value.setCompanyCode("C1"); value.setSubmitTime(LocalDateTime.of(2026, 10, day, 0, 0)); return value;
    }
}
