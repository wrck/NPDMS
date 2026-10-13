package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.*;
import cn.iocoder.yudao.module.pms.integration.api.sync.DppmsProjectBindingApi;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.*;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.pms.project.api.migration.*;
import cn.iocoder.yudao.module.pms.project.api.migration.ProjectAssociatedProductMigrationApi.Kind;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.module.pms.integration.sync.DppmsProjectAssociatedProductSyncTemplate.*;

class DppmsProjectAssociatedProductSyncAdapterTest {
    final ProjectAssociatedProductMigrationApi products=mock(ProjectAssociatedProductMigrationApi.class);
    final DppmsProjectBindingApi bindings=mock(DppmsProjectBindingApi.class);
    final ProjectMasterMigrationApi projects=mock(ProjectMasterMigrationApi.class);
    final SyncTaskService tasks=mock(SyncTaskService.class);final SyncRunMapper runs=mock(SyncRunMapper.class);
    final DppmsProjectAssociatedProductSyncAdapter adapter=new DppmsProjectAssociatedProductSyncAdapter(products,bindings,projects,tasks,runs);
    SyncTaskDO task;SyncRunDO run;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        task=new SyncTaskDO().setId(12L).setAdapter(ADAPTER).setConnectionId(18L).setSourceSystem("DPPMS")
                .setDefinition(JsonUtils.toJsonString(create(18L))).setVersion(0).setActiveRunId(13L);task.setTenantId(1L);
        run=new SyncRunDO().setId(13L).setTaskId(12L).setConfigVersion(0).setStatus("APPLYING").setPreview(false);run.setTenantId(1L);run.setCreator("1");
        when(tasks.required(12L)).thenReturn(task);when(runs.selectScoped(any())).thenReturn(run);
        when(bindings.resolve(any(DppmsProjectBindingApi.BatchQuery.class))).thenReturn(List.of(new DppmsProjectBindingApi.ProjectBinding(18L,"101",10L,"10","P1-0","P1-0")));
        when(projects.inspectBindings(any())).thenReturn(List.of(JsonUtils.parseObject("{\"id\":10,\"tenantId\":1,\"version\":4,\"projectCode\":\"P1-0\",\"projectType\":\"10\"}",ProjectMasterMigrationApi.Facts.class)));
        when(products.apply(any())).thenAnswer(call->{var command=(ProjectAssociatedProductMigrationApi.Command)call.getArgument(0);
            return command.rows().stream().map(row->new ProjectAssociatedProductMigrationApi.Result(row.sourceKey(),30L,"UNCHANGED",Map.of("id",30L),"formal-before")).toList();});
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    Row row(Kind kind,Long target) {
        var fields=new LinkedHashMap<String,Object>();columns(kind).forEach(name->fields.put(name,null));
        fields.put("sourceProjectId",101);fields.put("projectType","10");fields.put("originalProjectCode","P1-0");fields.put("projectCode","P1");
        return new Row(kind.sourceTable,"a".repeat(64)+":1",fields,target);
    }
    Batch batch(List<Row> rows,List<Binding> prior) { return new Batch("integration:1:12",rows,prior,true,"RETAIN",false,"UPSERT",false); }
    @Test void bothWholeSqlObjectsReuseTheirOwnerWithAllOriginalFieldsAndExactProjectIdentity() {
        var result=adapter.apply(batch(List.of(row(Kind.LEASE_PRODUCT,null),row(Kind.PRODUCT_CONFIGURATION,null)),List.of()));
        assertEquals(2,result.size());
        verify(products).apply(argThat(command->command.kind()==Kind.LEASE_PRODUCT && command.rows().getFirst().originalFields().size()==11
                && command.identity().projectId()==10L && command.identity().expectedProjectVersion()==4L && "P1-0".equals(command.originalProjectCode())));
        verify(products).apply(argThat(command->command.kind()==Kind.PRODUCT_CONFIGURATION && command.rows().getFirst().originalFields().containsKey("bomPaths")));
        verify(projects).lockBindings(any());
    }
    @Test void replayUsesTheCompleteFormalBeforeAndOriginalNullCannotBecomeAnEmptyString() {
        var initial=adapter.apply(batch(List.of(row(Kind.LEASE_PRODUCT,null)),List.of())).getFirst();
        var binding=new Binding(initial.object(),initial.sourceKey(),30L,initial.after());
        adapter.apply(batch(List.of(row(Kind.LEASE_PRODUCT,30L)),List.of(binding)));
        verify(products).apply(argThat(command->command.rows().getFirst().targetId()!=null && "formal-before".equals(command.rows().getFirst().baseline())));
        var changed=row(Kind.LEASE_PRODUCT,30L);var fields=new LinkedHashMap<>(changed.fields());fields.put("memo","");
        clearInvocations(products);assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(
                List.of(new Row(changed.object(),changed.sourceKey(),fields,30L)),List.of(binding))));verifyNoInteractions(products);
    }
    @Test void missingFormalProjectOrStaleConfigurationAndAdoptionFailBeforeOwnerWrites() {
        when(bindings.resolve(any(DppmsProjectBindingApi.BatchQuery.class))).thenReturn(List.of());
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(List.of(row(Kind.LEASE_PRODUCT,null)),List.of())));
        run.setConfigVersion(2);assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(List.of(row(Kind.LEASE_PRODUCT,null)),List.of())));
        assertThrows(IllegalArgumentException.class,()->adapter.apply(new Batch("integration:1:12",List.of(),List.of(),true,"RETAIN",true,"UPSERT",false)));verifyNoInteractions(products);
    }
    @Test void wholeTemplatesAreEditableAndCompositeKeysCannotBeUsedForIncrementalOrPagedRuns() {
        var definition=create(18L);var validator=new SyncDefinitionValidator(List.of(adapter));assertDoesNotThrow(()->validator.validate(definition));
        assertEquals(2,definition.sources().size());assertTrue(definition.sources().stream().allMatch(source->"".equals(source.parameters().get("projectIds")) && source.sourceKeyColumns().size()==12));
        var source=definition.sources().getFirst();
        var custom=source.toBuilder().sql("SELECT l.*, 101 AS sourceProjectId FROM pm_project_product_lease_line_from_crm l WHERE l.projectCode=:code")
                .parameters(Map.of("code","P1")).build();assertTrue(MysqlSyncReader.compile(custom).sql().contains("l.projectCode=?"));
        assertThrows(IllegalArgumentException.class,()->validator.validate(definition.toBuilder().mode("INCREMENTAL").build()));
        assertThrows(IllegalArgumentException.class,()->validator.validate(definition.toBuilder().autoPaging(true).build()));
        assertTrue(adapter.apply(batch(List.of(),List.of())).isEmpty());verifyNoInteractions(products,bindings,projects);
    }
}
