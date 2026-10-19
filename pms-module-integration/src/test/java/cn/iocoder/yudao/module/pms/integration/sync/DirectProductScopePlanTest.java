package cn.iocoder.yudao.module.pms.integration.sync;
import cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsProjectProductScopeApi;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsProjectProductScopeApi.*;
import cn.iocoder.yudao.module.pms.project.api.migration.ProjectScopeMigrationContextApi;
import org.junit.jupiter.api.*;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class DirectProductScopePlanTest {
    ProjectScopeMigrationContextApi projects=mock(ProjectScopeMigrationContextApi.class);
    DirectProductScopeMapper mapper=mock(DirectProductScopeMapper.class);
    DirectScopeLookupMapper scopes=mock(DirectScopeLookupMapper.class);
    DirectProductScopeVersions versions=mock(DirectProductScopeVersions.class);
    cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi access=mock(cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi.class);
    javax.sql.DataSource dataSource=new org.springframework.jdbc.datasource.DriverManagerDataSource("jdbc:h2:mem:p22-plan-"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
    org.springframework.transaction.support.TransactionTemplate tx=new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource));
    DirectProductScopePersistence service=new DirectProductScopePersistence(dataSource,projects,mapper,scopes,versions);
    LocalDateTime history=LocalDateTime.of(2020,1,2,3,4,5,321000000);
    DirectScopeOrderLineRow line=new DirectScopeOrderLineRow();
    Map<Long,List<DirectScopeDetailRow>> savedDetails=new HashMap<>();
    @BeforeEach void setup() {
        tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        TenantContextHolder.setTenantId(1L);
        var context=new ProjectScopeMigrationContextApi.Context(100L,3L,"P","Project","001","Company","C","Customer","E","Manager",20L,"162007","Office",7);
        when(projects.resolve(any())).thenReturn(context);when(projects.lockAndRevalidate(any())).thenReturn(context);
        line.setId(40L);line.setTenantId(1L);line.setSourceSystem("D365");line.setCompanyCode("001");line.setOrderType("0");line.setOrderQty(new BigDecimal("4"));
        when(mapper.selectAssociatedLines(any())).thenReturn(List.of(line));when(mapper.lockAssociatedLines(any())).thenReturn(List.of(line));
        when(mapper.selectIdentity(any())).thenReturn(List.of());when(mapper.lockIdentity(any())).thenReturn(List.of());
        when(mapper.insertHistoricalScope(any())).thenReturn(1);
        when(mapper.selectProductDetails(any())).thenAnswer(call->savedDetails.getOrDefault(call.getArgument(0,DirectProductScopeMapper.ProductDetailQuery.class).deliveryScopeId(),List.of()));
        when(mapper.selectProductDetailsForUpdate(any())).thenAnswer(call->savedDetails.getOrDefault(call.getArgument(0,DirectProductScopeMapper.ProductDetailQuery.class).deliveryScopeId(),List.of()));
        when(mapper.insertHistoricalProductDetail(any())).thenAnswer(call->{
            DirectScopeDetailRow input=call.getArgument(0);var saved=new DirectScopeDetailRow();
            org.springframework.beans.BeanUtils.copyProperties(input,saved);savedDetails.put(saved.getDeliveryScopeId(),List.of(saved));return 1;
        });
    }
    @AfterEach void cleanup() {TenantContextHolder.clear();}
    Entry entry(String id,String split) {
        Map<String,String> raw=new LinkedHashMap<>();List.of("id","projectId","contractNo","itemCode","itemName","projectQuantity","orderQuantity","deliverQuantity","openQuantity","orderNumber","lineNum").forEach(k->raw.put(k,null));
        raw.putAll(Map.of("id",id,"projectId","101","contractNo","C","itemCode","I","itemName","Historical name","orderQuantity","4","deliverQuantity","3","openQuantity","1","orderNumber","O","lineNum","1"));
        raw.put("projectQuantity",split);return new Entry(id,raw,new Baseline(Long.parseLong(id)+1000,"creator",history,"updater",history));
    }
    Command command(Entry... entries) {return new Command(1L,1L,100L,3L,"101",List.of(entries));}
    @Test void syncConnectionUsesTheExistingOfficeOwnerAndVersionGuard() {
        var original=command(entry("1",null));
        preview(new Command(original.tenantId(),original.actorId(),original.projectId(),original.expectedProjectVersion(),
                original.sourceProjectKey(),original.entries(),18L));
        verify(projects).resolve(argThat(query->query.sourceConnectionId().equals(18L) && query.expectedProjectVersion()==3L
                && query.projectId()==100L && "101".equals(query.sourceProjectKey())));
        verify(mapper,never()).insertHistoricalScope(any());
    }
    @Test void previewGeneratesFormalFieldsWithoutWritingOrConsumingCapacity() {
        var result=preview(command(entry("1",null))).getFirst();
        assertEquals(1001L,result.targetId());assertEquals(new BigDecimal("4"),result.fields().get("allocatedQty"));
        assertEquals(false,result.fields().get("allocationConfirmed"));assertEquals("ACTIVE",result.fields().get("scopeStatus"));
        assertEquals(1L,result.fields().get("allocationVersion"));assertNotNull(result.fields().get("effectiveFrom"));
        assertEquals(history,result.fields().get("createTime"));assertEquals("creator",result.fields().get("creator"));
        assertEquals("Historical name",result.fields().get("productDesc"));assertEquals(20L,result.fields().get("departmentId"));
        verify(mapper,never()).insertHistoricalScope(any());
        assertEquals("CREATED",result.fields().get("productDetailAction"));
        var detail=(Map<?,?>)result.fields().get("productDetail");assertEquals(21,detail.size());
        assertEquals(1001L,detail.get("deliveryScopeId"));assertEquals("I",detail.get("productCode"));
        assertEquals(new BigDecimal("4"),detail.get("allocatedQty"));assertNull(detail.get("serialNo"));
        verify(mapper,never()).insertHistoricalProductDetail(any());
    }
    @Test void appliesExplicitSplitAndKeepsHistoricalDeliveryFiguresAndAudit() {
        apply(command(entry("1","2")));
        verify(mapper).insertHistoricalScope(argThat(r->r.getId()==1001L && r.getAllocatedQty().compareTo(new BigDecimal("2"))==0
                && r.getDeliveredQuantity().compareTo(new BigDecimal("3"))==0 && r.getOpenQuantity().compareTo(BigDecimal.ONE)==0
                && Boolean.FALSE.equals(r.getAllocationConfirmed()) && history.equals(r.getUpdateTime()) && r.getEffectiveTo()==null));
        verify(projects).lockAndRevalidate(any());
        verify(mapper).insertHistoricalProductDetail(argThat(d->d.getDeliveryScopeId()==1001L && d.getSerialNo()==null
                && "I".equals(d.getProductCode()) && "Historical name".equals(d.getProductName())
                && d.getAllocatedQty().compareTo(new BigDecimal("2"))==0 && !history.equals(d.getCreateTime())));
    }
    @Test void entireBatchValidationPreventsPartialWritesForZeroDuplicateAndChangedOrder() {
        assertThrows(RuntimeException.class,()->apply(command(entry("1",null),entry("2","0"))));
        assertThrows(RuntimeException.class,()->apply(command(entry("1",null),entry("1",null))));
        line.setOrderQty(new BigDecimal("5"));assertThrows(RuntimeException.class,()->apply(command(entry("1",null))));
        verify(mapper,never()).insertHistoricalScope(any());
        verify(mapper,never()).insertHistoricalProductDetail(any());
    }
    @Test void distinctHistoricalRowsMayShareOrderLineAndAllocationVersionWithoutConsumingCapacity() {
        var result=apply(command(entry("1",null),entry("2",null)));
        assertEquals(List.of(1001L,1002L),result.stream().map(Result::targetId).toList());
        assertTrue(result.stream().allMatch(r->Boolean.FALSE.equals(r.fields().get("allocationConfirmed"))
                && Long.valueOf(1L).equals(r.fields().get("allocationVersion"))));
        verify(mapper,times(2)).insertHistoricalScope(any());
        
    }
    @Test void ambiguousLineAndExistingIdAreNeverGuessedOrOverwritten() {
        when(mapper.lockAssociatedLines(any())).thenReturn(List.of(line,line));
        assertThrows(RuntimeException.class,()->apply(command(entry("1",null))));
        when(mapper.lockAssociatedLines(any())).thenReturn(List.of(line));when(scopes.selectById(new DirectScopeLookupMapper.Identity(1L,1001L))).thenReturn(new DirectDeliveryScopeRow());
        assertThrows(RuntimeException.class,()->apply(command(entry("1",null))));verify(mapper,never()).insertHistoricalScope(any());
    }
    @Test void previewCollectsMissingAmbiguousAndInvalidQuantityWhileApplyRemainsAtomic() {
        when(mapper.selectAssociatedLines(any())).thenReturn(List.of(),List.of(line,line),List.of(line),List.of(line));
        var result=preview(command(entry("1",null),entry("2",null),entry("3","0"),entry("4","2")));
        assertEquals(List.of("CONFLICT","CONFLICT","CONFLICT","CREATED"),result.stream().map(Result::action).toList());
        assertEquals("DPPMS_PRODUCT_SCOPE_ASSOCIATED_ORDER_LINE_MISSING",result.getFirst().message());
        assertEquals("DPPMS_PRODUCT_SCOPE_POSITIVE_QUANTITY_REQUIRED",result.get(2).message());
        when(mapper.lockAssociatedLines(any())).thenReturn(List.of(line),List.of());
        assertThrows(IllegalArgumentException.class,()->apply(command(entry("1",null),entry("2",null))));
        verify(mapper,never()).insertHistoricalScope(any());verify(mapper,never()).insertHistoricalProductDetail(any());
    }
    @Test void onlyMissingOfficeIsCollectedAndAuthorizationErrorsStillPropagate() {
        when(projects.resolve(any())).thenThrow(new IllegalArgumentException("DPPMS_SCOPE_PROJECT_OFFICE_UNRESOLVED"));
        var raw=entry("1",null);var noBaseline=new Entry(raw.sourceKey(),raw.originalFields(),null);
        assertEquals("ISSUE",preview(command(noBaseline)).getFirst().action());
        when(projects.lockAndRevalidate(any())).thenThrow(new IllegalArgumentException("DPPMS_SCOPE_PROJECT_OFFICE_UNRESOLVED"));
        assertThrows(IllegalArgumentException.class,()->apply(command(noBaseline)));
        doThrow(new IllegalArgumentException("PROJECT_FORBIDDEN")).when(projects).resolve(any());
        assertThrows(IllegalArgumentException.class,()->preview(command(noBaseline)));verifyNoInteractions(mapper);
    }
    @Test void wrongTenantStopsAllOwnerReads() {
        TenantContextHolder.setTenantId(2L);assertThrows(RuntimeException.class,()->preview(command(entry("1",null))));
        verifyNoInteractions(projects,mapper,versions,scopes);
    }
    @Test void repeatValidatesCurrentFactsAndDoesNotRegenerateThreeFields() {
        var saved=new DirectDeliveryScopeRow();
        doAnswer(call->{savedFields(call.getArgument(0),saved);return 1;}).when(mapper).insertHistoricalScope(any());
        var command=command(entry("1",null));apply(command);
        when(mapper.lockIdentity(any())).thenReturn(List.of(saved));
        var result=apply(command).getFirst();assertEquals("UNCHANGED",result.action());assertEquals(saved.getEffectiveFrom(),result.fields().get("effectiveFrom"));
        saved.setOpenQuantity(new BigDecimal("7"));assertThrows(RuntimeException.class,()->apply(command));
        verify(mapper,times(1)).insertHistoricalScope(any());
        verify(mapper,times(1)).insertHistoricalProductDetail(any());
    }
    @Test void missingExistingProductDetailIsRepairedWithoutTouchingParentOrQuotaAndReplaysUnchanged() {
        var saved=new DirectDeliveryScopeRow();
        doAnswer(call->{savedFields(call.getArgument(0),saved);return 1;}).when(mapper).insertHistoricalScope(any());
        var command=command(entry("1",null));apply(command);savedDetails.clear();
        when(mapper.lockIdentity(any())).thenReturn(List.of(saved));
        var before=new DirectDeliveryScopeRow();savedFields(saved,before);clearInvocations(mapper,versions);
        var repaired=apply(command).getFirst();assertEquals("UPDATED",repaired.action());
        assertEquals("CREATED",repaired.fields().get("productDetailAction"));assertEquals(before,saved);
        verify(mapper,never()).insertHistoricalScope(any());verify(mapper).insertHistoricalProductDetail(any());
        
        var replay=apply(command).getFirst();assertEquals("UNCHANGED",replay.action());
        assertEquals("UNCHANGED",replay.fields().get("productDetailAction"));
        assertEquals(repaired.fields().get("productDetail"),replay.fields().get("productDetail"));
        verify(mapper,times(1)).insertHistoricalProductDetail(any());
    }
    @Test void changedExistingDetailAndExtraRowsCannotBeOverwrittenOrDuplicated() {
        var saved=new DirectDeliveryScopeRow();
        doAnswer(call->{savedFields(call.getArgument(0),saved);return 1;}).when(mapper).insertHistoricalScope(any());
        var command=command(entry("1",null));apply(command);when(mapper.lockIdentity(any())).thenReturn(List.of(saved));
        var detail=savedDetails.get(1001L).getFirst();clearInvocations(mapper);
        detail.setAllocatedQty(BigDecimal.ONE);assertThrows(RuntimeException.class,()->apply(command));
        detail.setAllocatedQty(new BigDecimal("4"));detail.setSerialNo("later-device");assertThrows(RuntimeException.class,()->apply(command));
        detail.setSerialNo(null);detail.setDeleted(true);assertThrows(RuntimeException.class,()->apply(command));
        detail.setDeleted(false);savedDetails.put(1001L,List.of(detail,detail));assertThrows(RuntimeException.class,()->apply(command));
        verify(mapper,never()).insertHistoricalProductDetail(any());verify(mapper,never()).insertHistoricalScope(any());
    }
    @Test void detailInsertConflictFailsTheOwningTransactionRatherThanReportingSuccess() {
        doReturn(0).when(mapper).insertHistoricalProductDetail(any());
        assertThrows(RuntimeException.class,()->apply(command(entry("1",null))));
    }
    private static void savedFields(DirectDeliveryScopeRow input,DirectDeliveryScopeRow saved) {
        org.springframework.beans.BeanUtils.copyProperties(input,saved);
    }
    @Test void missingAuditBindingAndChangedAuditCannotBeAdoptedOnRepeat() {
        var saved=new DirectDeliveryScopeRow();
        doAnswer(call->{savedFields(call.getArgument(0),saved);return 1;}).when(mapper).insertHistoricalScope(any());
        var original=entry("1",null);apply(command(original));
        when(mapper.lockIdentity(any())).thenReturn(List.of(saved));
        assertThrows(RuntimeException.class,()->apply(command(new Entry("1",original.originalFields(),null))));
        saved.setUpdater("later editor");
        assertThrows(RuntimeException.class,()->apply(command(original)));
        verify(mapper,times(1)).insertHistoricalScope(any());
    }
    @Test void completeFormalBeforePreservesOldManagerSnapshotAndBlocksAnyLaterScopeChange() {
        var saved=new DirectDeliveryScopeRow();
        doAnswer(call->{savedFields(call.getArgument(0),saved);return 1;}).when(mapper).insertHistoricalScope(any());
        var original=entry("1",null);var first=apply(command(original)).getFirst();
        var before=new LinkedHashMap<String,Object>();first.fields().forEach((name,value)->{
            if(!Set.of("productDetail","productDetailAction").contains(name))
                before.put(name,value==null?tools.jackson.databind.node.NullNode.getInstance():value instanceof LocalDateTime time?time.toString():value);
        });
        var baseline=original.baseline();var protectedEntry=new Entry(original.sourceKey(),original.originalFields(),new Baseline(
                baseline.id(),baseline.creator(),baseline.createTime(),baseline.updater(),baseline.updateTime(),
                cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(before)));
        when(mapper.lockIdentity(any())).thenReturn(List.of(saved));
        when(projects.lockAndRevalidate(any())).thenReturn(new ProjectScopeMigrationContextApi.Context(
                100L,3L,"P","Project","001","Company","C","Customer","NEW","Current manager",20L,"162007","Office",7));
        var replay=apply(command(protectedEntry)).getFirst();assertEquals("UNCHANGED",replay.action());
        assertEquals("Manager",replay.fields().get("projectManagerName"));assertEquals("E",replay.fields().get("projectManagerEmployeeNo"));
        saved.setProjectManagerName("modified historical scope");
        assertThrows(RuntimeException.class,()->apply(command(protectedEntry)));
        saved.setProjectManagerName("Manager");saved.setOpenQuantity(new BigDecimal("8"));
        assertThrows(RuntimeException.class,()->apply(command(protectedEntry)));
        verify(mapper,times(1)).insertHistoricalScope(any());verify(mapper,times(1)).insertHistoricalProductDetail(any());
    }

    @Test void newScopeAndDetailReceiptsUseStoredPrecisionAndReplayWithoutWrites() {
        var saved=new DirectDeliveryScopeRow();
        doAnswer(call->{savedFields(call.getArgument(0),saved);return 1;}).when(mapper).insertHistoricalScope(any());
        var raw=entry("1",null);var original=new Entry(raw.sourceKey(),raw.originalFields(),null);
        var first=apply(command(original)).getFirst();
        for(String field:List.of("createTime","updateTime","effectiveFrom"))
            assertEquals(0,((LocalDateTime)first.fields().get(field)).getNano()%1_000_000,field);
        var detail=(Map<?,?>)first.fields().get("productDetail");
        for(String field:List.of("createTime","updateTime"))
            assertEquals(0,((LocalDateTime)detail.get(field)).getNano()%1_000_000,field);
        var replay=replayEntry(original,first,(LocalDateTime)first.fields().get("createTime"));
        when(mapper.lockIdentity(any())).thenReturn(List.of(saved));
        assertEquals("UNCHANGED",apply(command(replay)).getFirst().action());
        saved.setUpdateTime(saved.getUpdateTime().plusNanos(1_000_000));
        assertThrows(RuntimeException.class,()->apply(command(replay)));
        verify(mapper,times(1)).insertHistoricalScope(any());verify(mapper,times(1)).insertHistoricalProductDetail(any());
    }
    @Test void earlierNanosecondReceiptsMatchOnlyTheirExactStoredMillisecond() {
        var saved=new DirectDeliveryScopeRow();
        doAnswer(call->{savedFields(call.getArgument(0),saved);return 1;}).when(mapper).insertHistoricalScope(any());
        var original=entry("1",null);var first=apply(command(original)).getFirst();
        when(mapper.lockIdentity(any())).thenReturn(List.of(saved));
        var second=LocalDateTime.of(2026,10,13,23,11,33);
        int[] nanos={123499000,123499500,123500000,266651500,999999500};
        int[] millis={123,124,124,267,1000};
        for(int index=0;index<nanos.length;index++) {
            var receiptTime=second.withNano(nanos[index]);var stored=second.plusNanos(millis[index]*1_000_000L);
            var replay=replayEntry(original,first,receiptTime);
            saved.setCreateTime(stored);saved.setUpdateTime(stored);saved.setEffectiveFrom(stored);
            assertEquals("UNCHANGED",apply(command(replay)).getFirst().action());
            saved.setEffectiveFrom(stored.plusNanos(1_000_000));
            assertThrows(RuntimeException.class,()->apply(command(replay)));
            saved.setEffectiveFrom(stored);saved.setUpdateTime(stored.plusNanos(1_000_000));
            assertThrows(RuntimeException.class,()->apply(command(replay)));
        }
        verify(mapper,times(1)).insertHistoricalScope(any());verify(mapper,times(1)).insertHistoricalProductDetail(any());
    }
    private static Entry replayEntry(Entry original,Result first,LocalDateTime receiptTime) {
        var before=new LinkedHashMap<String,Object>();
        first.fields().forEach((name,value)->{
            if(!Set.of("productDetail","productDetailAction").contains(name))
                before.put(name,value==null?tools.jackson.databind.node.NullNode.getInstance():value instanceof LocalDateTime time?time.toString():value);
        });
        for(String name:List.of("createTime","updateTime","effectiveFrom"))before.put(name,receiptTime.toString());
        return new Entry(original.sourceKey(),original.originalFields(),new Baseline(first.targetId(),
                (String)first.fields().get("creator"),receiptTime,(String)first.fields().get("updater"),receiptTime,
                cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(before)));
    }

    List<Result> preview(Command c){return tx.execute(status->service.preview(c));}
    List<Result> apply(Command c){return tx.execute(status->service.apply(c));}
}
