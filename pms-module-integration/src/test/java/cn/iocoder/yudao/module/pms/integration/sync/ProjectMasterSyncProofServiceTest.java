package cn.iocoder.yudao.module.pms.integration.sync;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.ProjectMasterSyncProofApi;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.*;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.pms.project.api.migration.ProjectMasterMigrationApi;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.module.pms.integration.sync.DppmsProjectMasterSyncTemplate.*;

class ProjectMasterSyncProofServiceTest {
    SyncTaskMapper tasks=mock(SyncTaskMapper.class);
    SyncBindingMapper bindings=mock(SyncBindingMapper.class);
    SyncRunMapper runs=mock(SyncRunMapper.class);
    ProjectMasterMigrationApi projects=mock(ProjectMasterMigrationApi.class);
    ProjectMasterSyncProofService service=new ProjectMasterSyncProofService(tasks,bindings,runs,projects);
    ProjectMasterSyncProofApi.Query query=new ProjectMasterSyncProofApi.Query(1L,7L,List.of("10"));
    SyncTaskDO task;SyncBindingDO binding;SyncRunDO run;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        task=new SyncTaskDO().setId(12L).setSourceSystem("DPPMS").setAdapter(ADAPTER);task.setTenantId(1L);
        when(tasks.selectIdentity(any())).thenReturn(task);
        run=new SyncRunDO();run.setId(30L);run.setTenantId(1L);run.setTaskId(12L);run.setPreview(false);run.setStatus("SUCCESS");
        when(runs.selectScoped(any())).thenReturn(run);
        Map<String,Object> raw=new LinkedHashMap<>();ORIGINAL_COLUMNS.forEach(k->raw.put(k,tools.jackson.databind.node.NullNode.getInstance()));
        raw.put("projectId","10");raw.put("column001","162007");
        var formal=new LinkedHashMap<String,Object>();formal.put("id",40L);formal.put("tenantId",1L);formal.put("version",2);
        formal.put("companyCode","001");formal.put("projectCode","FORMAL");formal.put("createTime","2018-01-02T03:04:05.123");
        formal.put("updateTime","2019-01-02T03:04:05.456");formal.put("disabled",false);formal.put("lifecycleStatus","NORMAL_CLOSED");
        binding=new SyncBindingDO().setTaskId(12L).setSourceObject(OBJECT).setObjectKey(OBJECT).setSourceKey("10")
            .setTargetId(40L).setLastRunId(30L).setFieldsJson(JsonUtils.toJsonString(Map.of("originalFields",raw,"project",formal)));
        binding.setTenantId(1L);when(bindings.selectSourceKeys(any())).thenReturn(List.of(binding));
        when(projects.preview(any())).thenAnswer(call->{
            ProjectMasterMigrationApi.Request request=call.getArgument(0);var entry=request.entries().getFirst();
            assertEquals("integration:1:12",request.owner());assertEquals(7L,request.actorId());assertFalse(request.adoptExisting());
            assertEquals("162007",entry.departmentCode());assertEquals(LocalDateTime.parse("2018-01-02T03:04:05.123"),entry.lastApplied().createTime());
            return List.of(new ProjectMasterMigrationApi.Result("10","UNCHANGED",entry.lastApplied(),entry.lastApplied(),"verified"));
        });
    }
    @AfterEach void clear(){TenantContextHolder.clear();}
    @Test void formalOwnerAndSuccessfulWriteReceiptAreRequiredWithoutNewWrites() {
        var proof=service.verify(query).getFirst();assertEquals(40L,proof.targetId());assertEquals(37,proof.originalFields().size());
        assertTrue(proof.originalFields().containsKey("customConfig"));assertNull(proof.originalFields().get("customConfig"));
        verify(projects,never()).apply(any());
        verify(bindings).selectSourceKeys(argThat(q->q.tenantId()==1L&&q.taskId()==12L&&OBJECT.equals(q.object())));
    }
    @Test void emptyScopeReturnsEmptyAndInvalidTenantOrIdentityNeverQueries() {
        assertEquals(List.of(),service.verify(new ProjectMasterSyncProofApi.Query(1L,7L,List.of())));
        assertThrows(IllegalArgumentException.class,()->service.verify(new ProjectMasterSyncProofApi.Query(2L,7L,List.of("10"))));
        assertThrows(IllegalArgumentException.class,()->service.verify(new ProjectMasterSyncProofApi.Query(1L,7L,List.of("010"))));
        assertThrows(IllegalArgumentException.class,()->service.verify(new ProjectMasterSyncProofApi.Query(1L,7L,List.of("10","10"))));
        verifyNoInteractions(tasks,bindings,runs,projects);
    }
    @Test void previewFailedOrForeignTaskRunIsNeverCarryEvidence() {
        run.setPreview(true);assertThrows(IllegalStateException.class,()->service.verify(query));
        run.setPreview(false);run.setStatus("FAILED");assertThrows(IllegalStateException.class,()->service.verify(query));
        run.setStatus("SUCCESS");run.setTaskId(13L);assertThrows(IllegalStateException.class,()->service.verify(query));
        run.setTaskId(12L);run.setTenantId(2L);assertThrows(IllegalStateException.class,()->service.verify(query));
        verifyNoInteractions(projects);
    }
    @Test void missingDuplicateCrossTenantAndIncompleteOriginalBindingsAreRejected() {
        when(bindings.selectSourceKeys(any())).thenReturn(List.of());assertThrows(IllegalStateException.class,()->service.verify(query));
        when(bindings.selectSourceKeys(any())).thenReturn(List.of(binding,binding));assertThrows(IllegalStateException.class,()->service.verify(query));
        when(bindings.selectSourceKeys(any())).thenReturn(List.of(binding));binding.setTenantId(2L);
        assertThrows(IllegalStateException.class,()->service.verify(query));binding.setTenantId(1L);
        binding.setFieldsJson("{\"originalFields\":{\"projectId\":\"10\"},\"project\":{\"id\":40}}");
        assertThrows(IllegalStateException.class,()->service.verify(query));verifyNoInteractions(projects);
    }
    @Test void laterBusinessModificationOrMissingOwnerResultCannotClaimUnchangedCarry() {
        doReturn(List.of(new ProjectMasterMigrationApi.Result("10","ISSUE",null,null,"changed"))).when(projects).preview(any());
        assertThrows(IllegalStateException.class,()->service.verify(query));
        doReturn(List.of()).when(projects).preview(any());assertThrows(IllegalStateException.class,()->service.verify(query));
        verify(projects,never()).apply(any());
    }
}
