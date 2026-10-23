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
    @Test void relationUsesExactSourceCompanyAndCarriesContractNumberBeforeReferencePostfill() {
        var mapper=mock(P18ProjectContractSyncTask.TargetMapper.class);
        var task=new P18ProjectContractSyncTask(mapper);
        var source=new P18ProjectContractSyncTask.Source().setContractSourceId(1L).setMemberId(2L).setOriginalProjectId(7L).setOriginalCompanyCode("01").setContractNo("C");source.setCanonical(source);
        var c=context().forParameters(new P18ProjectContractSyncTask.Parameters());var batch=new SyncBatch<>(c,List.of(source));
        task.syncDataInsertBefore(batch);assertEquals("001",source.getCompanyCode());assertEquals("C",source.getTarget().getContractNo());assertNull(source.getTarget().getContractId());assertEquals(new SyncCounts(1,1,0,0,0),task.syncDataCount(batch));
        source.setOriginalCompanyCode("02").setOriginalProjectCode("SOURCE-NAME-NOT-RELOOKED");
        task.syncDataInsertBefore(batch);assertEquals("002",source.getCompanyCode());assertEquals(7L,source.getFormalProjectId());assertEquals(7L,source.getTarget().getProjectId());
    }
    @Test void relationPreviewPlansRawBusinessKeysWithoutInventingContractIds() {
        var mapper=mock(P18ProjectContractSyncTask.TargetMapper.class);
        var task=new P18ProjectContractSyncTask(mapper);
        var known=new P18ProjectContractSyncTask.Source().setContractSourceId(1L).setMemberId(2L).setOriginalProjectId(7L).setOriginalCompanyCode("01").setContractNo("C");known.setCanonical(known);
        var missing=new P18ProjectContractSyncTask.Source().setContractSourceId(3L).setMemberId(4L).setOriginalProjectId(7L).setContractNo("OTHER");missing.setCanonical(missing);
        var c=context().forParameters(new P18ProjectContractSyncTask.Parameters());var batch=new SyncBatch<>(c,List.of(known,missing));task.syncDataInsertBefore(batch);
        for(var row:List.of(known,missing)){assertNotNull(row.getTarget());assertNull(row.getTarget().getId());assertNull(row.getTarget().getContractId());assertEquals(row.getContractNo(),row.getTarget().getContractNo());}
        assertEquals(new SyncCounts(2,2,0,0,0),task.syncDataCount(batch));assertNull(missing.getCompanyCode());
        assertTrue(c.runState().warnings().contains("P18_COMPANY_SOURCE_PENDING:1"));assertTrue(c.runState().warnings().contains("P18_CONTRACT_POSTFILL_NOT_EXECUTED"));
        verify(mapper,never()).fillContractReferences(any());verify(c.identities(),never()).saveAll(any());
    }
    @Test void relationPreviewRejectsMissingTargetForExistingBusinessBinding() {
        var mapper=mock(P18ProjectContractSyncTask.TargetMapper.class);
        var task=new P18ProjectContractSyncTask(mapper);var source=new P18ProjectContractSyncTask.Source().setContractSourceId(1L).setMemberId(2L).setOriginalProjectId(7L).setOriginalCompanyCode("01").setContractNo("C");source.setCanonical(source);
        var c=context().forParameters(new P18ProjectContractSyncTask.Parameters());
        when(c.identities().findByKeys(any())).thenAnswer(call->call.<SyncIdentityStore.Query>getArgument(0).object().equals("RELATION_BUSINESS")?Map.of(source.businessKey(),new SyncIdentityStore.Identity(source.businessKey(),99L,"baseline")):Map.of());
        assertEquals("P18_BOUND_RELATION_MISSING",assertThrows(IllegalArgumentException.class,()->task.syncDataInsertBefore(new SyncBatch<>(c,List.of(source)))).getMessage());verify(c.identities(),never()).saveAll(any());
    }
    @Test void relationCompany002NeedsNoActorCompanyGrantToPlanRawTarget() {
        var mapper=mock(P18ProjectContractSyncTask.TargetMapper.class);
        var task=new P18ProjectContractSyncTask(mapper);var source=new P18ProjectContractSyncTask.Source().setContractSourceId(1L).setMemberId(2L).setOriginalProjectId(7L).setOriginalCompanyCode("02").setContractNo("C");source.setCanonical(source);
        var c=context().forParameters(new P18ProjectContractSyncTask.Parameters());var batch=new SyncBatch<>(c,List.of(source));task.syncDataInsertBefore(batch);
        assertEquals("002",source.getCompanyCode());assertEquals("C",source.getTarget().getContractNo());assertEquals(new SyncCounts(1,1,0,0,0),task.syncDataCount(batch));assertTrue(c.runState().warnings().stream().noneMatch(w->w.contains("AUTHORIZATION_PENDING")));
    }
    @Test void relationParametersNoLongerNeedOrganizationScopesButBusinessPermissionRemains() {
        var mapper=mock(P18ProjectContractSyncTask.TargetMapper.class);var task=new P18ProjectContractSyncTask(mapper);var preview=context();
        assertNotNull(task.createParameters(preview));
        var actual=new SyncContext<Void>(preview.tenantId(),preview.actorId(),preview.connectionId(),false,preview.batchSize(),preview.maxRows(),preview.startedAt(),preview.source(),preview.identities(),preview.references(),preview.dataName(),preview.target(),false,true);
        assertNotNull(task.createParameters(actual));assertEquals(Set.of("pms:commerce:contract:relate"),task.requiredPermissions());verifyNoInteractions(mapper);
    }
    @Test void relationMixedCompanyPreviewKeepsEveryExactRawRelationKey() {
        var mapper=mock(P18ProjectContractSyncTask.TargetMapper.class);
        var task=new P18ProjectContractSyncTask(mapper);var rows=new ArrayList<P18ProjectContractSyncTask.Source>();
        for(int i=1;i<=3;i++){var row=new P18ProjectContractSyncTask.Source().setContractSourceId((long)i).setMemberId((long)i).setOriginalProjectId(7L).setOriginalCompanyCode(i==3?null:i==1?"01":"02").setContractNo("C"+i);row.setCanonical(row);rows.add(row);}
        var c=context().forParameters(new P18ProjectContractSyncTask.Parameters());var batch=new SyncBatch<>(c,rows);task.syncDataInsertBefore(batch);
        var query=org.mockito.ArgumentCaptor.forClass(P18ProjectContractSyncTask.RelationsQuery.class);verify(mapper).selectRelations(query.capture());assertEquals(1,query.getValue().tenantId());
        assertEquals(List.of(new P18ProjectContractSyncTask.RelationKey(7L,"C1"),new P18ProjectContractSyncTask.RelationKey(7L,"C2"),new P18ProjectContractSyncTask.RelationKey(7L,"C3")),query.getValue().keys());
        assertEquals(new SyncCounts(3,3,0,0,0),task.syncDataCount(batch));assertTrue(rows.stream().allMatch(row->row.getTarget()!=null&&row.getTarget().getContractId()==null));assertTrue(c.runState().warnings().stream().noneMatch(w->w.contains("AUTHORIZATION_PENDING")));
    }
    @Test void relationWithoutCompanyGrantStillProtectsExistingIndependentBindings() {
        for(var object:List.of("RELATION_BUSINESS","RELATION_ORIGINAL")){
            var mapper=mock(P18ProjectContractSyncTask.TargetMapper.class);
            var task=new P18ProjectContractSyncTask(mapper);var source=new P18ProjectContractSyncTask.Source().setContractSourceId(1L).setMemberId(2L).setOriginalProjectId(7L).setOriginalCompanyCode("01").setContractNo("C");source.setCanonical(source);
            var base=context();var c=new SyncContext<Void>(base.tenantId(),base.actorId(),base.connectionId(),false,base.batchSize(),base.maxRows(),base.startedAt(),base.source(),base.identities(),base.references(),base.dataName(),base.target(),false,true).forParameters(new P18ProjectContractSyncTask.Parameters());
            var key=object.equals("RELATION_BUSINESS")?source.businessKey():source.sourceKey();when(c.identities().findByKeys(any())).thenAnswer(call->call.<SyncIdentityStore.Query>getArgument(0).object().equals(object)?Map.of(key,new SyncIdentityStore.Identity(key,99L,"baseline")):Map.of());
            var expected=object.equals("RELATION_BUSINESS")?"P18_BOUND_RELATION_MISSING":"P18_ORIGINAL_RELATION_CHANGED";assertEquals(expected,assertThrows(IllegalArgumentException.class,()->task.syncDataInsertBefore(new SyncBatch<>(c,List.of(source)))).getMessage());verify(c.identities(),never()).saveAll(any());
        }
    }
    @Test void relationOriginalProjectIdentityIsStoredWithoutPerProjectOwnerLookup() {
        var mapper=mock(P18ProjectContractSyncTask.TargetMapper.class);var task=new P18ProjectContractSyncTask(mapper);
        var source=new P18ProjectContractSyncTask.Source().setContractSourceId(1L).setMemberId(2L).setOriginalProjectId(701L).setOriginalCompanyCode(null).setOriginalProjectCode("RAW-UNRESOLVED").setContractNo("C");source.setCanonical(source);
        var c=context().forParameters(new P18ProjectContractSyncTask.Parameters());task.syncDataInsertBefore(new SyncBatch<>(c,List.of(source)));
        assertEquals(701L,source.getFormalProjectId());assertEquals(701L,source.getTarget().getProjectId());assertNull(source.getCompanyCode());assertEquals("C",source.getTarget().getContractNo());
        var query=org.mockito.ArgumentCaptor.forClass(P18ProjectContractSyncTask.RelationsQuery.class);verify(mapper).selectRelations(query.capture());assertEquals(List.of(new P18ProjectContractSyncTask.RelationKey(701L,"C")),query.getValue().keys());verifyNoMoreInteractions(mapper);
    }
    @Test void relationUnmappedSourceCompanyRemainsInvalidForPreviewAndExecution() {
        var mapper=mock(P18ProjectContractSyncTask.TargetMapper.class);
        var task=new P18ProjectContractSyncTask(mapper);var source=new P18ProjectContractSyncTask.Source().setContractSourceId(1L).setMemberId(2L).setOriginalProjectId(7L).setOriginalCompanyCode("99").setContractNo("C");source.setCanonical(source);var base=context();
        for(boolean preview:List.of(true,false)){var c=new SyncContext<Void>(base.tenantId(),base.actorId(),base.connectionId(),preview,base.batchSize(),base.maxRows(),base.startedAt(),base.source(),base.identities(),base.references(),base.dataName(),base.target(),false,true).forParameters(new P18ProjectContractSyncTask.Parameters());assertTrue(assertThrows(IllegalArgumentException.class,()->task.syncDataInsertBefore(new SyncBatch<>(c,List.of(source)))).getMessage().contains("DPPMS_COMPANY_CODE_UNMAPPED"));}verifyNoInteractions(mapper);
    }
}
