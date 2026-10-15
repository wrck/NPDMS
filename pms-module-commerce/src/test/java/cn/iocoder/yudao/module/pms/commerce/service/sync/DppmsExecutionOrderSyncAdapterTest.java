package cn.iocoder.yudao.module.pms.commerce.service.sync;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.executionorder.CrmExecutionOrderDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.executionorder.CrmExecutionOrderMapper;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.Batch;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.Row;
import cn.iocoder.yudao.module.pms.commerce.service.migration.DppmsExecutionOrganizationReferences;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DppmsExecutionOrderSyncAdapterTest {
    private final CrmExecutionOrderMapper mapper = mock(CrmExecutionOrderMapper.class);
    private final DppmsExecutionOrganizationReferences organizations=mock(DppmsExecutionOrganizationReferences.class);
    private final DppmsExecutionOrderSyncAdapter adapter = new DppmsExecutionOrderSyncAdapter(mapper,organizations);

    @BeforeEach void setup() { TenantContextHolder.setTenantId(1L); when(mapper.selectIncoming(any())).thenReturn(List.of());
        when(organizations.load()).thenReturn(new DppmsExecutionOrganizationReferences.Directory(Map.of(),Map.of())); }
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
    @Test void missingLocalOrganizationsDoNotDiscardIndependentExecutionOrders() {
        var fields=new LinkedHashMap<>(row("1","独立执行单").fields());
        fields.put("companyCode","001");fields.put("departmentCode","311202");
        doAnswer(call->{
            Collection<CrmExecutionOrderDO> created=call.getArgument(0);
            var value=created.iterator().next();
            assertNull(value.getCompanyId());assertNull(value.getDepartmentId());
            assertEquals("001",value.getCompanyCode());assertEquals("162102",value.getDepartmentCode());
            value.setId(42L);return true;
        }).when(mapper).insertBatch(anyCollection(),eq(1000));
        var result=adapter.apply(batch(new Row("EXECUTION_ORDER","1",fields,null)));
        assertEquals("CREATED",result.getFirst().action());assertEquals(42L,result.getFirst().targetId());
    }

    @Test void malformedMoneyBecomesAnIssueInsteadOfAWrite() {
        Map<String,Object> fields = new LinkedHashMap<>(row("1", "项目一").fields());
        fields.put("projectAmount", "not-money");
        var change = adapter.preview(batch(new Row("EXECUTION_ORDER", "1", fields, null))).getFirst();
        assertEquals("ISSUE", change.action());
        assertTrue(change.message().contains("projectAmount"));
    }

    @Test void mixedGoodAndInvalidRowsFailTheChunkInsteadOfReportingUnwrittenCreates() {
        var bad=new LinkedHashMap<>(row("2","错误项目").fields());bad.put("projectAmount","bad");
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(row("1","正常项目"),
                new Row("EXECUTION_ORDER","2",bad,null))));
        verify(mapper,never()).insertBatch(anyCollection(),anyInt());
        verify(mapper,never()).updateBatch(anyCollection(),anyInt());
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
        old.setStatus("DISABLED");old.setAfEvidenceStatus("CONFIRMED");old.setPrimaryProjectId(88L);
        when(mapper.selectIncoming(any())).thenReturn(List.of(old));
        doAnswer(invocation -> {
            Collection<CrmExecutionOrderDO> rows = invocation.getArgument(0);
            assertEquals(1, rows.size());
            var target = rows.iterator().next();
            assertEquals(42L, target.getId()); assertEquals(7, target.getVersion());
            assertEquals("新名称", target.getProjectName());
            assertEquals("DISABLED",target.getStatus());assertEquals("CONFIRMED",target.getAfEvidenceStatus());
            assertEquals(88L,target.getPrimaryProjectId());
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

    @Test void originalBlankAndWhitespaceReachFormalFields() {
        var fields=new LinkedHashMap<>(row("1","  历史项目\n").fields());
        fields.put("channelName","");fields.put("customerProjectName","");
        doAnswer(call -> {
            Collection<CrmExecutionOrderDO> rows=call.getArgument(0);
            var actual=rows.iterator().next();assertEquals("",actual.getChannelName());
            assertEquals("",actual.getCustomerProjectName());assertEquals("  历史项目\n",actual.getProjectName());
            actual.setId(42L);return true;
        }).when(mapper).insertBatch(anyCollection(),eq(1000));
        assertEquals("CREATED",adapter.apply(batch(new Row("EXECUTION_ORDER","1",fields,null))).getFirst().action());
    }

    @Test void falseBatchResultMustRollBackInsteadOfReportingUpdated() {
        var old=new CrmExecutionOrderDO();old.setId(42L);old.setSourceSystem("CRM");old.setExecutionNo("EX-1");
        when(mapper.selectIncoming(any())).thenReturn(List.of(old));
        when(mapper.updateBatch(anyCollection(),eq(1000))).thenReturn(false);
        assertThrows(IllegalStateException.class,()->adapter.apply(batch(row("1","新名称"))));
    }

    @Test void unknownCompanyAndOverPrecisionMoneyNeverWrite() {
        var fields=new LinkedHashMap<>(row("1","项目").fields());fields.put("companyCode","99");
        assertEquals("ISSUE",adapter.preview(batch(new Row("EXECUTION_ORDER","1",fields,null))).getFirst().action());
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(new Row("EXECUTION_ORDER","1",fields,null))));
        fields.remove("companyCode");fields.put("engineeringFeeRaw","0.12345");
        assertEquals("ISSUE",adapter.preview(batch(new Row("EXECUTION_ORDER","1",fields,null))).getFirst().action());
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(new Row("EXECUTION_ORDER","1",fields,null))));
        verify(mapper,never()).insertBatch(anyCollection(),anyInt());
        verify(mapper,never()).updateBatch(anyCollection(),anyInt());
    }
    @Test void wholeTableLatestRevisionIsStableAcrossSeparateChunksAndReplays() {
        var current=new CrmExecutionOrderDO();current.setId(42L);current.setVersion(7L);
        current.setSourceSystem("CRM");current.setExecutionNo("EX-1");current.setProjectCode("P-1");current.setProjectName("最新项目");
        current.setStatus("DISABLED");current.setPrimaryProjectId(88L);current.setAfEvidenceStatus("CONFIRMED");
        when(mapper.selectIncoming(any())).thenReturn(List.of(current));
        for(String id:List.of("1","2","1","2")) {
            var fields=canonicalFields(id,"1".equals(id)?"早期项目":"最新项目");
            var change=adapter.apply(batch(new Row("EXECUTION_ORDER",id,fields,42L))).getFirst();
            assertEquals("UNCHANGED",change.action());assertEquals(42L,change.targetId());
            assertEquals(fields.get("projectName"),change.after().get("projectName"));
            assertEquals("最新项目",change.after().get("canonical_projectName"));
        }
        assertEquals(7L,current.getVersion());assertEquals("DISABLED",current.getStatus());assertEquals(88L,current.getPrimaryProjectId());
        verify(mapper,never()).updateBatch(anyCollection(),anyInt());verify(mapper,never()).insertBatch(anyCollection(),anyInt());
    }
    @Test void wholeTableSelectionCannotSubstituteAnotherExecutionIdentity() {
        var fields=canonicalFields("1","早期项目");fields.put("canonical_executionNo","FOREIGN-EXECUTION");
        var change=adapter.preview(batch(new Row("EXECUTION_ORDER","1",fields,null))).getFirst();
        assertEquals("ISSUE",change.action());assertTrue(change.message().contains("修订身份"));
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(new Row("EXECUTION_ORDER","1",fields,null))));
        verify(mapper,never()).updateBatch(anyCollection(),anyInt());verify(mapper,never()).insertBatch(anyCollection(),anyInt());
    }
    private Map<String,Object> canonicalFields(String id,String originalName) {
        var fields=new LinkedHashMap<String,Object>();
        for(var field:adapter.descriptor().objects().getFirst().fields()) {
            if(field.name().startsWith("canonical"))continue;
            fields.put(field.name(),null);fields.put("canonical_"+field.name(),null);
        }
        fields.putAll(Map.of("sourceSystem","CRM","executionNo","EX-1","projectCode","P-1","projectName",originalName,
                "canonical_sourceSystem","CRM","canonical_executionNo","EX-1","canonical_projectCode","P-1",
                "canonical_projectName","最新项目","canonicalSourceId",2));
        return fields;
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
