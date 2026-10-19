package cn.iocoder.yudao.module.pms.integration.basesync.task;

import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import cn.iocoder.yudao.module.pms.integration.basesync.BaseSyncProjectReferences;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformBusinessEventApi;
import cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi;
import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OriginalIdentityPolicyTest {
    SyncContext<P16ProjectSyncTask.Parameters> context() {
        var identities=mock(SyncIdentityStore.class);
        when(identities.findByKeys(any())).thenReturn(Map.of());
        return new SyncContext<>(1,2,3,true,500,1000000,LocalDateTime.of(2026,10,19,0,0),
                mock(SyncSource.class),identities,mock(SyncReferences.class),"ProjectMaster",mock(SqlSession.class),false,true)
                .forParameters(new P16ProjectSyncTask.Parameters());
    }
    P16ProjectSyncTask.Source source() {
        return new P16ProjectSyncTask.Source().setProjectId(7L).setProjectType("10")
                .setProjectCode("ORIGINAL-7").setProjectState("30").setCreateBy(" 未开户User ")
                .setUpdateBy("原用户名").setCreateTime(LocalDateTime.of(2020,1,1,0,0));
    }
    P16ProjectSyncTask task(P16ProjectSyncTask.TargetMapper mapper) {
        var permissions=mock(PermissionCommonApi.class);when(permissions.hasAnyRoles(anyLong(),any())).thenReturn(true);
        return new P16ProjectSyncTask(mapper,permissions,mock(OrganizationScopeApi.class),mock(PlatformBusinessEventApi.class));
    }
    @Test void newMasterKeepsSourcePrimaryKeyAndRawAuditNamesWithoutAccountBindings() {
        var mapper=mock(P16ProjectSyncTask.TargetMapper.class);var row=source();var c=context();
        task(mapper).syncDataInsertBefore(new SyncBatch<>(c,List.of(row)));
        assertEquals(7L,row.getTarget().getId());assertEquals(7L,row.getTarget().getRootId());assertEquals(7L,row.getTarget().getCodeRootId());
        assertEquals(" 未开户User ",row.getTarget().getCreator());assertEquals("原用户名",row.getTarget().getUpdater());
        verifyNoInteractions(c.references());
    }
    @Test void unownedExistingSourceIdCannotBeOverwrittenEvenWithAnotherProjectCode() {
        var mapper=mock(P16ProjectSyncTask.TargetMapper.class);
        when(mapper.selectProjects(any())).thenReturn(List.of(new P16ProjectSyncTask.Row().setId(7L).setProjectCode("OTHER")));
        assertTrue(assertThrows(IllegalArgumentException.class,()->task(mapper).syncDataInsertBefore(new SyncBatch<>(context(),List.of(source()))))
                .getMessage().contains("PROJECT_ID_ALREADY_OWNED"));
    }
    @Test void previewUsesOriginalProjectIdAndExecutionStillRequiresActualParent() {
        var mapper=mock(BaseSyncProjectReferences.ReferenceMapper.class);var scope=mock(ProjectScopeApi.class);
        var references=new BaseSyncProjectReferences(mapper,scope);
        var rows=references.resolve(new BaseSyncProjectReferences.Query(1,2,3,List.of("7"),true));
        assertEquals(7L,rows.get("7").id());assertNull(rows.get("7").version());
        assertThrows(IllegalStateException.class,()->references.resolve(new BaseSyncProjectReferences.Query(1,2,3,List.of("7"),false)));
        verifyNoInteractions(scope);
    }
    @Test void existingProjectStillRequiresCurrentManagePermission() {
        var mapper=mock(BaseSyncProjectReferences.ReferenceMapper.class);var scope=mock(ProjectScopeApi.class);
        when(mapper.selectCurrentProjects(any())).thenReturn(List.of(new BaseSyncProjectReferences.Identity("7",7L,1L,0L,"P7","10",null,null,null,"ACTIVE",false)));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                ()->new BaseSyncProjectReferences(mapper,scope).resolve(new BaseSyncProjectReferences.Query(1,2,3,List.of("7"),true)));
    }
    @Test void relationUsesSourceCompanyAndStillRequiresAnActualContract() {
        var mapper=mock(P18ProjectContractSyncTask.TargetMapper.class);var projects=mock(BaseSyncProjectReferences.class);
        when(projects.resolve(any())).thenReturn(Map.of("7",new BaseSyncProjectReferences.Identity("7",7L,1L,null,null,"10",null,null,null,null,false)));
        var task=new P18ProjectContractSyncTask(mapper,projects,mock(OrganizationScopeApi.class));
        var source=new P18ProjectContractSyncTask.Source().setContractSourceId(1L).setMemberId(2L).setOriginalProjectId(7L).setOriginalCompanyCode("01").setContractNo("C");
        var c=context().forParameters(new P18ProjectContractSyncTask.Parameters(Set.of("001")));
        assertTrue(assertThrows(IllegalArgumentException.class,()->task.syncDataInsertBefore(new SyncBatch<>(c,List.of(source)))).getMessage().contains("FORMAL_CONTRACT_MISSING:C"));
        var query=org.mockito.ArgumentCaptor.forClass(P18ProjectContractSyncTask.ContractsQuery.class);verify(mapper).selectContracts(query.capture());
        assertEquals(List.of(new P18ProjectContractSyncTask.ContractKey("001","C")),query.getValue().keys());
        source.setOriginalCompanyCode("02");
        assertTrue(assertThrows(IllegalArgumentException.class,()->task.syncDataInsertBefore(new SyncBatch<>(c,List.of(source)))).getMessage().contains("PROJECT_COMPANY_NOT_AUTHORIZED"));
        source.setOriginalCompanyCode("01").setOriginalProjectCode("SOURCE");
        when(projects.resolve(any())).thenReturn(Map.of("7",new BaseSyncProjectReferences.Identity("7",7L,1L,0L,"OTHER","10",null,"001",null,"ACTIVE",false)));
        assertTrue(assertThrows(IllegalArgumentException.class,()->task.syncDataInsertBefore(new SyncBatch<>(c,List.of(source)))).getMessage().contains("PROJECT_CODE_DIFFERS_FROM_SOURCE"));
    }
}
