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
    @Test void relationUsesSourceCompanyAndRequiresAnActualContractForExecution() {
        var mapper=mock(P18ProjectContractSyncTask.TargetMapper.class);var projects=mock(BaseSyncProjectReferences.class);
        when(projects.resolve(any())).thenReturn(Map.of("7",new BaseSyncProjectReferences.Identity("7",7L,1L,null,null,"10",null,null,null,null,false)));
        var task=new P18ProjectContractSyncTask(mapper,projects,mock(OrganizationScopeApi.class));
        var source=new P18ProjectContractSyncTask.Source().setContractSourceId(1L).setMemberId(2L).setOriginalProjectId(7L).setOriginalCompanyCode("01").setContractNo("C");
        var base=context();var c=new SyncContext<Void>(base.tenantId(),base.actorId(),base.connectionId(),false,base.batchSize(),base.maxRows(),base.startedAt(),base.source(),base.identities(),base.references(),base.dataName(),base.target(),false,true).forParameters(new P18ProjectContractSyncTask.Parameters(Set.of("001")));
        assertTrue(assertThrows(IllegalArgumentException.class,()->task.syncDataInsertBefore(new SyncBatch<>(c,List.of(source)))).getMessage().contains("FORMAL_CONTRACT_MISSING:C"));
        var query=org.mockito.ArgumentCaptor.forClass(P18ProjectContractSyncTask.ContractsQuery.class);verify(mapper).selectContracts(query.capture());
        assertEquals(List.of(new P18ProjectContractSyncTask.ContractKey("001","C")),query.getValue().keys());
        source.setOriginalCompanyCode("02");
        assertTrue(assertThrows(IllegalArgumentException.class,()->task.syncDataInsertBefore(new SyncBatch<>(c,List.of(source)))).getMessage().contains("PROJECT_COMPANY_NOT_AUTHORIZED"));
        source.setOriginalCompanyCode("01").setOriginalProjectCode("SOURCE");
        when(projects.resolve(any())).thenReturn(Map.of("7",new BaseSyncProjectReferences.Identity("7",7L,1L,0L,"OTHER","10",null,"001",null,"ACTIVE",false)));
        assertTrue(assertThrows(IllegalArgumentException.class,()->task.syncDataInsertBefore(new SyncBatch<>(c,List.of(source)))).getMessage().contains("PROJECT_CODE_DIFFERS_FROM_SOURCE"));
    }
    @Test void relationPreviewKeepsOriginalBusinessKeysWithoutInventingContractIdsOrWriteCounts() {
        var mapper=mock(P18ProjectContractSyncTask.TargetMapper.class);var projects=mock(BaseSyncProjectReferences.class);
        when(projects.resolve(any())).thenReturn(Map.of("7",new BaseSyncProjectReferences.Identity("7",7L,1L,null,null,"10",null,null,null,null,false)));
        var task=new P18ProjectContractSyncTask(mapper,projects,mock(OrganizationScopeApi.class));
        var known=new P18ProjectContractSyncTask.Source().setContractSourceId(1L).setMemberId(2L).setOriginalProjectId(7L).setOriginalCompanyCode("01").setContractNo("C");
        var missingCompany=new P18ProjectContractSyncTask.Source().setContractSourceId(3L).setMemberId(4L).setOriginalProjectId(7L).setContractNo("OTHER");
        var c=context().forParameters(new P18ProjectContractSyncTask.Parameters(Set.of("001")));var batch=new SyncBatch<>(c,List.of(known,missingCompany));
        assertDoesNotThrow(()->task.syncDataInsertBefore(batch));assertNull(known.getFormalContractId());assertNull(known.getTarget());assertNull(missingCompany.getCompanyCode());
        assertEquals("C",known.getContractNo());assertEquals("OTHER",missingCompany.getContractNo());assertEquals(new SyncCounts(2,0,0,0,0),task.syncDataCount(batch));
        assertTrue(c.runState().warnings().contains("P18_CONTRACT_REFERENCE_PENDING:2"));assertTrue(c.runState().warnings().contains("P18_COMPANY_SOURCE_PENDING:1"));
        verify(mapper,never()).insertProjectContract(any());verify(c.identities(),never()).saveAll(any());
        known.setOriginalCompanyCode("02");assertDoesNotThrow(()->task.syncDataInsertBefore(new SyncBatch<>(c,List.of(known))));
        assertTrue(c.runState().warnings().contains("P18_COMPANY_AUTHORIZATION_PENDING:002=1"));assertNull(known.getFormalProjectId());
    }
    @Test void relationPreviewCannotDeferAContractForAnExistingIndependentBinding() {
        var mapper=mock(P18ProjectContractSyncTask.TargetMapper.class);var projects=mock(BaseSyncProjectReferences.class);
        when(projects.resolve(any())).thenReturn(Map.of("7",new BaseSyncProjectReferences.Identity("7",7L,1L,null,null,"10",null,null,null,null,false)));
        var task=new P18ProjectContractSyncTask(mapper,projects,mock(OrganizationScopeApi.class));
        var source=new P18ProjectContractSyncTask.Source().setContractSourceId(1L).setMemberId(2L).setOriginalProjectId(7L).setOriginalCompanyCode("01").setContractNo("C");
        var c=context().forParameters(new P18ProjectContractSyncTask.Parameters(Set.of("001")));
        when(c.identities().findByKeys(any())).thenAnswer(call->{var q=call.<SyncIdentityStore.Query>getArgument(0);return q.object().equals("RELATION_BUSINESS")?Map.of(source.businessKey(),new SyncIdentityStore.Identity(source.businessKey(),99L,"baseline")):Map.of();});
        assertThrows(IllegalArgumentException.class,()->task.syncDataInsertBefore(new SyncBatch<>(c,List.of(source))));
        verify(mapper,never()).insertProjectContract(any());verify(c.identities(),never()).saveAll(any());
    }
    @Test void relationSourcePreviewWithoutCompanyGrantDoesNotReadFormalContractsOrInventWrites() {
        var mapper=mock(P18ProjectContractSyncTask.TargetMapper.class);var projects=mock(BaseSyncProjectReferences.class);
        when(projects.resolve(any())).thenReturn(Map.of("7",new BaseSyncProjectReferences.Identity("7",7L,1L,null,null,"10",null,null,null,null,false)));
        var task=new P18ProjectContractSyncTask(mapper,projects,mock(OrganizationScopeApi.class));
        var source=new P18ProjectContractSyncTask.Source().setContractSourceId(1L).setMemberId(2L).setOriginalProjectId(7L).setOriginalCompanyCode("01").setContractNo("C");
        var c=context().forParameters(new P18ProjectContractSyncTask.Parameters(Set.of("DPTECH-DEMO")));var batch=new SyncBatch<>(c,List.of(source));
        assertDoesNotThrow(()->task.syncDataInsertBefore(batch));
        assertEquals(new SyncCounts(1,0,0,0,0),task.syncDataCount(batch));
        assertEquals("001",source.getCompanyCode());assertEquals(7L,source.getOriginalProjectId());assertEquals("C",source.getContractNo());
        assertNull(source.getTarget());assertNull(source.getFormalProjectId());assertNull(source.getFormalContractId());
        assertTrue(c.runState().warnings().contains("P18_COMPANY_AUTHORIZATION_PENDING:001=1"));
        verify(projects).resolve(any());verifyNoInteractions(mapper);verify(c.identities(),never()).saveAll(any());
    }
    @Test void relationEmptyCompanyScopesPermitOnlySourcePreview() {
        var organization=mock(OrganizationScopeApi.class);var mapper=mock(P18ProjectContractSyncTask.TargetMapper.class);
        var task=new P18ProjectContractSyncTask(mapper,mock(BaseSyncProjectReferences.class),organization);
        var preview=context();
        assertEquals(Set.of(),task.createParameters(preview).companies());
        when(organization.getActiveScopes(preview.actorId())).thenReturn(List.of());
        assertEquals(Set.of(),task.createParameters(preview).companies());
        var execution=new SyncContext<Void>(preview.tenantId(),preview.actorId(),preview.connectionId(),false,preview.batchSize(),preview.maxRows(),preview.startedAt(),preview.source(),preview.identities(),preview.references(),preview.dataName(),preview.target(),false,true);
        assertEquals("P18_AUTHORIZED_COMPANIES_EMPTY",assertThrows(IllegalArgumentException.class,()->task.createParameters(execution)).getMessage());
        assertEquals(Set.of("pms:commerce:contract:relate"),task.requiredPermissions());verifyNoInteractions(mapper);
    }
    @Test void relationMixedCompanyPreviewQueriesOnlyAuthorizedFormalContracts() {
        var mapper=mock(P18ProjectContractSyncTask.TargetMapper.class);var projects=mock(BaseSyncProjectReferences.class);
        when(projects.resolve(any())).thenReturn(Map.of("7",new BaseSyncProjectReferences.Identity("7",7L,1L,null,null,"10",null,null,null,null,false)));
        var task=new P18ProjectContractSyncTask(mapper,projects,mock(OrganizationScopeApi.class));
        var permitted=new P18ProjectContractSyncTask.Source().setContractSourceId(1L).setMemberId(2L).setOriginalProjectId(7L).setOriginalCompanyCode("01").setContractNo("AUTHORIZED");
        var ungranted=new P18ProjectContractSyncTask.Source().setContractSourceId(3L).setMemberId(4L).setOriginalProjectId(7L).setOriginalCompanyCode("02").setContractNo("PENDING");
        var unknownCompany=new P18ProjectContractSyncTask.Source().setContractSourceId(5L).setMemberId(6L).setOriginalProjectId(7L).setContractNo("NO-COMPANY");
        var c=context().forParameters(new P18ProjectContractSyncTask.Parameters(Set.of("001")));var batch=new SyncBatch<>(c,List.of(permitted,ungranted,unknownCompany));
        task.syncDataInsertBefore(batch);
        var query=org.mockito.ArgumentCaptor.forClass(P18ProjectContractSyncTask.ContractsQuery.class);verify(mapper).selectContracts(query.capture());
        assertEquals(1L,query.getValue().tenantId());assertTrue(query.getValue().preview());
        assertEquals(List.of(new P18ProjectContractSyncTask.ContractKey("001","AUTHORIZED")),query.getValue().keys());
        verifyNoMoreInteractions(mapper);
        assertEquals(new SyncCounts(3,0,0,0,0),task.syncDataCount(batch));
        assertTrue(c.runState().warnings().containsAll(List.of("P18_COMPANY_AUTHORIZATION_PENDING:002=1","P18_COMPANY_SOURCE_PENDING:1","P18_CONTRACT_REFERENCE_PENDING:3")));
        assertNull(ungranted.getTarget());assertNull(ungranted.getFormalProjectId());assertNull(ungranted.getFormalContractId());
    }
    @Test void relationPendingCompanyCannotHideExistingIndependentBindings() {
        for(var object:List.of("RELATION_BUSINESS","RELATION_ORIGINAL")) {
            var mapper=mock(P18ProjectContractSyncTask.TargetMapper.class);var projects=mock(BaseSyncProjectReferences.class);
            when(projects.resolve(any())).thenReturn(Map.of("7",new BaseSyncProjectReferences.Identity("7",7L,1L,null,null,"10",null,null,null,null,false)));
            var task=new P18ProjectContractSyncTask(mapper,projects,mock(OrganizationScopeApi.class));
            var source=new P18ProjectContractSyncTask.Source().setContractSourceId(1L).setMemberId(2L).setOriginalProjectId(7L).setOriginalCompanyCode("01").setContractNo("C");
            var c=context().forParameters(new P18ProjectContractSyncTask.Parameters(Set.of("DPTECH-DEMO")));
            var key=object.equals("RELATION_BUSINESS")?source.businessKey():source.sourceKey();
            when(c.identities().findByKeys(any())).thenAnswer(call->call.<SyncIdentityStore.Query>getArgument(0).object().equals(object)?Map.of(key,new SyncIdentityStore.Identity(key,99L,"baseline")):Map.of());
            var expected=object.equals("RELATION_BUSINESS")?"P18_BOUND_RELATION_COMPANY_AUTHORIZATION_REQUIRED":"P18_ORIGINAL_RELATION_CHANGED";
            assertEquals(expected,assertThrows(IllegalArgumentException.class,()->task.syncDataInsertBefore(new SyncBatch<>(c,List.of(source)))).getMessage());
            verifyNoInteractions(mapper);verify(c.identities(),never()).saveAll(any());
        }
    }
    @Test void relationPendingCompanyStillRequiresExistingProjectManagePermission() {
        var mapper=mock(P18ProjectContractSyncTask.TargetMapper.class);var projects=mock(BaseSyncProjectReferences.class);
        when(projects.resolve(any())).thenThrow(new org.springframework.security.access.AccessDeniedException("PROJECT_MANAGE_REQUIRED"));
        var task=new P18ProjectContractSyncTask(mapper,projects,mock(OrganizationScopeApi.class));
        var source=new P18ProjectContractSyncTask.Source().setOriginalProjectId(7L).setOriginalCompanyCode("01");
        var c=context().forParameters(new P18ProjectContractSyncTask.Parameters(Set.of("DPTECH-DEMO")));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->task.syncDataInsertBefore(new SyncBatch<>(c,List.of(source))));
        verifyNoInteractions(mapper);verify(c.identities(),never()).saveAll(any());
    }
    @Test void relationUnmappedSourceCompanyRemainsInvalidForPreviewAndExecution() {
        var mapper=mock(P18ProjectContractSyncTask.TargetMapper.class);var projects=mock(BaseSyncProjectReferences.class);
        when(projects.resolve(any())).thenReturn(Map.of("7",new BaseSyncProjectReferences.Identity("7",7L,1L,null,null,"10",null,null,null,null,false)));
        var task=new P18ProjectContractSyncTask(mapper,projects,mock(OrganizationScopeApi.class));
        var source=new P18ProjectContractSyncTask.Source().setContractSourceId(1L).setMemberId(2L).setOriginalProjectId(7L).setOriginalCompanyCode("99").setContractNo("C");
        var preview=context();
        for(boolean isPreview:List.of(true,false)) {
            var c=new SyncContext<Void>(preview.tenantId(),preview.actorId(),preview.connectionId(),isPreview,preview.batchSize(),preview.maxRows(),preview.startedAt(),preview.source(),preview.identities(),preview.references(),preview.dataName(),preview.target(),false,true).forParameters(new P18ProjectContractSyncTask.Parameters(Set.of("001")));
            assertTrue(assertThrows(IllegalArgumentException.class,()->task.syncDataInsertBefore(new SyncBatch<>(c,List.of(source)))).getMessage().contains("DPPMS_COMPANY_CODE_UNMAPPED"));
        }
        verifyNoInteractions(mapper);
    }
}
