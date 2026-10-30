package cn.iocoder.yudao.module.pms.commerce.service.sync;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.executionorder.CrmExecutionOrderDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.executionorder.CrmExecutionOrderMapper;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.Batch;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.Row;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DppmsExecutionOrderSyncAdapterTest {
    private final CrmExecutionOrderMapper mapper = mock(CrmExecutionOrderMapper.class);
    private final DppmsExecutionOrderSyncAdapter adapter = new DppmsExecutionOrderSyncAdapter(mapper);

    @BeforeEach void setup() { TenantContextHolder.setTenantId(1L); when(mapper.selectIncoming(any())).thenReturn(List.of()); }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }

    @Test void previewsARegularHeaderWithoutWriting() {
        var change = adapter.preview(batch(row("1", "项目一"))).getFirst();
        assertEquals("CREATED", change.action());
        assertEquals("CRM|EX-1", change.after().get("_businessKey"));
        verify(mapper, never()).insert(any(CrmExecutionOrderDO.class));
        verify(mapper, never()).updateById(any(CrmExecutionOrderDO.class));
    }

    @Test void latestSourceRevisionWinsForDuplicateBusinessKey() {
        var changes = adapter.preview(batch(row("1", "项目一"), row("2", "项目二")));
        assertEquals(List.of("CREATED", "CREATED"), changes.stream().map(change -> change.action()).toList());
        assertTrue(changes.stream().noneMatch(change -> "CONFLICT".equals(change.action())));
    }

    @Test void malformedMoneyBecomesAnIssueInsteadOfAWrite() {
        Map<String,Object> fields = new LinkedHashMap<>(row("1", "项目一").fields());
        fields.put("projectAmount", "not-money");
        var change = adapter.preview(batch(new Row("EXECUTION_ORDER", "1", fields, null))).getFirst();
        assertEquals("ISSUE", change.action());
        assertTrue(change.message().contains("projectAmount"));
    }

    @Test void batchesDistinctTargetsAndReturnsGeneratedIdsForEveryRevision() {
        doAnswer(invocation -> {
            Collection<CrmExecutionOrderDO> rows = invocation.getArgument(0);
            assertEquals(1, rows.size());
            var target = rows.iterator().next();
            assertEquals("项目二", target.getProjectName());
            target.setId(42L);
            return true;
        }).when(mapper).insertBatch(anyCollection(), eq(1000));
        var changes = adapter.apply(batch(row("1", "项目一"), row("2", "项目二")));
        assertEquals(List.of(42L, 42L), changes.stream().map(c -> c.targetId()).toList());
        verify(mapper, never()).insert(any(CrmExecutionOrderDO.class));
        verify(mapper, never()).updateById(any(CrmExecutionOrderDO.class));
    }

    @Test void decimalScaleAloneDoesNotRewriteAnUnchangedTarget() {
        var old = new CrmExecutionOrderDO();
        old.setId(42L); old.setSourceSystem("CRM"); old.setExecutionNo("EX-1");
        old.setProjectAmount(new java.math.BigDecimal("9000.000000"));
        when(mapper.selectIncoming(any())).thenReturn(List.of(old));
        var source = new Row("EXECUTION_ORDER", "1", Map.of("executionNo", "EX-1",
                "sourceSystem", "CRM", "projectAmount", "9000"), null);
        var change = adapter.apply(batch(source)).getFirst();
        assertEquals("UNCHANGED", change.action());
        assertEquals(42L, change.targetId());
        verify(mapper, never()).updateBatch(anyCollection(), anyInt());
        verify(mapper, never()).insertBatch(anyCollection(), anyInt());
        verify(mapper).selectIncoming(argThat(q -> q.tenantId() == 1L
                && "CRM".equals(q.sourceSystem()) && q.executionNumbers().equals(List.of("EX-1"))));
    }

    @Test void deletedTargetPreventsTheWholeBatchFromWriting() {
        var old = new CrmExecutionOrderDO();
        old.setId(42L); old.setSourceSystem("CRM"); old.setExecutionNo("EX-1"); old.setDeleted(true);
        when(mapper.selectIncoming(any())).thenReturn(List.of(old));
        assertEquals("CONFLICT", adapter.apply(batch(row("1", "项目一"))).getFirst().action());
        verify(mapper, never()).updateBatch(anyCollection(), anyInt());
        verify(mapper, never()).insertBatch(anyCollection(), anyInt());
    }

    @Test void batchesUpdatesWithTheirExistingIdentityAndVersion() {
        var old = new CrmExecutionOrderDO();
        old.setId(42L); old.setVersion(7L); old.setSourceSystem("CRM"); old.setExecutionNo("EX-1");
        old.setProjectName("旧名称");
        when(mapper.selectIncoming(any())).thenReturn(List.of(old));
        doAnswer(invocation -> {
            Collection<CrmExecutionOrderDO> rows = invocation.getArgument(0);
            assertEquals(1, rows.size());
            var target = rows.iterator().next();
            assertEquals(42L, target.getId()); assertEquals(7, target.getVersion());
            assertEquals("新名称", target.getProjectName());
            return true;
        }).when(mapper).updateBatch(anyCollection(), eq(1000));
        var change = adapter.apply(batch(row("1", "新名称"))).getFirst();
        assertEquals("UPDATED", change.action()); assertEquals(42L, change.targetId());
        verify(mapper).updateBatch(anyCollection(), eq(1000));
        verify(mapper, never()).updateById(any(CrmExecutionOrderDO.class));
    }

    @Test void batchFailurePropagatesToTheRunTransaction() {
        doThrow(new IllegalStateException("batch failed"))
                .when(mapper).insertBatch(anyCollection(), eq(1000));
        assertThrows(IllegalStateException.class, () -> adapter.apply(batch(row("1", "项目一"))));
    }

    private static Row row(String id, String projectName) {
        return new Row("EXECUTION_ORDER", id, Map.of("executionNo", "EX-1", "sourceSystem", "CRM",
                "projectCode", "P-1", "projectName", projectName, "engineeringFeeRaw", "1,200.50",
                "projectAmount", "9000", "submitTime", "2026-09-20 10:20:30"), null);
    }
    private static Batch batch(Row... rows) {
        return new Batch("test", List.of(rows), List.of(), true, "RETAIN", false, "UPSERT", false);
    }
}
