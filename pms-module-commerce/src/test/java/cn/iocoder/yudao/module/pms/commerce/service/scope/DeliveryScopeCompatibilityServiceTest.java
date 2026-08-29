package cn.iocoder.yudao.module.pms.commerce.service.scope;

import cn.iocoder.yudao.module.pms.asset.api.device.AssetDeviceScopeApi;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.SerialScopeValidationResult;
import cn.iocoder.yudao.module.pms.commerce.api.scope.dto.SplitScopeApplyCommand;
import cn.iocoder.yudao.module.pms.commerce.api.scope.dto.SplitScopeApplyResult;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderLineDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.outbox.CommerceOutboxEventDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDetailDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.SalesOrderLineMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.outbox.CommerceOutboxEventMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.DeliveryScopeDetailMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.DeliveryScopeMapper;
import cn.iocoder.yudao.module.pms.project.api.commerce.ProjectOfficeFactApi;
import cn.iocoder.yudao.module.pms.project.api.commerce.dto.ProjectFactOutcome;
import cn.iocoder.yudao.module.pms.project.api.commerce.dto.ProjectOfficeFact;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryScopeCompatibilityServiceTest {

    @Mock private SalesOrderLineMapper orderLineMapper;
    @Mock private DeliveryScopeMapper scopeMapper;
    @Mock private DeliveryScopeDetailMapper detailMapper;
    @Mock private CommerceOutboxEventMapper outboxMapper;
    @Mock private ProjectOfficeFactApi projectOfficeFactApi;
    @Mock private AssetDeviceScopeApi assetDeviceScopeApi;
    private DeliveryScopeCompatibilityService service;

    @BeforeEach
    void setUp() {
        service = new DeliveryScopeCompatibilityService(orderLineMapper, scopeMapper, detailMapper,
                outboxMapper, projectOfficeFactApi, assetDeviceScopeApi);
    }

    @Test
    void shouldRejectMissingProjectVersionsBeforeReadingOwnerOrCommerceFacts() {
        SplitScopeApplyCommand command = new SplitScopeApplyCommand(1L, 100L, null, 4L, "idem",
                Map.of("A", 101L), Map.of(), List.of(allocation("A", "2", List.of())));

        SplitScopeApplyResult result = service.applySplit(command);

        assertFalse(result.valid());
        assertTrue(result.errors().contains("INVALID_APPLY_COMMAND"));
        verifyNoInteractions(projectOfficeFactApi, assetDeviceScopeApi, orderLineMapper, scopeMapper,
                detailMapper, outboxMapper);
    }

    @Test
    void shouldUseLockedOwnerProductCodeForChildAndParentRemainder() {
        stubProjectFacts();
        SalesOrderLineDO line = line("ERP-PRODUCT-1");
        when(orderLineMapper.selectByIdsForUpdate(any())).thenReturn(List.of(line));
        DeliveryScopeDO parent = parentScope("5", 4L);
        when(scopeMapper.selectActiveByProjectIdForUpdate(any())).thenReturn(List.of(parent));
        AtomicLong ids = new AtomicLong(500L);
        doAnswer(invocation -> {
            ((DeliveryScopeDO) invocation.getArgument(0)).setId(ids.getAndIncrement());
            return 1;
        }).when(scopeMapper).insert(any(DeliveryScopeDO.class));

        SplitScopeApplyResult result = service.applySplit(command("2", List.of()));

        assertTrue(result.valid());
        assertEquals(5L, result.scopeVersion());
        InOrder ownerOrder = inOrder(projectOfficeFactApi);
        ownerOrder.verify(projectOfficeFactApi).lockAndRevalidate(argThat(query ->
                query.projectId().equals(100L) && query.expectedProjectVersion().equals(3)));
        ownerOrder.verify(projectOfficeFactApi).lockAndRevalidate(argThat(query ->
                query.projectId().equals(101L) && query.expectedProjectVersion().equals(0)));

        ArgumentCaptor<DeliveryScopeDO> scopeCaptor = ArgumentCaptor.forClass(DeliveryScopeDO.class);
        verify(scopeMapper, times(2)).insert(scopeCaptor.capture());
        DeliveryScopeDO remainder = scopeCaptor.getAllValues().get(0);
        DeliveryScopeDO child = scopeCaptor.getAllValues().get(1);
        assertEquals(100L, remainder.getProjectId());
        assertEquals("PARENT", remainder.getProjectCode());
        assertEquals(new BigDecimal("3"), remainder.getAllocatedQty());
        assertEquals(101L, child.getProjectId());
        assertEquals("CHILD", child.getProjectCode());

        ArgumentCaptor<DeliveryScopeDetailDO> detailCaptor = ArgumentCaptor.forClass(DeliveryScopeDetailDO.class);
        verify(detailMapper, times(2)).insert(detailCaptor.capture());
        assertTrue(detailCaptor.getAllValues().stream()
                .allMatch(detail -> "ERP-PRODUCT-1".equals(detail.getProductCode())
                        && detail.getSerialNo() == null && detail.getDetailSequence() == 1));
        assertEquals(new BigDecimal("3"), detailCaptor.getAllValues().get(0).getAllocatedQty());
        assertEquals(new BigDecimal("2"), detailCaptor.getAllValues().get(1).getAllocatedQty());
    }

    @Test
    void shouldRejectBlankProductCodeBeforeScopeHistoryOrOutboxWrites() {
        stubProjectFacts();
        when(orderLineMapper.selectByIdsForUpdate(any())).thenReturn(List.of(line(" ")));
        when(scopeMapper.selectActiveByProjectIdForUpdate(any())).thenReturn(List.of(parentScope("5", 4L)));

        SplitScopeApplyResult result = service.applySplit(command("2", List.of()));

        assertFalse(result.valid());
        assertTrue(result.errors().contains("ERP_PRODUCT_CODE_REQUIRED:10"));
        verify(scopeMapper, never()).updateById(any(DeliveryScopeDO.class));
        verify(scopeMapper, never()).insert(any(DeliveryScopeDO.class));
        verifyNoInteractions(detailMapper);
        verify(outboxMapper, never()).insert(any(CommerceOutboxEventDO.class));
    }

    @Test
    void shouldRevalidateSerialsAgainstTargetProjectAndPersistSerialSubjects() {
        stubProjectFacts();
        when(assetDeviceScopeApi.validateAssignableSerials(1L, 101L, List.of("SN-1", "SN-2")))
                .thenReturn(new SerialScopeValidationResult(true, List.of(), List.of(), List.of()));
        when(orderLineMapper.selectByIdsForUpdate(any())).thenReturn(List.of(line(null)));
        when(scopeMapper.selectActiveByProjectIdForUpdate(any())).thenReturn(List.of(parentScope("2", 4L)));
        doAnswer(invocation -> { ((DeliveryScopeDO) invocation.getArgument(0)).setId(500L); return 1; })
                .when(scopeMapper).insert(any(DeliveryScopeDO.class));

        SplitScopeApplyResult result = service.applySplit(command("2", List.of("SN-1", "SN-2")));

        assertTrue(result.valid());
        verify(assetDeviceScopeApi).validateAssignableSerials(1L, 101L, List.of("SN-1", "SN-2"));
        ArgumentCaptor<DeliveryScopeDetailDO> captor = ArgumentCaptor.forClass(DeliveryScopeDetailDO.class);
        verify(detailMapper, times(2)).insert(captor.capture());
        assertEquals(List.of("SN-1", "SN-2"), captor.getAllValues().stream()
                .map(DeliveryScopeDetailDO::getSerialNo).toList());
        assertTrue(captor.getAllValues().stream().allMatch(detail -> detail.getProductCode() == null
                && detail.getAllocatedQty().compareTo(BigDecimal.ONE) == 0));
    }

    @Test
    void shouldRejectSerialPartialSplitWhenRemainderHasNoOwnerProductCode() {
        stubProjectFacts();
        when(assetDeviceScopeApi.validateAssignableSerials(1L, 101L, List.of("SN-1", "SN-2")))
                .thenReturn(new SerialScopeValidationResult(true, List.of(), List.of(), List.of()));
        when(orderLineMapper.selectByIdsForUpdate(any())).thenReturn(List.of(line(null)));
        when(scopeMapper.selectActiveByProjectIdForUpdate(any())).thenReturn(List.of(parentScope("5", 4L)));

        SplitScopeApplyResult result = service.applySplit(command("2", List.of("SN-1", "SN-2")));

        assertFalse(result.valid());
        assertTrue(result.errors().contains("ERP_PRODUCT_CODE_REQUIRED_FOR_REMAINDER:10"));
        verify(scopeMapper, never()).updateById(any(DeliveryScopeDO.class));
        verify(scopeMapper, never()).insert(any(DeliveryScopeDO.class));
        verifyNoInteractions(detailMapper);
        verify(outboxMapper, never()).insert(any(CommerceOutboxEventDO.class));
    }

    @Test
    void shouldRejectWrongOwnerIdentityBeforeLockingCommerceFacts() {
        when(projectOfficeFactApi.lockAndRevalidate(any())).thenReturn(
                fact(999L, 3, "WRONG"));

        SplitScopeApplyResult result = service.applySplit(command("2", List.of()));

        assertFalse(result.valid());
        assertTrue(result.errors().stream().allMatch(error -> error.startsWith("PROJECT_OFFICE_FACT_INVALID:")));
        verifyNoInteractions(orderLineMapper, scopeMapper, detailMapper);
        verify(outboxMapper, never()).insert(any(CommerceOutboxEventDO.class));
    }

    private void stubProjectFacts() {
        when(projectOfficeFactApi.lockAndRevalidate(any())).thenAnswer(invocation -> {
            var query = invocation.<cn.iocoder.yudao.module.pms.project.api.commerce.dto.ProjectOfficeFactQuery>
                    getArgument(0);
            return fact(query.projectId(), query.expectedProjectVersion(),
                    query.projectId().equals(100L) ? "PARENT" : "CHILD");
        });
    }

    private ProjectOfficeFact fact(Long projectId, Integer version, String projectCode) {
        return new ProjectOfficeFact(ProjectFactOutcome.FOUND, projectId, version, projectCode,
                projectId + 1000, "OFF-" + projectId, "办事处" + projectId, 7);
    }

    private SplitScopeApplyCommand command(String quantity, List<String> serials) {
        return new SplitScopeApplyCommand(1L, 100L, 3, 4L, "idem",
                Map.of("A", 101L), Map.of("A", 0), List.of(allocation("A", quantity, serials)));
    }

    private SplitScopeApplyCommand.Allocation allocation(String key, String quantity, List<String> serials) {
        return new SplitScopeApplyCommand.Allocation(key, 10L, new BigDecimal(quantity), "IGNORED", serials);
    }

    private SalesOrderLineDO line(String productCode) {
        SalesOrderLineDO line = new SalesOrderLineDO();
        line.setId(10L);
        line.setTenantId(1L);
        line.setSourceSystem("ERP");
        line.setSourceVersion("v1");
        line.setCompanyCode("C01");
        line.setCompanyName("公司");
        line.setOrderType("NORMAL");
        line.setOrderNo("SO-1");
        line.setLineNo("10");
        line.setItemCode("ITEM-1");
        line.setItemDesc("设备");
        line.setProductCode(productCode);
        line.setOrderQty(new BigDecimal("5"));
        line.setUnitCode("EA");
        line.setUnitScale(0);
        line.setQuantityStatus("CONFIRMED");
        return line;
    }

    private DeliveryScopeDO parentScope(String quantity, long version) {
        DeliveryScopeDO scope = new DeliveryScopeDO();
        scope.setId(400L);
        scope.setTenantId(1L);
        scope.setProjectId(100L);
        scope.setOrderLineId(10L);
        scope.setAllocatedQty(new BigDecimal(quantity));
        scope.setAllocationVersion(version);
        scope.setScopeStatus("ACTIVE");
        return scope;
    }
}
