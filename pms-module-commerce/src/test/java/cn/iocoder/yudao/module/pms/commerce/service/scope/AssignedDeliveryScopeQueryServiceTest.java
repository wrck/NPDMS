package cn.iocoder.yudao.module.pms.commerce.service.scope;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.scope.DeliveryScopeFactException;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderLineDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDetailDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeProjectVersionDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.SalesOrderLineMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.query.SalesOrderLineIdsQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.DeliveryScopeDetailMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.DeliveryScopeMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.DeliveryScopeProjectVersionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssignedDeliveryScopeQueryServiceTest {

    @Mock private DeliveryScopeProjectVersionMapper versionMapper;
    @Mock private DeliveryScopeMapper scopeMapper;
    @Mock private DeliveryScopeDetailMapper detailMapper;
    @Mock private SalesOrderLineMapper orderLineMapper;
    private AssignedDeliveryScopeQueryService service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        service = new AssignedDeliveryScopeQueryService(
                versionMapper, scopeMapper, detailMapper, orderLineMapper);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldReturnCanonicalAssignedScopeWithoutLockWhenVersionIsNotRequired() {
        when(versionMapper.selectCurrent(any())).thenReturn(version(4L));
        when(scopeMapper.selectCurrentByProjectId(any())).thenReturn(List.of(scope("ACTIVE")));
        when(detailMapper.selectByScopeIds(any())).thenReturn(List.of(detail(" SN-1 ")));
        when(orderLineMapper.selectByIds(any(SalesOrderLineIdsQuery.class))).thenReturn(List.of(line()));

        var result = service.getAssignedScope(10L, null);

        assertEquals(4L, result.scopeVersion());
        assertEquals(List.of("SN-1"), result.assignedLines().getFirst().serialNumbers());
        assertEquals("PRODUCT-A", result.assignedLines().getFirst().productCode());
        verify(versionMapper, never()).selectForUpdate(any());
        verify(scopeMapper, never()).selectCurrentByProjectIdForUpdate(any());
    }

    @Test
    void shouldLockScopeBeforeRejectingStaleExpectedVersion() {
        when(scopeMapper.selectCurrentByProjectIdForUpdate(any())).thenReturn(List.of());
        when(versionMapper.selectForUpdate(any())).thenReturn(version(5L));

        DeliveryScopeFactException failure = assertThrows(DeliveryScopeFactException.class,
                () -> service.getAssignedScope(10L, 4L));

        assertEquals(DeliveryScopeFactException.Code.SCOPE_STALE, failure.getCode());
        var lockOrder = inOrder(scopeMapper, versionMapper);
        lockOrder.verify(scopeMapper).selectCurrentByProjectIdForUpdate(any());
        lockOrder.verify(versionMapper).selectForUpdate(any());
    }

    @Test
    void shouldFailClosedForCurrentConflict() {
        when(versionMapper.selectForUpdate(any())).thenReturn(version(4L));
        when(scopeMapper.selectCurrentByProjectIdForUpdate(any()))
                .thenReturn(List.of(scope("CONFLICT_FROZEN")));

        DeliveryScopeFactException failure = assertThrows(DeliveryScopeFactException.class,
                () -> service.getAssignedScope(10L, 4L));

        assertEquals(DeliveryScopeFactException.Code.SCOPE_CONFLICT, failure.getCode());
        verify(detailMapper, never()).selectByScopeIds(any());
    }

    @Test
    void shouldFailClosedWhenActiveScopeHasNoQualifiedDetail() {
        when(versionMapper.selectCurrent(any())).thenReturn(version(4L));
        when(scopeMapper.selectCurrentByProjectId(any())).thenReturn(List.of(scope("ACTIVE")));
        when(detailMapper.selectByScopeIds(any())).thenReturn(List.of());
        when(orderLineMapper.selectByIds(any(SalesOrderLineIdsQuery.class))).thenReturn(List.of(line()));

        DeliveryScopeFactException failure = assertThrows(DeliveryScopeFactException.class,
                () -> service.getAssignedScope(10L, null));

        assertEquals(DeliveryScopeFactException.Code.OWNER_DATA_CORRUPTED, failure.getCode());
    }

    private DeliveryScopeProjectVersionDO version(long scopeVersion) {
        DeliveryScopeProjectVersionDO value = new DeliveryScopeProjectVersionDO();
        value.setScopeVersion(scopeVersion);
        return value;
    }

    private DeliveryScopeDO scope(String status) {
        DeliveryScopeDO value = new DeliveryScopeDO();
        value.setId(100L);
        value.setOrderLineId(200L);
        value.setScopeStatus(status);
        return value;
    }

    private DeliveryScopeDetailDO detail(String serialNumber) {
        DeliveryScopeDetailDO value = new DeliveryScopeDetailDO();
        value.setId(300L);
        value.setDeliveryScopeId(100L);
        value.setAllocatedQty(BigDecimal.ONE);
        value.setDetailStatus("ACTIVE");
        value.setSerialNo(serialNumber);
        return value;
    }

    private SalesOrderLineDO line() {
        SalesOrderLineDO value = new SalesOrderLineDO();
        value.setId(200L);
        value.setQuantityStatus("CONFIRMED");
        value.setSourceLifecycleStatus("ACTIVE");
        value.setUnitCode("SET");
        value.setProductCode("PRODUCT-A");
        return value;
    }
}
