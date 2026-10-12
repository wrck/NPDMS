package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.*;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.*;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.pms.project.api.migration.ProjectMasterMigrationApi;
import cn.iocoder.yudao.module.pms.project.api.migration.ProjectMasterMigrationApi.*;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.module.pms.integration.sync.DppmsProjectMasterSyncTemplate.*;

class DppmsProjectMasterSyncAdapterTest {
    ProjectMasterMigrationApi projects=mock(ProjectMasterMigrationApi.class);
    SyncTaskService tasks=mock(SyncTaskService.class);
    SyncRunMapper runs=mock(SyncRunMapper.class);
    SyncBindingMapper bindings=mock(SyncBindingMapper.class);
    DppmsProjectMasterSyncAdapter adapter=new DppmsProjectMasterSyncAdapter(projects,tasks,runs,bindings);
    SyncTaskDO task;SyncRunDO run;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        task=new SyncTaskDO().setId(12L).setVersion(1).setActiveRunId(13L).setConnectionId(18L)
                .setAdapter(ADAPTER).setSourceSystem("DPPMS").setDefinition(JsonUtils.toJsonString(create(18L)));task.setTenantId(1L);
        run=new SyncRunDO().setId(13L).setTaskId(12L).setConfigVersion(1).setStatus("APPLYING").setPreview(false);
        run.setTenantId(1L);run.setCreator("7");when(tasks.required(12L)).thenReturn(task);when(runs.selectScoped(any())).thenReturn(run);
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }
    private Row row() {
        Map<String,Object> fields=new LinkedHashMap<>();ORIGINAL_COLUMNS.forEach(c->fields.put(c,null));
        ACTOR_COLUMNS.forEach(c->fields.put(c,null));fields.put("projectId","10");fields.put("projectType","10");
        fields.put("migrationCompanyCode","01");fields.put("compId","1");fields.put("expectedProjectVersion",3);fields.put("column001","311202");
        fields.put("createTime","2023-01-02 03:04:05");fields.put("customInfo","{\"amount\":2}");
        return new Row(OBJECT,"10",fields,null);
    }
    private Batch batch(Row row,List<Binding> previous) { return new Batch("integration:1:12",List.of(row),previous,false,"RETAIN",true,"UPSERT",false); }
    private Facts facts() {
        LocalDateTime date=LocalDateTime.of(2023,1,2,3,4,5);
        return new Facts(40L,1L,4L,"OLD-CODE","Name",1L,"001","Company","10","GENERAL","DIRECT_SIGN",
                "SUPERVISION",null,"01",null,null,null,null,null,null,null,null,null,null,"{\"amount\":2}",false,
                null,null,null,date,null,date,"NORMAL_CLOSED","S6",date);
    }
    @Test void templateUsesAllOriginalColumnsAndCurrentSyncValidation() {
        var definition=create(18L);assertDoesNotThrow(()->new SyncDefinitionValidator(List.of(adapter)).validate(definition));
        assertDoesNotThrow(()->MysqlSyncReader.compile(definition.sources().getFirst()));
        assertEquals(37,ORIGINAL_COLUMNS.size());assertFalse(adapter.descriptor().supportsTargetClear());
    }
    @Test void companyAndDepartmentRulesReachOwnerWithoutDiscardingOriginalFields() {
        when(projects.apply(any())).thenAnswer(call->{Request r=call.getArgument(0);var e=r.entries().getFirst();
            assertEquals("001",e.companyCode());assertEquals("162102",e.departmentCode());assertEquals(3L,e.expectedProjectVersion());
            assertEquals(37,e.originalFields().size());assertNull(e.originalFields().get("updateTime"));
            assertEquals("{\"amount\":2}",e.originalFields().get("customInfo"));assertNull(e.creator());assertNull(e.updater());
            return List.of(new Result("10","UPDATED",facts(),facts(),"正式承接"));});
        var result=adapter.apply(batch(row(),List.of())).getFirst();assertEquals(40L,result.targetId());
        assertEquals(37,((Map<?,?>)result.after().get("originalFields")).size());
    }
    @Test void bindingRoundTripPreservesFormalDatesAndOriginalNullColumns() {
        when(projects.apply(any())).thenAnswer(call->{Request r=call.getArgument(0);var e=r.entries().getFirst();
            if (e.lastApplied()!=null) assertEquals(facts().createTime(),e.lastApplied().createTime());
            return List.of(new Result("10",e.lastApplied()==null?"UPDATED":"UNCHANGED",facts(),facts(),"承接"));});
        var initial=adapter.apply(batch(row(),List.of())).getFirst();
        Map<String,Object> saved=JsonUtils.parseMap(JsonUtils.toJsonString(initial.after()));
        var raw=row();var bound=new Row(OBJECT,"10",raw.fields(),40L);
        assertEquals("UNCHANGED",adapter.apply(batch(bound,List.of(new Binding(OBJECT,"10",40L,saved)))).getFirst().action());
        raw.fields().put("projectName","新的来源内容");clearInvocations(projects);
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(bound,List.of(new Binding(OBJECT,"10",40L,saved)))));
        verifyNoInteractions(projects);
    }
    @Test void unknownCompanyAndMissingOriginalColumnsAreBlockedBeforeOwner() {
        var raw=row();raw.fields().put("migrationCompanyCode","99");
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(raw,List.of())));
        raw.fields().put("migrationCompanyCode","01");raw.fields().remove("customConfig");
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(raw,List.of())));verifyNoInteractions(projects);
    }
    @Test void nullCompanyPassesToOwnerOnlyWhenTheOriginalCompanyIsNull() {
        var raw=row();raw.fields().put("compId",null);raw.fields().put("migrationCompanyCode",null);
        when(projects.apply(any())).thenAnswer(call->{Request r=call.getArgument(0);
            assertNull(r.entries().getFirst().companyCode());assertNull(r.entries().getFirst().originalFields().get("compId"));
            return List.of(new Result("10","ISSUE",null,null,"正式Owner处理"));});
        adapter.apply(batch(raw,List.of()));clearInvocations(projects);
        raw.fields().put("compId","1");
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(raw,List.of())));
        raw.fields().put("compId",null);raw.fields().put("migrationCompanyCode","01");
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(raw,List.of())));
        verifyNoInteractions(projects);
    }
    @Test void fndAuditBindingWinsOverSupplementaryTIdentity() {
        var raw=row();raw.fields().put("createBy","x01861");raw.fields().put("creatorFndSourceKey",78);
        raw.fields().put("creatorTSourceKey",900);raw.fields().put("creatorFndMatchCount",1);raw.fields().put("creatorTMatchCount",1);
        var b=new SyncBindingDO();b.setSourceKey("78");b.setTargetId(41L);
        when(bindings.selectIdentitySources(any())).thenReturn(List.of(b));
        when(projects.apply(any())).thenAnswer(call->{Request r=call.getArgument(0);assertEquals("41",r.entries().getFirst().creator());
            return List.of(new Result("10","UPDATED",facts(),facts(),"承接"));});
        adapter.apply(batch(raw,List.of()));verify(bindings).selectIdentitySources(argThat(q->"fnd_user_info".equals(q.sourceObject())));
    }
    @Test void staleRunAndUnmigratedAuditUserCannotReachOwner() {
        run.setConfigVersion(2);assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(row(),List.of())));
        run.setConfigVersion(1);var raw=row();raw.fields().put("createBy","unknown");
        raw.fields().put("creatorFndMatchCount",0);raw.fields().put("creatorTMatchCount",0);
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(raw,List.of())));verifyNoInteractions(projects);
    }
    @Test void mergedDuplicateAccountsMustAllBindToTheSameFormalAuditUser() {
        var raw=row();raw.fields().put("createBy","c00733");raw.fields().put("creatorFndMatchCount",2);
        raw.fields().put("creatorFndSourceKeys","233,408");raw.fields().put("creatorTMatchCount",0);
        var first=new SyncBindingDO().setSourceKey("233").setTargetId(41L);var second=new SyncBindingDO().setSourceKey("408").setTargetId(41L);
        when(bindings.selectIdentitySources(any())).thenReturn(List.of(first,second));
        when(projects.apply(any())).thenAnswer(call->{Request request=call.getArgument(0);assertEquals("41",request.entries().getFirst().creator());
            return List.of(new Result("10","UPDATED",facts(),facts(),"承接"));});
        adapter.apply(batch(raw,List.of()));clearInvocations(projects);second.setTargetId(42L);
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(raw,List.of())));verifyNoInteractions(projects);
    }
    @Test void approvedOriginalAuditCodesDoNotCreateOrSelectAnUnmigratedAccount() {
        var raw=row();raw.fields().put("createBy","sys");raw.fields().put("creatorHistoricalCode","sys");
        when(projects.apply(any())).thenAnswer(call->{Request request=call.getArgument(0);assertEquals("sys",request.entries().getFirst().creator());
            return List.of(new Result("10","CREATED",null,facts(),"承接"));});
        assertEquals(40L,adapter.apply(batch(raw,List.of())).getFirst().targetId());verifyNoInteractions(bindings);
        clearInvocations(projects);raw.fields().put("creatorHistoricalCode","admin");
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(raw,List.of())));verifyNoInteractions(projects);
    }
}
