package cn.iocoder.yudao.module.pms.commerce.service.migration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.migration.ContractCollectionPlanMigrationApi.*;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.associated.ContractCollectionPlanDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.ContractCollectionPlanMapper;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ContractCollectionPlanMigrationServiceTest {
    ContractCollectionPlanMapper mapper = mock(ContractCollectionPlanMapper.class);
    AdminUserApi users = mock(AdminUserApi.class);
    ContractCollectionPlanMigrationService service = new ContractCollectionPlanMigrationService(mapper,users);
    LocalDateTime created = LocalDateTime.parse("2018-06-07T08:09:10.123");
    LocalDateTime updated = created.plusYears(1).plusNanos(333_000_000);
    @BeforeEach void setup() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }
    private Plan source(String item) {
        return new Plan(null,1L,null,"C1","1",item,"终验",null,30,created.plusDays(1),"销售反馈","CRM",
                created,null,"11",created,"crmapp",updated,false);
    }
    private Request request(Entry... entries) { return new Request(1L,1L,"integration:1:123",List.of(entries),false); }
    private Entry fresh(String key) { return new Entry(key,null,null,null,source("终验款")); }

    @Test void importsCompleteHistoryAndKeepsDuplicateBusinessRowsDistinct() {
        when(mapper.selectExactContractIds(any())).thenReturn(List.of(20L));
        when(mapper.insertPlan(any())).thenReturn(1);
        var result = service.apply(request(fresh("10"),fresh("11")));
        var rows = ArgumentCaptor.forClass(ContractCollectionPlanDO.class);
        verify(mapper,times(2)).insertPlan(rows.capture());
        assertNotEquals(rows.getAllValues().get(0).getId(),rows.getAllValues().get(1).getId());
        for (var row : rows.getAllValues()) {
            assertEquals(created,row.getCreateTime()); assertEquals(updated,row.getUpdateTime());
            assertEquals("crmapp",row.getUpdater()); assertEquals(20L,row.getContractId());
            assertEquals("终验款",row.getCollectionItemName()); assertEquals(30,row.getAfterDays());
            assertNull(row.getEventPlannedAt()); assertEquals(created.plusDays(1),row.getEventActualFinishedAt());
        }
        assertTrue(result.stream().allMatch(r -> "CREATED".equals(r.action())));
    }
    @Test void ambiguousContractStillCarriesWholePlanWithoutAttachingAProject() {
        when(mapper.selectExactContractIds(any())).thenReturn(List.of(20L,21L));
        when(mapper.insertPlan(any())).thenReturn(1);
        var result = service.apply(request(fresh("10"))).getFirst();
        assertEquals("CREATED",result.action()); assertNull(result.after().contractId());
        assertEquals("C1",result.after().contractNo()); assertEquals("销售反馈",result.after().marketingFeedback());
        assertTrue(result.message().contains("待关联"));
    }
    @Test void previewHasNoBusinessWritesAndNoSyntheticFormalId() {
        when(mapper.selectExactContractIds(any())).thenReturn(List.of(20L));
        var result = service.preview(request(fresh("10"))).getFirst();
        assertEquals("CREATED",result.action()); assertNull(result.after().id());
        verify(mapper,never()).insertPlan(any()); verify(mapper,never()).updatePlan(any());
    }
    @Test void invalidTenantAndDuplicateSourceOrTargetFailBeforeWriting() {
        assertThrows(IllegalArgumentException.class,()->service.apply(new Request(2L,1L,"integration:2:123",List.of(fresh("10")),false)));
        assertThrows(IllegalArgumentException.class,()->service.apply(request(fresh("10"),fresh("10"))));
        assertThrows(IllegalArgumentException.class,()->service.apply(request(new Entry("1",20L,null,null,source("终验款")),new Entry("2",20L,null,null,source("终验款")))));
        verify(mapper,never()).insertPlan(any()); verify(mapper,never()).updatePlan(any());
    }
    @Test void missingOrDeletedEstablishedTargetCannotBeRecreatedOrRevived() {
        var missing = service.apply(request(new Entry("10",40L,null,null,source("终验款")))).getFirst();
        assertEquals("ISSUE",missing.action());
        var deleted = entity(source("终验款")); deleted.setId(40L); deleted.setDeleted(true);
        when(mapper.selectIdentityForUpdate(any())).thenReturn(deleted);
        assertEquals("ISSUE",service.apply(request(new Entry("10",40L,null,null,source("终验款")))).getFirst().action());
        verify(mapper,never()).insertPlan(any()); verify(mapper,never()).updatePlan(any());
    }
    @Test void explicitAdoptionCorrectsHistoricalAuditAndPreservesFormalId() {
        var row = entity(source("终验款")); row.setId(40L); row.setContractId(20L);
        row.setCreateTime(created.plusYears(8)); row.setUpdateTime(updated.plusYears(8)); row.setCreator("migration");
        when(mapper.selectIdentityForUpdate(any())).thenReturn(row);
        when(mapper.selectExactContractIds(any())).thenReturn(List.of(20L));
        when(mapper.updatePlan(any())).thenReturn(1);
        var result = service.apply(new Request(1L,1L,"integration:1:123",List.of(new Entry("10",null,40L,null,source("终验款"))),true)).getFirst();
        assertEquals("UPDATED",result.action()); assertEquals(40L,result.after().id());
        assertEquals(created,result.after().createTime()); assertEquals(updated,result.after().updateTime());
        verify(mapper,never()).insertPlan(any());
    }
    @Test void sourceIdentityChangesAreBlockedEvenWhenAdoptionWasApproved() {
        var row = entity(source("初验款")); row.setId(40L);
        when(mapper.selectIdentityForUpdate(any())).thenReturn(row);
        var result = service.apply(new Request(1L,1L,"integration:1:123",List.of(new Entry("10",null,40L,null,source("终验款"))),true)).getFirst();
        assertEquals("ISSUE",result.action());
        verify(mapper,never()).insertPlan(any()); verify(mapper,never()).updatePlan(any());
    }
    @Test void failedWriteRaisesConflictSoBindingTransactionCannotCommit() {
        when(mapper.selectExactContractIds(any())).thenReturn(List.of(20L));
        when(mapper.insertPlan(any())).thenReturn(0);
        assertThrows(IllegalStateException.class,()->service.apply(request(fresh("10"))));
    }
    private ContractCollectionPlanDO entity(Plan plan) {
        var row = new ContractCollectionPlanDO(); row.setTenantId(plan.tenantId());row.setContractNo(plan.contractNo());
        row.setBatchCode(plan.batchCode());row.setCollectionItemName(plan.collectionItemName());row.setReferenceEventName(plan.referenceEventName());
        row.setEventPlannedAt(plan.eventPlannedAt());row.setAfterDays(plan.afterDays());row.setEventActualFinishedAt(plan.eventActualFinishedAt());
        row.setMarketingFeedback(plan.marketingFeedback());row.setDataSource(plan.dataSource());row.setEffectiveFrom(plan.effectiveFrom());
        row.setEffectiveTo(plan.effectiveTo());row.setCreator(plan.creator());row.setCreateTime(plan.createTime());
        row.setUpdater(plan.updater());row.setUpdateTime(plan.updateTime());row.setDeleted(plan.deleted());return row;
    }
}
