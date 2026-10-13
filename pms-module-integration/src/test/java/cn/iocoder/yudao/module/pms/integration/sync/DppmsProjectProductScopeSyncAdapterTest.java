package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsProjectProductScopeApi;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.*;
import cn.iocoder.yudao.module.pms.integration.api.sync.DppmsProjectBindingApi;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.*;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.pms.project.api.migration.ProjectMasterMigrationApi;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static cn.iocoder.yudao.module.pms.integration.sync.DppmsProjectProductScopeSyncTemplate.*;

class DppmsProjectProductScopeSyncAdapterTest {
    final DppmsProjectProductScopeApi scopes=mock(DppmsProjectProductScopeApi.class);
    final DppmsProjectBindingApi bindings=mock(DppmsProjectBindingApi.class);
    final ProjectMasterMigrationApi projects=mock(ProjectMasterMigrationApi.class);
    final SyncTaskService tasks=mock(SyncTaskService.class);
    final SyncRunMapper runs=mock(SyncRunMapper.class);
    final DppmsProjectProductScopeSyncAdapter adapter=new DppmsProjectProductScopeSyncAdapter(scopes,bindings,projects,tasks,runs);
    SyncRunDO run;SyncTaskDO task;
    final LocalDateTime date=LocalDateTime.parse("2026-10-10T12:00:00");
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        task=new SyncTaskDO().setId(12L).setAdapter(ADAPTER).setConnectionId(18L).setSourceSystem("DPPMS")
                .setDefinition(JsonUtils.toJsonString(create(18L))).setVersion(0).setActiveRunId(13L);task.setTenantId(1L);
        run=new SyncRunDO().setId(13L).setTaskId(12L).setConfigVersion(0).setStatus("APPLYING").setPreview(false);run.setTenantId(1L);run.setCreator("1");
        when(tasks.required(12L)).thenReturn(task);when(runs.selectScoped(any())).thenReturn(run);
        when(bindings.resolve(any(DppmsProjectBindingApi.BatchQuery.class))).thenReturn(List.of(new DppmsProjectBindingApi.ProjectBinding(18L,"50370",10L,"10","P1","P1")));
        when(projects.inspectBindings(any())).thenReturn(List.of(JsonUtils.parseObject("{\"id\":10,\"tenantId\":1,\"version\":4,\"projectCode\":\"P1\",\"projectType\":\"10\"}",ProjectMasterMigrationApi.Facts.class)));
        when(scopes.apply(any())).thenAnswer(call->{var command=(DppmsProjectProductScopeApi.Command)call.getArgument(0);
            var fields=new LinkedHashMap<String,Object>();fields.put("id",30L);fields.put("creator","1");fields.put("updater","1");
            fields.put("createTime",date);fields.put("updateTime",date);fields.put("allocationConfirmed",false);
            return command.entries().stream().map(entry->new DppmsProjectProductScopeApi.Result(entry.sourceKey(),30L,"UNCHANGED",fields)).toList();});
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    Row row() {
        var fields=new LinkedHashMap<String,Object>();COLUMNS.forEach(name->fields.put(name,null));
        fields.put("id",100);fields.put("projectId",50370);fields.put("originalProjectCode","P1");fields.put("projectType","10");
        fields.put("contractNo","C1");fields.put("itemCode","X1");fields.put("itemName","原产品名称");
        fields.put("orderQuantity","3.000000");fields.put("deliverQuantity","1.000000");fields.put("openQuantity","2.000000");
        fields.put("orderNumber","O1");fields.put("lineNum","10");
        return new Row(OBJECT,"100",fields,null);
    }
    Batch batch(Row row,List<Binding> prior) { return new Batch("integration:1:12",List.of(row),prior,true,"RETAIN",false,"UPSERT",false); }
    @Test void completeOriginalsArePassedToFormalOwnerWithActualProjectVersionAndConnection() {
        var result=adapter.apply(batch(row(),List.of())).getFirst();
        verify(scopes).apply(argThat(command->command.projectId()==10L && command.expectedProjectVersion()==4L
                && command.sourceConnectionId()==18L && command.entries().getFirst().originalFields().size()==11
                && command.entries().getFirst().originalFields().get("projectQuantity")==null
                && "3.000000".equals(command.entries().getFirst().originalFields().get("orderQuantity"))));
        assertEquals(30L,result.targetId());assertEquals(11,((Map<?,?>)result.after().get("originalFields")).size());
        assertEquals(false,((Map<?,?>)result.after().get("scope")).get("allocationConfirmed"));
        verify(projects).lockBindings(argThat(query->query.projects().size()==1 && query.projects().getFirst().version()==4L));
    }
    @Test void replayUsesSavedAuditAndEveryOriginalFieldChangeBlocksBeforeOwner() {
        var source=row();var created=adapter.apply(batch(source,List.of())).getFirst();
        var bound=new Row(OBJECT,"100",source.fields(),30L);
        var proof=new Binding(OBJECT,"100",30L,JsonUtils.parseMap(JsonUtils.toJsonString(created.after())));
        clearInvocations(scopes);adapter.apply(batch(bound,List.of(proof)));
        verify(scopes).apply(argThat(command->command.entries().getFirst().baseline().id()==30L
                && date.equals(command.entries().getFirst().baseline().createTime())));
        clearInvocations(scopes);bound.fields().put("openQuantity","1.000000");
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(bound,List.of(proof))));verifyNoInteractions(scopes);
    }
    @Test void missingProjectOrChangedTypeCannotBecomeAFormalScope() {
        when(bindings.resolve(any(DppmsProjectBindingApi.BatchQuery.class))).thenReturn(List.of());
        assertEquals("ISSUE",adapter.apply(batch(row(),List.of())).getFirst().action());verifyNoInteractions(scopes);
        when(bindings.resolve(any(DppmsProjectBindingApi.BatchQuery.class))).thenReturn(List.of(new DppmsProjectBindingApi.ProjectBinding(18L,"50370",10L,"afxx","P1","P1")));
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(row(),List.of())));verifyNoInteractions(scopes);
    }
    @Test void previewPreservesRawFieldsForOwnerDependencyBlockerWithoutCreatingBaseline() {
        run.setStatus("VALIDATING").setPreview(true);
        when(scopes.preview(any())).thenReturn(List.of(new DppmsProjectProductScopeApi.Result("100",null,"ISSUE",Map.of(),
                "DPPMS_PRODUCT_SCOPE_ASSOCIATED_ORDER_LINE_MISSING")));
        var source=row();var result=adapter.preview(batch(source,List.of())).getFirst();
        assertEquals("ISSUE",result.action());assertNull(result.targetId());assertEquals(source.fields(),result.after());
        assertTrue(result.message().contains("ASSOCIATED_ORDER_LINE_MISSING"));
        verify(scopes,never()).apply(any());verify(projects,never()).lockBindings(any());
    }
    @Test void replayRetainsPreciseScopeTimesWhenAuditWasSerializedAsEpochMillis() {
        var precise=date.withNano(266651500);
        doAnswer(call->{
            var fields=new LinkedHashMap<String,Object>();fields.put("id",30L);fields.put("creator","1");fields.put("updater","1");
            fields.put("createTime",precise);fields.put("updateTime",precise);fields.put("effectiveFrom",precise);fields.put("allocationConfirmed",false);
            return List.of(new DppmsProjectProductScopeApi.Result("100",30L,"CREATED",fields));
        }).when(scopes).apply(any());
        var source=row();var created=adapter.apply(batch(source,List.of())).getFirst();
        var persisted=JsonUtils.parseMap(JsonUtils.toJsonString(created.after()));
        var proof=new Binding(OBJECT,"100",30L,persisted);var bound=new Row(OBJECT,"100",source.fields(),30L);
        clearInvocations(scopes);adapter.apply(batch(bound,List.of(proof)));
        verify(scopes).apply(argThat(command->precise.equals(command.entries().getFirst().baseline().createTime())
                && precise.equals(command.entries().getFirst().baseline().updateTime())));
        clearInvocations(scopes);
        @SuppressWarnings("unchecked") var audit=(Map<String,Object>)persisted.get("baseline");
        audit.put("updateTime",((Number)audit.get("updateTime")).longValue()+1);
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(bound,List.of(proof))));verifyNoInteractions(scopes);
    }
    @Test void approvedOldAuditBaselineDoesNotAuthorizeClearOrAdoption() {
        var source=row();source.fields().put("baseline",Map.of("id",30,"creator","1","updater","1","createTime",date.toString(),"updateTime",date.toString()));
        adapter.apply(batch(source,List.of()));verify(scopes).apply(argThat(command->command.entries().getFirst().baseline().id()==30L
                && date.equals(command.entries().getFirst().baseline().createTime()) && date.equals(command.entries().getFirst().baseline().updateTime())));
        clearInvocations(scopes);
        var adopt=new Batch("integration:1:12",List.of(source),List.of(),true,"RETAIN",true,"UPSERT",false);
        assertThrows(IllegalArgumentException.class,()->adapter.apply(adopt));verifyNoInteractions(scopes);
    }
    @Test void wrongConfigurationVersionBlocksBeforeOwner() {
        run.setConfigVersion(1);
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(row(),List.of())));verifyNoInteractions(scopes);
    }
}
