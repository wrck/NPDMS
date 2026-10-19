package cn.iocoder.yudao.module.pms.integration.sync;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsDeviceBootstrapApi;
import cn.iocoder.yudao.module.pms.engineering.api.installation.InstallationHistoryInsertApi;
import cn.iocoder.yudao.module.pms.integration.api.sync.*;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.*;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.pms.project.api.migration.ProjectMasterMigrationApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class DppmsDeviceAssignmentServiceTest {
    SyncTaskService taskService;SyncTaskMapper tasks;SyncRunMapper runs;SyncBindingMapper bindings;
    DppmsDeviceBootstrapApi devices;InstallationHistoryInsertApi installations;DppmsPublishedProjectScopeApi published;
    ProjectMasterMigrationApi projects;PermissionApi permissions;DppmsDeviceAssignmentService service;
    SyncTaskDO baseTask,historyTask;SyncRunDO snapshot;DppmsDeviceAssignmentService.Request request;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                new cn.iocoder.yudao.framework.security.core.LoginUser().setId(2L).setTenantId(1L),null,List.of()));
        taskService=mock(SyncTaskService.class);tasks=mock(SyncTaskMapper.class);runs=mock(SyncRunMapper.class);bindings=mock(SyncBindingMapper.class);
        devices=mock(DppmsDeviceBootstrapApi.class);installations=mock(InstallationHistoryInsertApi.class);published=mock(DppmsPublishedProjectScopeApi.class);
        projects=mock(ProjectMasterMigrationApi.class);permissions=mock(PermissionApi.class);
        service=new DppmsDeviceAssignmentService(taskService,tasks,runs,bindings,devices,installations,published,projects,permissions);
        when(permissions.hasAnyPermissions(2L,"pms:integration:execute")).thenReturn(true);
        baseTask=new SyncTaskDO().setId(30L).setVersion(3);historyTask=new SyncTaskDO().setId(33L).setVersion(4);
        when(taskService.locked(30L)).thenReturn(baseTask);when(taskService.definition(baseTask)).thenReturn(DppmsDeviceBootstrapSyncTemplate.create(7L));
        when(tasks.selectSourceAdapter(any())).thenReturn(List.of(historyTask));when(tasks.selectForUpdate(any())).thenReturn(historyTask);
        snapshot=new SyncRunDO().setId(330L).setStatus("SUCCESS").setFullSnapshot(true).setConfigVersion(4).setSummaryJson("{}")
            .setConfigSnapshot(JsonUtils.toJsonString(DppmsInstallationSyncTemplate.create(7L)));
        when(runs.selectLatestFormalRoot(any())).thenReturn(snapshot);
        var seed=new DppmsDeviceBootstrapApi.Seed("SN",5L,"raw","C","P",LocalDateTime.of(2020,1,1,0,0),null,null,null);
        when(devices.snapshot(any())).thenReturn(List.of(seed));
        when(devices.previewBase(any())).thenReturn(List.of(new DppmsDeviceBootstrapApi.BaseResult("SN",10L,"UNCHANGED",5L,0L)));
        var fields=Map.<String,Object>of("originalFields",Map.of("barcode","SN","id","88","projectId","9","contractNo","C"),
            "projectId",20L,"equipmentId",10L,"formalFieldsJson","{\"id\":40}");
        var binding=new SyncBindingDO().setId(100L).setTargetId(40L).setSourceKey("88").setFieldsJson(JsonUtils.toJsonString(fields));
        when(bindings.selectInstallationSelectionPage(any())).thenAnswer(call->{SyncQueries.InstallationSelection q=call.getArgument(0);return q.afterId()==null?List.of(binding):List.of();});
        when(published.resolve(any())).thenReturn(new DppmsPublishedProjectScopeApi.Scope(160L,List.of(new DppmsProjectBindingApi.ProjectBinding(7L,"9",20L,"10","OLD","NEW"))));
        // Identity payload is immaterial to this consumer; the Owner still receives the exact inspected collection.
        when(projects.inspectBindings(any())).thenAnswer(call->{
            var target=call.<ProjectMasterMigrationApi.IdentityInspection>getArgument(0).projects().getFirst();
            return List.of(JsonUtils.parseObject("{\"id\":"+target.projectId()+"}",ProjectMasterMigrationApi.Facts.class));
        });
        when(installations.verify(any())).thenReturn(List.of(new InstallationHistoryInsertApi.Result("88",40L,"OK",null,"{\"id\":40}")));
        when(devices.previewAssignment(any())).thenReturn(new DppmsDeviceBootstrapApi.AssignmentResult(10L,20L,"UPDATED",1L));
        when(devices.applyAssignment(any())).thenReturn(new DppmsDeviceBootstrapApi.AssignmentResult(10L,20L,"UPDATED",1L));
        request=new DppmsDeviceAssignmentService.Request(30L,3,List.of("SN"),LocalDateTime.of(2026,10,16,0,0),null);
    }
    @AfterEach void clear(){TenantContextHolder.clear();org.springframework.security.core.context.SecurityContextHolder.clearContext();}
    @Test void previewIsReadOnlyAndApplyRevalidatesSameDigest() {
        var plan=service.preview(request);verify(devices,never()).applyAssignment(any());
        var applied=service.apply(new DppmsDeviceAssignmentService.Request(30L,3,List.of("SN"),request.effectiveAt(),plan.digest()));
        assertEquals(330L,applied.installationRunId());assertEquals(1L,applied.items().getFirst().assignmentVersion());
        verify(installations,times(2)).verify(any());verify(devices).applyAssignment(any());
    }
    @Test void emptyScopeAndUnauthorizedRequestsCannotReachOwners() {
        assertThrows(IllegalArgumentException.class,()->service.preview(new DppmsDeviceAssignmentService.Request(30L,3,List.of(),request.effectiveAt(),null)));
        when(permissions.hasAnyPermissions(2L,"pms:integration:execute")).thenReturn(false);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.preview(request));verifyNoInteractions(devices,installations);
    }
    @Test void failedPartialActiveAndRestrictedInstallationInputsAreRejected() {
        snapshot.setStatus("FAILED");assertThrows(IllegalArgumentException.class,()->service.preview(request));snapshot.setStatus("SUCCESS");
        snapshot.setSummaryJson("{\"ISSUE\":1}");assertThrows(IllegalArgumentException.class,()->service.preview(request));snapshot.setSummaryJson("{}");
        historyTask.setActiveRunId(331L);assertThrows(IllegalArgumentException.class,()->service.preview(request));historyTask.setActiveRunId(null);
        snapshot.setConfigSnapshot(JsonUtils.toJsonString(DppmsInstallationSyncTemplate.create(8L)));
        assertThrows(IllegalArgumentException.class,()->service.preview(request));verifyNoInteractions(devices,installations);
    }
    @Test void previewCannotInventMissingDeviceMaster() {
        when(devices.previewBase(any())).thenReturn(List.of(new DppmsDeviceBootstrapApi.BaseResult("SN",-1L,"CREATED",5L,0L)));
        assertThrows(IllegalArgumentException.class,()->service.preview(request));verifyNoInteractions(installations);verify(devices,never()).applyAssignment(any());
    }
    @Test void changedPreviewDigestCannotWrite() {
        assertThrows(IllegalArgumentException.class,()->service.apply(new DppmsDeviceAssignmentService.Request(30L,3,List.of("SN"),request.effectiveAt(),"stale")));
        verify(devices,never()).applyAssignment(any());
    }
    @Test void changedImmutableInstallationCannotWrite() {
        doThrow(new IllegalArgumentException("BOUND_RECORD_CHANGED")).when(installations).verify(any());
        assertThrows(IllegalArgumentException.class,()->service.preview(request));verify(devices,never()).applyAssignment(any());
    }
    @Test void missingHistoryAndChangedOwnerEvidenceCannotWrite() {
        doReturn(List.of()).when(bindings).selectInstallationSelectionPage(any());
        assertThrows(IllegalArgumentException.class,()->service.preview(request));verify(devices,never()).applyAssignment(any());
    }
}
