package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.migration.ProjectContractRelationMigrationApi.*;
import cn.iocoder.yudao.module.pms.project.api.migration.ProjectMigrationIdentityContextApi;
import cn.iocoder.yudao.module.pms.project.api.migration.ProjectMasterMigrationApi;
import cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi;
import cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.permission.dto.UserCompanyDepartmentScopeRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.junit.jupiter.api.*;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DirectProjectRelationPlanTest {
    final DirectProjectRelationMapper relations=mock(DirectProjectRelationMapper.class);
    final DirectRelationContractMapper contracts=mock(DirectRelationContractMapper.class);
    final ProjectMigrationIdentityContextApi projects=mock(ProjectMigrationIdentityContextApi.class);
    final OrganizationScopeApi organization=mock(OrganizationScopeApi.class);
    final PermissionApi permissions=mock(PermissionApi.class);
    final AdminUserApi users=mock(AdminUserApi.class);
    final SystemUserMigrationApi historicalUsers=mock(SystemUserMigrationApi.class);
    final ProjectMasterMigrationApi projectIdentities=mock(ProjectMasterMigrationApi.class);
    final javax.sql.DataSource dataSource=new org.springframework.jdbc.datasource.DriverManagerDataSource("jdbc:h2:mem:p18-plan-"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
    final org.springframework.transaction.support.TransactionTemplate tx=new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource));
    final DirectProjectRelationPersistence service=new DirectProjectRelationPersistence(
            dataSource,relations,contracts,projects,organization,permissions,users,historicalUsers,projectIdentities);
    final LocalDateTime old=LocalDateTime.parse("2022-12-01T10:05:40"), current=LocalDateTime.parse("2026-10-07T22:15:10.513");
    @BeforeEach void setup() {
        tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        TenantContextHolder.setTenantId(1L);
        when(permissions.hasAnyPermissions(eq(1L),any())).thenReturn(true);
        var scope=new UserCompanyDepartmentScopeRespDTO();scope.setId(20L);scope.setVersion(0);scope.setCompanyCode("001");
        when(organization.getActiveScopes(1L)).thenReturn(List.of(scope));
        var project=new ProjectMigrationIdentityContextApi.Context(10L,3L,"P1","10","001","公司");
        when(projects.resolve(any())).thenReturn(project);when(projects.lockAndRevalidate(any())).thenReturn(project);
        var contract=new DirectRelationContractRow();contract.setId(11L);contract.setTenantId(1L);contract.setDeleted(false);contract.setContractNo("C1");contract.setCompanyCode("001");
        when(contracts.selectByIdForUpdate(any())).thenReturn(contract);
        when(contracts.selectMigrationIdentity(any())).thenReturn(List.of(contract));
        var facts=mock(ProjectMasterMigrationApi.Facts.class);
        when(facts.id()).thenReturn(10L);when(facts.tenantId()).thenReturn(1L);when(facts.version()).thenReturn(3L);
        when(facts.projectCode()).thenReturn("P1");when(facts.projectType()).thenReturn("10");when(facts.companyCode()).thenReturn("001");
        when(projectIdentities.inspectBindings(any())).thenReturn(List.of(facts));
        when(relations.insertHistorical(any())).thenReturn(1);when(relations.correctHistorical(any())).thenReturn(1);
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    Relation incoming() { return new Relation(null,1L,10L,11L,"RELATED","DPPMS","pm_project_contract","100:200",old,null,"ACTIVE",0,null,old,null,old,false); }
    Relation existing() { return new Relation(30L,1L,10L,11L,"RELATED","PMS","COM_PROJECT_CONTRACT_RELATION","op1",current,null,"ACTIVE",4,"1",current,"1",current,false); }
    Request request(Entry entry,boolean adopt) { return new Request(1L,1L,"integration:1:5",List.of(entry),adopt); }
    Entry entry(Long id,Relation last,Relation reviewed) { return new Entry("100:200",id,last,reviewed,incoming(),"50370","P1",3L,"C1"); }
    Relation replace(Relation row,Map<String,Object> changes) {
        var fields=new LinkedHashMap<String,Object>(cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseMap(
                cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(row)));
        fields.putAll(changes);
        return cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(
                cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(fields),Relation.class);
    }
    Entry restoration(String status) {
        var source=replace(incoming(),Map.of("status",status));
        var original=replace(source,Map.of("id",30L));
        return new Entry("100:200",30L,original,null,source,"50370","P1",3L,"C1",18L,
                new Restoration(replace(original,Map.of("deleted",true,"version",1))));
    }
    @Test void approvedRestorationPreservesEveryBusinessAndAuditFieldAndReplaysWithoutAnotherWrite() {
        when(relations.restoreWithdrawn(any())).thenReturn(1);
        for(String status:List.of("ACTIVE","HISTORICAL")) {
            var entry=restoration(status);var withdrawn=entry.restoration().withdrawnBefore();
            when(relations.selectIdentity(any())).thenReturn(withdrawn);
            when(relations.selectIdentityForUpdate(any())).thenReturn(withdrawn);
            clearInvocations(relations);
            var preview=preview(request(entry,false)).getFirst();
            assertEquals("UPDATED",preview.action());assertEquals(entry.restoration().restored(),preview.after());
            verify(relations,never()).restoreWithdrawn(any());
            var applied=apply(request(entry,false)).getFirst();assertEquals(preview,applied);
            verify(relations).restoreWithdrawn(argThat(write->write.before().equals(withdrawn) && write.after().equals(preview.after())));
            when(relations.selectIdentityForUpdate(any())).thenReturn(applied.after());clearInvocations(relations);
            assertEquals("UNCHANGED",apply(request(entry,false)).getFirst().action());
            verify(relations,never()).restoreWithdrawn(any());verify(relations,never()).correctHistorical(any());verify(relations,never()).insertHistorical(any());
        }
    }
    @Test void restorationRejectsEveryChangedColumnAndSourceDriftBeforeAnyWrite() {
        var entry=restoration("ACTIVE");var withdrawn=entry.restoration().withdrawnBefore();
        var changes=new LinkedHashMap<String,Object>();
        changes.put("id",31L);changes.put("tenantId",2L);changes.put("projectId",12L);changes.put("contractId",13L);
        changes.put("relationRole","OTHER");changes.put("sourceSystem","PMS");changes.put("sourceTable","other");
        changes.put("sourceRecordKey","999:200");changes.put("effectiveFrom",current);changes.put("effectiveTo",current);
        changes.put("status","HISTORICAL");changes.put("version",3);changes.put("creator","1");changes.put("createTime",current);
        changes.put("updater","1");changes.put("updateTime",current);changes.put("deleted",false);
        for(var change:changes.entrySet()) {
            when(relations.selectIdentityForUpdate(any())).thenReturn(replace(withdrawn,Map.of(change.getKey(),change.getValue())));
            assertThrows(IllegalArgumentException.class,()->apply(request(entry,false)),change.getKey());
        }
        when(relations.selectIdentityForUpdate(any())).thenReturn(withdrawn);
        var sourceDrift=new Entry(entry.sourceKey(),entry.targetId(),entry.lastApplied(),null,
                replace(entry.incoming(),Map.of("updateTime",current)),entry.sourceProjectKey(),entry.sourceProjectCode(),
                entry.expectedProjectVersion(),entry.contractNo(),entry.sourceConnectionId(),entry.restoration());
        assertThrows(IllegalArgumentException.class,()->apply(request(sourceDrift,false)));
        assertThrows(IllegalArgumentException.class,()->apply(request(entry,true)));
        when(permissions.hasAnyPermissions(eq(1L),any())).thenReturn(false);
        assertThrows(IllegalArgumentException.class,()->apply(request(entry,false)));
        verify(relations,never()).restoreWithdrawn(any());verify(relations,never()).insertHistorical(any());verify(relations,never()).correctHistorical(any());
    }
    @Test void restorationRejectsChangedWithdrawalSnapshotAndFailedCompareAndSet() {
        var entry=restoration("ACTIVE");var withdrawn=entry.restoration().withdrawnBefore();
        var changed=replace(withdrawn,Map.of("creator","1"));
        when(relations.selectIdentityForUpdate(any())).thenReturn(changed);
        var invalid=new Entry(entry.sourceKey(),entry.targetId(),entry.lastApplied(),null,entry.incoming(),entry.sourceProjectKey(),
                entry.sourceProjectCode(),entry.expectedProjectVersion(),entry.contractNo(),entry.sourceConnectionId(),new Restoration(changed));
        assertThrows(IllegalArgumentException.class,()->apply(request(invalid,false)));
        verify(relations,never()).restoreWithdrawn(any());
        when(relations.selectIdentityForUpdate(any())).thenReturn(withdrawn);
        when(relations.restoreWithdrawn(any())).thenReturn(0);
        assertThrows(IllegalStateException.class,()->apply(request(entry,false)));
    }
    @Test void previewGeneratesNoIdAndPreservesNullAuditorsAndOriginalDates() {
        var result=preview(request(entry(null,null,null),false)).getFirst();
        assertEquals("CREATED",result.action());assertNull(result.after().id());assertNull(result.after().creator());assertNull(result.after().updater());
        assertEquals(old,result.after().createTime());assertEquals(old,result.after().updateTime());assertEquals(old,result.after().effectiveFrom());
        verify(relations,never()).insertHistorical(any());verify(relations,never()).correctHistorical(any());
    }
    @Test void normalHistoricalInsertUsesFormalOwnerAndDoesNotCreateOperationCompletion() {
        var result=apply(request(entry(null,null,null),false)).getFirst();
        assertNotNull(result.after().id());assertEquals(0,result.after().version());
        verify(projects).lockAndRevalidate(any());verify(relations).insertHistorical(result.after());
    }
    Entry withAuditors(String creator,String updater) {
        var x=incoming();var row=new Relation(x.id(),x.tenantId(),x.projectId(),x.contractId(),x.relationRole(),
                x.sourceSystem(),x.sourceTable(),x.sourceRecordKey(),x.effectiveFrom(),x.effectiveTo(),x.status(),
                x.version(),creator,x.createTime(),updater,x.updateTime(),x.deleted());
        return new Entry("100:200",null,null,null,row,"50370","P1",3L,"C1");
    }
    @Test void onlyApprovedUnlinkedAuditCodesArePreservedWithoutCreatingUsersOrSkippingAuthorization() {
        var approved=withAuditors("sys","异常数据处理");
        var result=apply(request(approved,false)).getFirst();
        assertEquals("sys",result.after().creator());assertEquals("异常数据处理",result.after().updater());
        verifyNoInteractions(historicalUsers);verify(users).validateUser(1L);verify(projects).lockAndRevalidate(any());
        clearInvocations(relations);when(organization.getActiveScopes(1L)).thenReturn(List.of());
        assertThrows(IllegalArgumentException.class,()->apply(request(approved,false)));
        verify(relations,never()).insertHistorical(any());
    }
    @Test void anotherUnlinkedAuditCodeCannotUseTheApprovedException() {
        assertThrows(IllegalArgumentException.class,()->apply(request(withAuditors("another-system-code",null),false)));
        verify(relations,never()).insertHistorical(any());verifyNoInteractions(historicalUsers);
    }
    ReferenceQuery referenceQuery() { return new ReferenceQuery(1L,1L,"integration:1:5",List.of(new Reference("100:200",10L,"10","P1","C1"))); }
    @Test void targetResolutionReadsFormalProjectVersionAndUniqueContractWithoutBusinessWrite() {
        assertEquals(List.of(new ResolvedReference("100:200",10L,3L,11L)),resolveReferences(referenceQuery()));
        verify(projectIdentities).inspectBindings(argThat(query->query.projects().equals(List.of(new ProjectMasterMigrationApi.IdentityTarget(10L,"10","P1")))));
        verify(contracts).selectMigrationIdentity(argThat(query->query.tenantId()==1 && query.companyCode().equals("001") && query.contractNo().equals("C1")));
        verifyNoInteractions(relations,projects,historicalUsers);
    }
    @Test void targetResolutionReportsMissingAndDuplicateButRejectsChangedCompanyAndUnauthorizedReferences() {
        var contract=new DirectRelationContractRow();contract.setId(11L);contract.setTenantId(1L);contract.setDeleted(false);contract.setContractNo("C1");contract.setCompanyCode("001");
        for(var candidates:List.of(List.<DirectRelationContractRow>of(),List.of(contract,contract))) {
            when(contracts.selectMigrationIdentity(any())).thenReturn(candidates);
            var ref=resolveReferences(referenceQuery()).getFirst();
            assertNull(ref.contractId());
            assertEquals(candidates.isEmpty()?ReferenceIssue.CONTRACT_MISSING:ReferenceIssue.CONTRACT_AMBIGUOUS,ref.issue());
        }
        contract.setCompanyCode("002");when(contracts.selectMigrationIdentity(any())).thenReturn(List.of(contract));
        assertThrows(IllegalArgumentException.class,()->resolveReferences(referenceQuery()));
        setup();when(organization.getActiveScopes(1L)).thenReturn(List.of());
        assertThrows(IllegalArgumentException.class,()->resolveReferences(referenceQuery()));
        setup();when(permissions.hasAnyPermissions(eq(1L),any())).thenReturn(false);
        assertThrows(IllegalArgumentException.class,()->resolveReferences(referenceQuery()));
        assertThrows(IllegalArgumentException.class,()->resolveReferences(new ReferenceQuery(2L,1L,"integration:2:5",referenceQuery().references())));
        verifyNoInteractions(relations,projects,historicalUsers);
    }
    @Test void missingProjectCompanyIsAReferenceIssueButCannotCreateARelationship() {
        var facts=projectIdentities.inspectBindings(null).getFirst();
        for(String company:Arrays.asList(null,"")) {
            when(facts.companyCode()).thenReturn(company);
            assertEquals(List.of(new ResolvedReference("100:200",10L,3L,null,ReferenceIssue.PROJECT_COMPANY_MISSING)),
                    resolveReferences(referenceQuery()));
        }
        verify(contracts,never()).selectMigrationIdentity(any());
        when(projects.lockAndRevalidate(any())).thenReturn(new ProjectMigrationIdentityContextApi.Context(10L,3L,"P1","10",null,null));
        assertThrows(IllegalArgumentException.class,()->apply(request(entry(null,null,null),false)));
        verify(relations,never()).insertHistorical(any());verify(relations,never()).correctHistorical(any());
        when(facts.tenantId()).thenReturn(2L);
        assertThrows(IllegalArgumentException.class,()->resolveReferences(referenceQuery()));
    }
    @Test void deletedRelationshipIsPreviewConflictAndApplyStillRejectsBeforeWrite() {
        var x=existing();var deleted=new Relation(x.id(),x.tenantId(),x.projectId(),x.contractId(),x.relationRole(),
                x.sourceSystem(),x.sourceTable(),x.sourceRecordKey(),x.effectiveFrom(),x.effectiveTo(),x.status(),
                x.version(),x.creator(),x.createTime(),x.updater(),x.updateTime(),true);
        when(relations.selectIdentity(any())).thenReturn(deleted);
        when(relations.selectIdentityForUpdate(any())).thenReturn(deleted);
        var result=preview(request(entry(null,null,null),false)).getFirst();
        assertEquals("CONFLICT",result.action());assertEquals(deleted,result.before());assertEquals(deleted,result.after());
        assertThrows(IllegalArgumentException.class,()->apply(request(entry(null,null,null),false)));
        verify(relations,never()).insertHistorical(any());verify(relations,never()).correctHistorical(any());
    }
    @Test void reviewedCorrectionChangesOnlyFiveHistoricalValuesAndConcurrencyVersion() {
        when(relations.selectIdentityForUpdate(any())).thenReturn(existing());
        var result=apply(request(entry(30L,null,existing()),true)).getFirst();
        assertEquals("UPDATED",result.action());assertEquals(5,result.after().version());assertNull(result.after().creator());assertNull(result.after().updater());
        assertEquals(existing().sourceRecordKey(),result.after().sourceRecordKey());assertEquals("PMS",result.after().sourceSystem());
        assertEquals(30L,result.after().id());assertEquals(11L,result.after().contractId());assertEquals(old,result.after().effectiveFrom());
    }
    @Test void existingWithoutApprovalAndAnyLaterChangeFailBeforeWrite() {
        when(relations.selectIdentityForUpdate(any())).thenReturn(existing());
        assertThrows(IllegalArgumentException.class,()->apply(request(entry(30L,null,null),false)));
        var stale=new Relation(30L,1L,10L,11L,"RELATED","PMS","COM_PROJECT_CONTRACT_RELATION","changed",current,null,"ACTIVE",4,"1",current,"1",current,false);
        when(relations.selectIdentityForUpdate(any())).thenReturn(stale);
        assertThrows(IllegalArgumentException.class,()->apply(request(entry(30L,null,existing()),true)));
        verify(relations,never()).correctHistorical(any());verify(relations,never()).insertHistorical(any());
    }
    @Test void replayIsUnchangedAndOrdinarySyncCannotIgnoreAuditDifference() {
        when(relations.selectIdentityForUpdate(any())).thenReturn(existing());
        var corrected=apply(request(entry(30L,null,existing()),true)).getFirst().after();clearInvocations(relations);
        when(relations.selectIdentityForUpdate(any())).thenReturn(corrected);
        assertEquals("UNCHANGED",apply(request(entry(30L,corrected,null),false)).getFirst().action());
        when(relations.selectIdentityForUpdate(any())).thenReturn(existing());
        assertThrows(IllegalArgumentException.class,()->apply(request(entry(30L,existing(),null),false)));
        verify(relations,never()).correctHistorical(any());
    }
    @Test void emptyScopePermissionCrossCompanyAndCrossTenantRemainBlocked() {
        when(organization.getActiveScopes(1L)).thenReturn(List.of());
        assertThrows(IllegalArgumentException.class,()->apply(request(entry(null,null,null),false)));
        setup();when(permissions.hasAnyPermissions(eq(1L),any())).thenReturn(false);
        assertThrows(IllegalArgumentException.class,()->apply(request(entry(null,null,null),false)));
        setup();var contract=new DirectRelationContractRow();contract.setTenantId(1L);contract.setDeleted(false);contract.setContractNo("C1");contract.setCompanyCode("002");
        when(contracts.selectByIdForUpdate(any())).thenReturn(contract);
        assertThrows(IllegalArgumentException.class,()->apply(request(entry(null,null,null),false)));
        assertThrows(IllegalArgumentException.class,()->apply(new Request(2L,1L,"integration:2:5",List.of(entry(null,null,null)),false)));
        verify(relations,never()).insertHistorical(any());verify(relations,never()).correctHistorical(any());
    }
    @Test void unapprovedDuplicateIdentityCannotBypassOwnerValidation() {
        var x=incoming();
        var duplicate=new Relation(null,x.tenantId(),x.projectId(),x.contractId(),x.relationRole(),x.sourceSystem(),x.sourceTable(),"101:200",
                x.effectiveFrom(),x.effectiveTo(),x.status(),x.version(),x.creator(),x.createTime(),x.updater(),x.updateTime(),x.deleted());
        var other=new Entry("101:200",null,null,null,duplicate,"50370","P1",3L,"C1");
        assertThrows(IllegalArgumentException.class,()->apply(new Request(1L,1L,"integration:1:5",List.of(entry(null,null,null),other),false)));
        verify(relations,never()).insertHistorical(any());verify(relations,never()).correctHistorical(any());
    }

    Entry historical(Long target,Relation last,Relation review) {
        var date=LocalDateTime.parse("2021-04-14T15:10:56");
        var row=new Relation(null,1L,10L,11L,"RELATED","DPPMS","pm_project_contract","41325:37632",
                date,null,"HISTORICAL",0,null,date,null,date,false);
        return new Entry("41325:37632",target,last,review,row,"36645","16200220110601N-0",3L,"16200220092717N");
    }
    void historicalContext() {
        var project=new ProjectMigrationIdentityContextApi.Context(10L,3L,"16200220110601N-0","10","001","公司");
        when(projects.resolve(any())).thenReturn(project);when(projects.lockAndRevalidate(any())).thenReturn(project);
        var contract=new DirectRelationContractRow();contract.setTenantId(1L);contract.setDeleted(false);contract.setContractNo("16200220092717N");contract.setCompanyCode("001");
        when(contracts.selectByIdForUpdate(any())).thenReturn(contract);
    }
    @Test void approvedHistoricalInsertAndReplayPreserveSeventeenFieldsAndNoEndDate() {
        historicalContext();var preview=preview(request(historical(null,null,null),false)).getFirst();
        assertEquals("HISTORICAL",preview.after().status());assertNull(preview.after().id());assertNull(preview.after().effectiveTo());
        var applied=apply(request(historical(null,null,null),false)).getFirst().after();
        assertEquals(LocalDateTime.parse("2021-04-14T15:10:56"),applied.createTime());assertNull(applied.updater());
        when(relations.selectIdentityForUpdate(any())).thenReturn(applied);clearInvocations(relations);
        var replay=apply(request(historical(applied.id(),applied,null),false)).getFirst();
        assertEquals("UNCHANGED",replay.action());assertEquals(applied,replay.after());
        verify(relations,never()).insertHistorical(any());verify(relations,never()).correctHistorical(any());
    }
    @Test void historyCannotAdoptCurrentIdentityOrChangeStatusAndUnapprovedHistoryIsBlocked() {
        historicalContext();when(relations.selectIdentityForUpdate(any())).thenReturn(existing());
        assertThrows(IllegalArgumentException.class,()->apply(request(historical(30L,existing(),null),false)));
        assertThrows(IllegalArgumentException.class,()->apply(request(historical(30L,null,existing()),true)));
        var h=historical(null,null,null);var x=h.incoming();
        var arbitrary=new Entry("999:37632",null,null,null,new Relation(null,1L,10L,11L,"RELATED","DPPMS","pm_project_contract",
                "999:37632",x.effectiveFrom(),null,"HISTORICAL",0,null,x.createTime(),null,x.updateTime(),false),
                h.sourceProjectKey(),h.sourceProjectCode(),3L,h.contractNo());
        assertThrows(IllegalArgumentException.class,()->apply(request(arbitrary,false)));
        when(relations.selectIdentityForUpdate(any())).thenReturn(new Relation(30L,1L,10L,11L,"RELATED","DPPMS","pm_project_contract",
                "100:200",old,null,"HISTORICAL",0,null,old,null,old,false));
        assertThrows(IllegalArgumentException.class,()->apply(request(entry(30L,null,null),false)));
        verify(relations,never()).insertHistorical(any());verify(relations,never()).correctHistorical(any());
    }
    private List<Result> preview(Request r){return tx.execute(status->service.preview(r));}
    private List<Result> apply(Request r){return tx.execute(status->service.apply(r));}
    private List<ResolvedReference> resolveReferences(ReferenceQuery r){return tx.execute(status->service.resolveReferences(r));}
}
