package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.*;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.*;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.pms.project.api.migration.ProjectMasterMigrationApi;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.module.pms.integration.sync.DppmsProjectIdentitySyncTemplate.*;

class DppmsProjectIdentitySyncAdapterTest {
    final ProjectMasterMigrationApi projects=mock(ProjectMasterMigrationApi.class);
    final SyncTaskService tasks=mock(SyncTaskService.class);
    final SyncRunMapper runs=mock(SyncRunMapper.class);
    final SyncBindingMapper bindings=mock(SyncBindingMapper.class);
    final DppmsProjectIdentitySyncAdapter adapter=new DppmsProjectIdentitySyncAdapter(projects,tasks,runs,bindings);
    SyncRunDO run;
    @BeforeEach void setup(){
        TenantContextHolder.setTenantId(1L);
        var task=new SyncTaskDO().setId(12L).setAdapter(ADAPTER).setConnectionId(18L).setSourceSystem("DPPMS").setVersion(1)
                .setActiveRunId(13L).setDefinition(JsonUtils.toJsonString(create(18L)));task.setTenantId(1L);
        run=new SyncRunDO().setId(13L).setTaskId(12L).setConfigVersion(1).setPreview(false).setStatus("APPLYING");run.setTenantId(1L);run.setCreator("7");
        when(tasks.required(12L)).thenReturn(task);when(runs.selectScoped(any())).thenReturn(run);
        var original=new LinkedHashMap<String,Object>();DppmsProjectMasterSyncTemplate.ORIGINAL_COLUMNS.forEach(c->original.put(c,tools.jackson.databind.node.NullNode.getInstance()));
        original.put("projectId","12");original.put("projectType","10");original.put("projectCode","P-0");
        var canonical=new SyncBindingDO().setSourceKey("12").setTargetId(100L).setFieldsJson(JsonUtils.toJsonString(Map.of(
                "originalFields",original,"project",Map.of("id",100L,"tenantId",1L,"version",3L,"projectType","10","projectCode","P-0"))));
        when(bindings.selectIdentitySources(any())).thenReturn(List.of(canonical));
        var current=new LinkedHashMap<String,Object>();current.put("id",100L);current.put("tenantId",1L);current.put("version",12L);
        current.put("projectType","10");current.put("projectCode","P-0");current.put("projectName","正常业务修改后的名称");
        when(projects.inspectBindings(any())).thenReturn(List.of(DppmsProjectMasterSyncAdapter.decode(current)));
    }
    @AfterEach void cleanup(){TenantContextHolder.clear();}
    private Row row(String key){return new Row(OBJECT,key,new LinkedHashMap<>(Map.of("originalProjectId",key,"canonicalProjectId","12","projectType","10","projectCode","P-0")),null);}
    private Batch batch(List<Row> rows,List<Binding> prior){return new Batch("integration:1:12",rows,prior,false,"RETAIN",false,"UPSERT",false);}
    @Test void entireSourceIdentityUsesOneCanonicalTargetAndNeverUpdatesBusinessFacts(){
        var rows=List.of(row("10"),row("11"),row("12"));var changes=adapter.apply(batch(rows,List.of()));
        assertEquals(3,changes.size());assertTrue(changes.stream().allMatch(c->c.targetId()==100L && "UNCHANGED".equals(c.action())));
        verify(projects).lockBindings(argThat(q->q.actorId()==7 && q.projects().size()==1 && q.projects().getFirst().id()==100
                && q.projects().getFirst().version()==12 && "正常业务修改后的名称".equals(q.projects().getFirst().projectName())));
        verify(projects,never()).apply(any());verify(projects,never()).preview(any());
        var replayRows=new ArrayList<Row>();var prior=new ArrayList<Binding>();
        for(var c:changes){var source=rows.stream().filter(r->r.sourceKey().equals(c.sourceKey())).findFirst().orElseThrow();
            replayRows.add(new Row(OBJECT,source.sourceKey(),source.fields(),c.targetId()));prior.add(new Binding(OBJECT,c.sourceKey(),c.targetId(),c.after()));}
        assertEquals(changes.stream().map(Change::after).toList(),adapter.apply(batch(replayRows,prior)).stream().map(Change::after).toList());
    }
    @Test void wrongIdentityMissingMasterStaleRunAndOwnerChangesBlockBeforeBindingResults(){
        var row=row("11");row.fields().put("projectCode","different");
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(List.of(row),List.of())));
        when(bindings.selectIdentitySources(any())).thenReturn(List.of());
        assertEquals("ISSUE",adapter.apply(batch(List.of(row("11")),List.of())).getFirst().action());
        run.setConfigVersion(2);assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(List.of(row("11")),List.of())));
        verifyNoInteractions(projects);
    }
    @Test void existingSourceCannotRebindToAnotherFormalProject(){
        var source=row("11");var initial=adapter.apply(batch(List.of(source),List.of())).getFirst();
        clearInvocations(projects);
        var bound=new Row(OBJECT,"11",source.fields(),101L);
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(List.of(bound),List.of(new Binding(OBJECT,"11",101L,initial.after())))));
        verifyNoInteractions(projects);
    }
    @Test void approvedIdentityRestorationReusesVerifiedBindingAndRejectsChangedReceipt(){
        var source=row("11");var saved=adapter.apply(batch(List.of(source),List.of())).getFirst().after();
        var retired=new SyncBindingDO().setId(81L).setTaskId(12L).setSourceKey("11").setTargetId(100L)
                .setLastRunId(80L).setFieldsJson(JsonUtils.toJsonString(saved));retired.setTenantId(1L);
        when(bindings.selectRetiredSourceKeysForUpdate(argThat(q->q!=null && "pm_project".equals(q.sourceObject())
                && OBJECT.equals(q.object()) && q.sourceKeys().equals(List.of("11"))))).thenReturn(List.of(retired));
        when(bindings.restoreVerifiedBinding(retired)).thenReturn(1);
        var receipt=new SyncRunDO().setId(80L).setTaskId(12L).setPreview(false).setStatus("SUCCESS");receipt.setTenantId(1L);
        when(runs.selectScoped(argThat(q->q!=null && Long.valueOf(80).equals(q.id())))).thenReturn(receipt);
        source.fields().put("migrationRestoration",Map.of("retiredBindingId",81L,"lastRunId",80L,"targetId",100L));
        adapter.apply(batch(List.of(source),List.of()));verify(bindings).restoreVerifiedBinding(retired);
        clearInvocations(bindings);run.setPreview(true).setStatus("VALIDATING");
        adapter.preview(batch(List.of(source),List.of()));verify(bindings,never()).restoreVerifiedBinding(any());
        run.setPreview(false).setStatus("APPLYING");receipt.setStatus("FAILED");clearInvocations(projects);
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(List.of(source),List.of())));verifyNoInteractions(projects);
    }
    @Test void templateIsEditableAndPreviewUsesFormalOwnerReadProtection(){
        var definition=create(18L);assertEquals("10",definition.sources().getFirst().parameters().get("projectTypes"));
        assertDoesNotThrow(()->new SyncDefinitionValidator(List.of(adapter)).validate(definition));
        assertDoesNotThrow(()->MysqlSyncReader.compile(definition.sources().getFirst()));
        run.setPreview(true);run.setStatus("VALIDATING");adapter.preview(batch(List.of(row("11")),List.of()));
        verify(projects).verifyBindings(any());verify(projects,never()).lockBindings(any());
        doThrow(new IllegalArgumentException("TARGET_CHANGED")).when(projects).verifyBindings(any());
        assertThrows(IllegalArgumentException.class,()->adapter.preview(batch(List.of(row("11")),List.of())));
    }
}
