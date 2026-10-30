package cn.iocoder.yudao.module.pms.commerce.service.scope;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.commerce.api.scope.dto.DeliveryScopeLineFact;
import cn.iocoder.yudao.module.pms.commerce.api.scope.dto.DeliveryScopeLineRef;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDetailDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.DeliveryScopeDetailMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.DeliveryScopeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DeliveryScopeLineFactServiceTest {
    private final DeliveryScopeMapper scopeMapper = mock(DeliveryScopeMapper.class);
    private final DeliveryScopeDetailMapper detailMapper = mock(DeliveryScopeDetailMapper.class);
    private final DeliveryScopeLineFactService service = new DeliveryScopeLineFactService();
    @BeforeEach void setup() {
        ReflectionTestUtils.setField(service, "scopeMapper", scopeMapper);
        ReflectionTestUtils.setField(service, "detailMapper", detailMapper);
    }

    /** 范围行查询固定返回给定行（等价模拟 in/eq项目/isNull effectiveTo/@TableLogic 过滤后的结果行数）。 */
    private void scopeRowsReturn(DeliveryScopeDO... rows) {
        doAnswer(inv -> List.of(rows)).when(scopeMapper).selectList(any());
    }

    private void detailRowsReturn(DeliveryScopeDetailDO... rows) {
        doAnswer(inv -> List.of(rows)).when(detailMapper).selectList(any());
    }

    @Test void emptySelectionReturnsEmptyResult() {
        assertTrue(service.validateSelection(10L, List.of()).isEmpty());
        assertTrue(service.validateSelection(10L, null).isEmpty());
        verifyNoInteractions(scopeMapper, detailMapper);
    }

    @Test void detailRowCarriesServerOrderFacts() {
        detailRowsReturn(detail(7L, 60L));
        scopeRowsReturn(scope(60L, 10L));
        List<DeliveryScopeLineFact> facts = service.validateSelection(10L,
                List.of(DeliveryScopeLineRef.ofDetail(7L)));
        assertEquals(1, facts.size());
        var fact = facts.getFirst();
        assertEquals(7L, fact.scopeDetailId());
        assertEquals(60L, fact.scopeId());
        assertEquals("SO-60", fact.orderNo());
        assertEquals("P-1", fact.productCode());
        assertEquals("设备一", fact.productName());
        assertEquals("SWITCH", fact.deviceTypeCode());
        assertEquals(BigDecimal.TEN, fact.allocatedQuantity());
    }

    @Test void undividedScopeRowCarriesOrderLineDescription() {
        scopeRowsReturn(scope(61L, 10L));
        List<DeliveryScopeLineFact> facts = service.validateSelection(10L,
                List.of(DeliveryScopeLineRef.ofScope(61L)));
        assertEquals(1, facts.size());
        var fact = facts.getFirst();
        assertNull(fact.scopeDetailId());
        assertEquals(61L, fact.scopeId());
        assertEquals("订单行描述", fact.productName());
    }

    @Test void scopeRowReturnedFewerThanRequestedIsRejected() {
        // 归属当前项目过滤命中不足（请求 S61，仅返回已过滤集合空）→ 拒绝且不触发明细查询
        scopeRowsReturn();
        assertThrows(ServiceException.class, () -> service.validateSelection(10L,
                List.of(DeliveryScopeLineRef.ofScope(61L))));
    }

    @Test void detailOutsideValidatedScopesIsRejected() {
        // 明细归属范围行 99，但该范围行经归属过滤后未命中 → 拒绝
        detailRowsReturn(detail(7L, 99L));
        scopeRowsReturn();
        assertThrows(ServiceException.class, () -> service.validateSelection(10L,
                List.of(DeliveryScopeLineRef.ofDetail(7L))));
    }

    @Test void detailReturnedFewerThanRequestedIsRejected() {
        detailRowsReturn();
        assertThrows(ServiceException.class, () -> service.validateSelection(10L,
                List.of(DeliveryScopeLineRef.ofDetail(7L))));
    }

    @Test void duplicateOrHalfReferenceIsRejected() {
        assertThrows(ServiceException.class, () -> service.validateSelection(10L,
                List.of(DeliveryScopeLineRef.ofDetail(7L), DeliveryScopeLineRef.ofDetail(7L))));
        assertThrows(ServiceException.class, () -> service.validateSelection(10L,
                List.of(new DeliveryScopeLineRef(7L, 60L))));
        assertThrows(ServiceException.class, () -> service.validateSelection(10L,
                List.of(new DeliveryScopeLineRef(null, null))));
        assertThrows(ServiceException.class, () -> service.validateSelection(null,
                List.of(DeliveryScopeLineRef.ofDetail(7L))));
        verifyNoInteractions(scopeMapper, detailMapper);
    }

    private DeliveryScopeDO scope(Long id, Long projectId) {
        DeliveryScopeDO scope = new DeliveryScopeDO();
        scope.setId(id); scope.setProjectId(projectId);
        scope.setOrderNo("SO-" + id); scope.setLineNo("10"); scope.setProductCode("ITEM-" + id);
        scope.setProductDesc("订单行描述"); scope.setAllocatedQty(BigDecimal.TEN);
        return scope;
    }

    private DeliveryScopeDetailDO detail(Long id, Long deliveryScopeId) {
        DeliveryScopeDetailDO detail = new DeliveryScopeDetailDO();
        detail.setId(id); detail.setDeliveryScopeId(deliveryScopeId);
        detail.setProductName("设备一"); detail.setProductCode("P-1");
        detail.setDeviceTypeCode("SWITCH"); detail.setDeviceTypeName("交换机");
        detail.setAllocatedQty(BigDecimal.TEN);
        return detail;
    }
}
