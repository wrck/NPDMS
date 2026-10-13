package cn.iocoder.yudao.module.pms.commerce.service.migration;
import cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsProjectProductScopeApi;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsProjectProductScopeApi.*;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderLineDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDetailDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsProductScopeMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.DeliveryScopeMapper;
import cn.iocoder.yudao.module.pms.commerce.service.scope.DeliveryScopeProjectVersionService;
import cn.iocoder.yudao.module.pms.project.api.migration.ProjectScopeMigrationContextApi;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class DppmsProjectProductScopeServiceTest {
    ProjectScopeMigrationContextApi projects=mock(ProjectScopeMigrationContextApi.class);
    DppmsProductScopeMapper mapper=mock(DppmsProductScopeMapper.class);
    DeliveryScopeMapper scopes=mock(DeliveryScopeMapper.class);
    DeliveryScopeProjectVersionService versions=mock(DeliveryScopeProjectVersionService.class);
    cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi access=mock(cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi.class);
    DppmsProjectProductScopeService service=new DppmsProjectProductScopeService(projects,mapper,scopes,versions,access);
    LocalDateTime history=LocalDateTime.of(2020,1,2,3,4,5,321000000);
    SalesOrderLineDO line=new SalesOrderLineDO();
    Map<Long,List<DeliveryScopeDetailDO>> savedDetails=new HashMap<>();
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        var context=new ProjectScopeMigrationContextApi.Context(100L,3L,"P","Project","001","Company","C","Customer","E","Manager",20L,"162007","Office",7);
        when(projects.resolve(any())).thenReturn(context);when(projects.lockAndRevalidate(any())).thenReturn(context);
        line.setId(40L);line.setTenantId(1L);line.setSourceSystem("D365");line.setCompanyCode("001");line.setOrderType("0");line.setOrderQty(new BigDecimal("4"));
        when(mapper.selectAssociatedLines(any())).thenReturn(List.of(line));when(mapper.lockAssociatedLines(any())).thenReturn(List.of(line));
        when(mapper.selectIdentity(any())).thenReturn(List.of());when(mapper.lockIdentity(any())).thenReturn(List.of());
        when(mapper.insertHistoricalScope(any())).thenReturn(1);
        when(mapper.selectProductDetails(any())).thenAnswer(call->savedDetails.getOrDefault(call.getArgument(0,DppmsProductScopeMapper.ProductDetailQuery.class).deliveryScopeId(),List.of()));
        when(mapper.selectProductDetailsForUpdate(any())).thenAnswer(call->savedDetails.getOrDefault(call.getArgument(0,DppmsProductScopeMapper.ProductDetailQuery.class).deliveryScopeId(),List.of()));
        when(mapper.insertHistoricalProductDetail(any())).thenAnswer(call->{
            DeliveryScopeDetailDO input=call.getArgument(0);var saved=new DeliveryScopeDetailDO();
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
        service.preview(new Command(original.tenantId(),original.actorId(),original.projectId(),original.expectedProjectVersion(),
                original.sourceProjectKey(),original.entries(),18L));
        verify(projects).resolve(argThat(query->query.sourceConnectionId().equals(18L) && query.expectedProjectVersion()==3L
                && query.projectId()==100L && "101".equals(query.sourceProjectKey())));
        verify(mapper,never()).insertHistoricalScope(any());
    }
    @Test void previewGeneratesFormalFieldsWithoutWritingOrConsumingCapacity() {
        var result=service.preview(command(entry("1",null))).getFirst();
        assertEquals(1001L,result.targetId());assertEquals(new BigDecimal("4"),result.fields().get("allocatedQty"));
        assertEquals(false,result.fields().get("allocationConfirmed"));assertEquals("ACTIVE",result.fields().get("scopeStatus"));
        assertEquals(1L,result.fields().get("allocationVersion"));assertNotNull(result.fields().get("effectiveFrom"));
        assertEquals(history,result.fields().get("createTime"));assertEquals("creator",result.fields().get("creator"));
        assertEquals("Historical name",result.fields().get("productDesc"));assertEquals(20L,result.fields().get("departmentId"));
        verify(mapper,never()).insertHistoricalScope(any());verify(versions,never()).lock(any(),any(),any(),any());verify(versions,never()).advance(any(),any(),any(),any());
        assertEquals("CREATED",result.fields().get("productDetailAction"));
        var detail=(Map<?,?>)result.fields().get("productDetail");assertEquals(21,detail.size());
        assertEquals(1001L,detail.get("deliveryScopeId"));assertEquals("I",detail.get("productCode"));
        assertEquals(new BigDecimal("4"),detail.get("allocatedQty"));assertNull(detail.get("serialNo"));
        verify(mapper,never()).insertHistoricalProductDetail(any());
    }
    @Test void appliesExplicitSplitAndKeepsHistoricalDeliveryFiguresAndAudit() {
        service.apply(command(entry("1","2")));
        verify(mapper).insertHistoricalScope(argThat(r->r.getId()==1001L && r.getAllocatedQty().compareTo(new BigDecimal("2"))==0
                && r.getDeliveredQuantity().compareTo(new BigDecimal("3"))==0 && r.getOpenQuantity().compareTo(BigDecimal.ONE)==0
                && Boolean.FALSE.equals(r.getAllocationConfirmed()) && history.equals(r.getUpdateTime()) && r.getEffectiveTo()==null));
        verify(projects).lockAndRevalidate(any());verify(versions,never()).advance(any(),any(),any(),any());
        verify(mapper).insertHistoricalProductDetail(argThat(d->d.getDeliveryScopeId()==1001L && d.getSerialNo()==null
                && "I".equals(d.getProductCode()) && "Historical name".equals(d.getProductName())
                && d.getAllocatedQty().compareTo(new BigDecimal("2"))==0 && !history.equals(d.getCreateTime())));
    }
    @Test void entireBatchValidationPreventsPartialWritesForZeroDuplicateAndChangedOrder() {
        assertThrows(RuntimeException.class,()->service.apply(command(entry("1",null),entry("2","0"))));
        assertThrows(RuntimeException.class,()->service.apply(command(entry("1",null),entry("1",null))));
        line.setOrderQty(new BigDecimal("5"));assertThrows(RuntimeException.class,()->service.apply(command(entry("1",null))));
        verify(mapper,never()).insertHistoricalScope(any());
        verify(mapper,never()).insertHistoricalProductDetail(any());
    }
    @Test void distinctHistoricalRowsMayShareOrderLineAndAllocationVersionWithoutConsumingCapacity() {
        var result=service.apply(command(entry("1",null),entry("2",null)));
        assertEquals(List.of(1001L,1002L),result.stream().map(Result::targetId).toList());
        assertTrue(result.stream().allMatch(r->Boolean.FALSE.equals(r.fields().get("allocationConfirmed"))
                && Long.valueOf(1L).equals(r.fields().get("allocationVersion"))));
        verify(mapper,times(2)).insertHistoricalScope(any());
        verify(versions,never()).advance(any(),any(),any(),any());
    }
    @Test void ambiguousLineAndExistingIdAreNeverGuessedOrOverwritten() {
        when(mapper.lockAssociatedLines(any())).thenReturn(List.of(line,line));
        assertThrows(RuntimeException.class,()->service.apply(command(entry("1",null))));
        when(mapper.lockAssociatedLines(any())).thenReturn(List.of(line));when(scopes.selectById(1001L)).thenReturn(new DeliveryScopeDO());
        assertThrows(RuntimeException.class,()->service.apply(command(entry("1",null))));verify(mapper,never()).insertHistoricalScope(any());
    }
    @Test void wrongTenantStopsAllOwnerReads() {
        TenantContextHolder.setTenantId(2L);assertThrows(RuntimeException.class,()->service.preview(command(entry("1",null))));
        verifyNoInteractions(projects,mapper,versions,scopes);
    }
    @Test void repeatValidatesCurrentFactsAndDoesNotRegenerateThreeFields() {
        var saved=new DeliveryScopeDO();
        doAnswer(call->{savedFields(call.getArgument(0),saved);return 1;}).when(mapper).insertHistoricalScope(any());
        var command=command(entry("1",null));service.apply(command);
        when(mapper.lockIdentity(any())).thenReturn(List.of(saved));
        var result=service.apply(command).getFirst();assertEquals("UNCHANGED",result.action());assertEquals(saved.getEffectiveFrom(),result.fields().get("effectiveFrom"));
        saved.setOpenQuantity(new BigDecimal("7"));assertThrows(RuntimeException.class,()->service.apply(command));
        verify(mapper,times(1)).insertHistoricalScope(any());
        verify(mapper,times(1)).insertHistoricalProductDetail(any());
    }
    @Test void missingExistingProductDetailIsRepairedWithoutTouchingParentOrQuotaAndReplaysUnchanged() {
        var saved=new DeliveryScopeDO();
        doAnswer(call->{savedFields(call.getArgument(0),saved);return 1;}).when(mapper).insertHistoricalScope(any());
        var command=command(entry("1",null));service.apply(command);savedDetails.clear();
        when(mapper.lockIdentity(any())).thenReturn(List.of(saved));
        var before=new DeliveryScopeDO();savedFields(saved,before);clearInvocations(mapper,versions);
        var repaired=service.apply(command).getFirst();assertEquals("UPDATED",repaired.action());
        assertEquals("CREATED",repaired.fields().get("productDetailAction"));assertEquals(before,saved);
        verify(mapper,never()).insertHistoricalScope(any());verify(mapper).insertHistoricalProductDetail(any());
        verify(versions,never()).advance(any(),any(),any(),any());
        var replay=service.apply(command).getFirst();assertEquals("UNCHANGED",replay.action());
        assertEquals("UNCHANGED",replay.fields().get("productDetailAction"));
        assertEquals(repaired.fields().get("productDetail"),replay.fields().get("productDetail"));
        verify(mapper,times(1)).insertHistoricalProductDetail(any());
    }
    @Test void changedExistingDetailAndExtraRowsCannotBeOverwrittenOrDuplicated() {
        var saved=new DeliveryScopeDO();
        doAnswer(call->{savedFields(call.getArgument(0),saved);return 1;}).when(mapper).insertHistoricalScope(any());
        var command=command(entry("1",null));service.apply(command);when(mapper.lockIdentity(any())).thenReturn(List.of(saved));
        var detail=savedDetails.get(1001L).getFirst();clearInvocations(mapper);
        detail.setAllocatedQty(BigDecimal.ONE);assertThrows(RuntimeException.class,()->service.apply(command));
        detail.setAllocatedQty(new BigDecimal("4"));detail.setSerialNo("later-device");assertThrows(RuntimeException.class,()->service.apply(command));
        detail.setSerialNo(null);detail.setDeleted(true);assertThrows(RuntimeException.class,()->service.apply(command));
        detail.setDeleted(false);savedDetails.put(1001L,List.of(detail,detail));assertThrows(RuntimeException.class,()->service.apply(command));
        verify(mapper,never()).insertHistoricalProductDetail(any());verify(mapper,never()).insertHistoricalScope(any());
    }
    @Test void detailInsertConflictFailsTheOwningTransactionRatherThanReportingSuccess() {
        doReturn(0).when(mapper).insertHistoricalProductDetail(any());
        assertThrows(RuntimeException.class,()->service.apply(command(entry("1",null))));
    }
    private static void savedFields(DeliveryScopeDO input,DeliveryScopeDO saved) {
        org.springframework.beans.BeanUtils.copyProperties(input,saved);
    }
    @Test void missingAuditBindingAndChangedAuditCannotBeAdoptedOnRepeat() {
        var saved=new DeliveryScopeDO();
        doAnswer(call->{savedFields(call.getArgument(0),saved);return 1;}).when(mapper).insertHistoricalScope(any());
        var original=entry("1",null);service.apply(command(original));
        when(mapper.lockIdentity(any())).thenReturn(List.of(saved));
        assertThrows(RuntimeException.class,()->service.apply(command(new Entry("1",original.originalFields(),null))));
        saved.setUpdater("later editor");
        assertThrows(RuntimeException.class,()->service.apply(command(original)));
        verify(mapper,times(1)).insertHistoricalScope(any());
    }
    @Test void completeFormalBeforePreservesOldManagerSnapshotAndBlocksAnyLaterScopeChange() {
        var saved=new DeliveryScopeDO();
        doAnswer(call->{savedFields(call.getArgument(0),saved);return 1;}).when(mapper).insertHistoricalScope(any());
        var original=entry("1",null);var first=service.apply(command(original)).getFirst();
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
        var replay=service.apply(command(protectedEntry)).getFirst();assertEquals("UNCHANGED",replay.action());
        assertEquals("Manager",replay.fields().get("projectManagerName"));assertEquals("E",replay.fields().get("projectManagerEmployeeNo"));
        saved.setProjectManagerName("modified historical scope");
        assertThrows(RuntimeException.class,()->service.apply(command(protectedEntry)));
        saved.setProjectManagerName("Manager");saved.setOpenQuantity(new BigDecimal("8"));
        assertThrows(RuntimeException.class,()->service.apply(command(protectedEntry)));
        verify(mapper,times(1)).insertHistoricalScope(any());verify(mapper,times(1)).insertHistoricalProductDetail(any());
    }
    @Test void emptyProjectPermissionCannotExpandTheScopePage() {
        when(access.resolveCurrent(any())).thenReturn(new cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult(100L,1L,Set.of(),Set.of(100L)));
        assertThrows(RuntimeException.class,()->service.page(new DppmsProjectProductScopeApi.Query(1L,1L,100L,1,20)));
        verifyNoInteractions(mapper);
    }
    @Test void formalPagePreservesHistoricalQuantitiesAndReusesOrderUnit() {
        when(access.resolveCurrent(any())).thenReturn(new cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult(100L,1L,Set.of(100L),Set.of()));
        var row=new DppmsProductScopeMapper.ProductProjection();row.setId(9L);row.setSourceEvidence("DPPMS:pm_project_product_line:101:7");
        row.setAllocatedQty(new BigDecimal("2"));row.setOrderQuantity(new BigDecimal("4"));row.setDeliveredQuantity(new BigDecimal("3"));row.setOpenQuantity(BigDecimal.ONE);
        row.setUnitCode(null);row.setAllocationConfirmed(false);
        when(mapper.selectProjectPage(any())).thenReturn(List.of(row));when(mapper.countProject(any())).thenReturn(1L);
        var result=service.page(new DppmsProjectProductScopeApi.Query(1L,1L,100L,1,20));
        assertEquals(1,result.total());assertEquals("7",result.list().getFirst().sourceKey());
        assertEquals(new BigDecimal("2"),result.list().getFirst().fields().get("projectQuantity"));
        assertEquals(new BigDecimal("3"),result.list().getFirst().fields().get("deliveredQuantity"));assertNull(result.list().getFirst().fields().get("unitCode"));
    }
}
