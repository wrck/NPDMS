package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.*;
import cn.iocoder.yudao.module.pms.integration.api.sync.DppmsProjectBindingApi;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.*;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.pms.project.api.migration.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.module.pms.integration.sync.DppmsProjectRealProductSyncTemplate.*;

class DppmsProjectRealProductSyncAdapterTest {
    final ProjectRealProductMigrationApi products=mock(ProjectRealProductMigrationApi.class);
    final DppmsProjectBindingApi bindings=mock(DppmsProjectBindingApi.class);
    final ProjectMasterMigrationApi projects=mock(ProjectMasterMigrationApi.class);
    final SyncTaskService tasks=mock(SyncTaskService.class);
    final SyncRunMapper runs=mock(SyncRunMapper.class);
    final DppmsProjectRealProductSyncAdapter adapter=new DppmsProjectRealProductSyncAdapter(products,bindings,projects,tasks,runs);
    SyncTaskDO task;SyncRunDO run;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        task=new SyncTaskDO().setId(12L).setAdapter(ADAPTER).setConnectionId(18L).setSourceSystem("DPPMS")
                .setDefinition(JsonUtils.toJsonString(create(18L))).setVersion(0).setActiveRunId(13L);task.setTenantId(1L);
        run=new SyncRunDO().setId(13L).setTaskId(12L).setConfigVersion(0).setStatus("APPLYING").setPreview(false);run.setTenantId(1L);run.setCreator("1");
        when(tasks.required(12L)).thenReturn(task);when(runs.selectScoped(any())).thenReturn(run);
        when(bindings.resolve(any(DppmsProjectBindingApi.BatchQuery.class))).thenReturn(List.of(new DppmsProjectBindingApi.ProjectBinding(18L,"101",10L,"10","P1","P1")));
        when(projects.inspectBindings(any())).thenReturn(List.of(JsonUtils.parseObject("{\"id\":10,\"tenantId\":1,\"version\":4,\"projectCode\":\"P1\",\"projectType\":\"10\"}",ProjectMasterMigrationApi.Facts.class)));
        when(products.apply(any())).thenAnswer(call->{var command=(ProjectRealProductMigrationApi.Command)call.getArgument(0);
            return command.rows().stream().map(row->new ProjectRealProductMigrationApi.Result(row.sourceKey(),30L,"UNCHANGED",Map.of("id",30L),"old-formal-baseline")).toList();});
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    Row row(Long target) {
        var fields=new LinkedHashMap<String,Object>();columns().forEach(name->fields.put(name,null));
        fields.put("id",301);fields.put("projectId",101);fields.put("projectType","10");fields.put("originalProjectCode","P1");
        fields.put("num",-2);fields.put("projectCode","COMMERCIAL");fields.put("upstream_num",-2);fields.put("upstream_projectCode","COMMERCIAL");
        fields.put("upstream_dataSource","SMS");fields.put("upstreamMatchCount",1);return new Row(OBJECT,"301",fields,target);
    }
    Batch batch(Row row,List<Binding> prior) { return new Batch("integration:1:12",List.of(row),prior,true,"RETAIN",false,"UPSERT",false); }
    @Test void rawSqlOriginalAndUpstreamFieldsFeedExistingOwnerWithExactProjectIdentity() {
        var result=adapter.apply(batch(row(null),List.of())).getFirst();
        verify(products).apply(argThat(command->command.identity().projectId()==10L && command.identity().sourceConnectionId()==18L
                && command.identity().expectedProjectVersion()==4L && command.rows().getFirst().originalFields().size()==12
                && command.rows().getFirst().upstreamFields().size()==10 && "-2".equals(command.rows().getFirst().originalFields().get("num"))
                && command.rows().getFirst().originalFields().containsKey("memo") && command.rows().getFirst().originalFields().get("memo")==null));
        assertEquals(30L,result.targetId());assertEquals(12,((Map<?,?>)JsonUtils.parseMap(JsonUtils.toJsonString(result.after())).get("originalFields")).size());
        verify(projects).lockBindings(any());
    }
    @Test void replayRetainsExactFormalBaselineAndAnySourceChangeBlocksBeforeOwner() {
        var result=adapter.apply(batch(row(null),List.of())).getFirst();var prior=new Binding(OBJECT,"301",30L,JsonUtils.parseMap(JsonUtils.toJsonString(result.after())));
        clearInvocations(products);adapter.apply(batch(row(30L),List.of(prior)));
        verify(products).apply(argThat(command->"old-formal-baseline".equals(command.rows().getFirst().baseline())));
        var changed=row(30L);changed.fields().put("memo","");clearInvocations(products);
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(changed,List.of(prior))));verifyNoInteractions(products);
    }
    @Test void missingProjectWrongVersionAndAdoptionRemainBlocked() {
        when(bindings.resolve(any(DppmsProjectBindingApi.BatchQuery.class))).thenReturn(List.of());
        assertEquals("ISSUE",adapter.apply(batch(row(null),List.of())).getFirst().action());verifyNoInteractions(products);
        run.setConfigVersion(1);assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(row(null),List.of())));
        var adopt=new Batch("integration:1:12",List.of(row(null)),List.of(),true,"RETAIN",true,"UPSERT",false);
        assertThrows(IllegalArgumentException.class,()->adapter.apply(adopt));verifyNoInteractions(products);
    }
    @Test void emptyPilotRetainsWholeTemplateSqlAndDoesNotInventProductsOrProjectCalls() {
        assertDoesNotThrow(()->new SyncDefinitionValidator(List.of(adapter)).validate(create(18L)));
        var source=create(18L).sources().getFirst();assertEquals("",source.parameters().get("projectIds"));assertEquals("10",source.parameters().get("projectTypes"));
        assertEquals("SQL",source.readMode());assertTrue(source.sql().contains("l.*"));assertTrue(source.sql().contains("upstream_dataSource"));
        assertTrue(adapter.apply(new Batch("integration:1:12",List.of(),List.of(),true,"RETAIN",false,"UPSERT",false)).isEmpty());
        verifyNoInteractions(products,projects,bindings);
    }
}
