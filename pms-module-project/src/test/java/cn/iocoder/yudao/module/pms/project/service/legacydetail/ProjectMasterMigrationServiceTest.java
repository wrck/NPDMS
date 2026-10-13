package cn.iocoder.yudao.module.pms.project.service.legacydetail;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.migration.ProjectMasterMigrationApi.*;
import cn.iocoder.yudao.module.pms.project.api.migration.ProjectMasterMigrationApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.legacydetail.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.*;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService;
import cn.iocoder.yudao.module.system.api.company.CompanyApi;
import cn.iocoder.yudao.module.system.api.company.dto.CompanyRespDTO;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi;
import cn.iocoder.yudao.module.system.api.migration.SystemDepartmentMigrationApi;
import cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectCreationAuthorizationService;
import cn.iocoder.yudao.module.pms.project.service.projecttree.ProjectTreeProjectionService;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectMasterMigrationServiceTest {
    final ProjectMasterMigrationMapper projects=mock(ProjectMasterMigrationMapper.class);
    final ProjectLegacyDetailMapper sources=mock(ProjectLegacyDetailMapper.class);
    final ProjectManualCreationService access=mock(ProjectManualCreationService.class);
    final CompanyApi companies=mock(CompanyApi.class);
    final DeptApi departments=mock(DeptApi.class);
    final AdminUserApi users=mock(AdminUserApi.class);
    final SystemUserMigrationApi historicalUsers=mock(SystemUserMigrationApi.class);
    final SystemDepartmentMigrationApi historicalDepartments=mock(SystemDepartmentMigrationApi.class);
    final ProjectCreationAuthorizationService creationAuthorization=mock(ProjectCreationAuthorizationService.class);
    final OrganizationScopeApi organization=mock(OrganizationScopeApi.class);
    final ProjectTreeProjectionService treeProjection=mock(ProjectTreeProjectionService.class);
    final ProjectMasterMigrationService service=new ProjectMasterMigrationService(projects,sources,access,companies,departments,users,historicalUsers,historicalDepartments,creationAuthorization,organization,treeProjection);
    ProjectMasterDO row;
    ProjectLegacyRecordDO record;
    Map<String,String> original;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        row=new ProjectMasterDO();row.setId(100L);row.setTenantId(1L);row.setVersion(3L);row.setDeleted(false);
        row.setProjectCode("PJT-FROZEN");row.setProjectName("原正式项目");row.setProjectType("10");row.setLifecycleStatus("ACTIVE");row.setCurrentStage("S4");
        row.setDepartmentId(8L);row.setDepartmentCode("162007");row.setAssignmentStatus("UNASSIGNED");row.setActivePlanVersionId(99L);
        row.setTaskTreeVersion(7L);row.setTaskProgressVersion(9L);row.setCodeRootId(100L);row.setCodeRuleVersion("V1");
        row.setCreateTime(LocalDateTime.of(2026,10,6,0,0));row.setUpdateTime(row.getCreateTime());row.setCreator("1");row.setUpdater("1");
        when(projects.selectIdentity(any())).thenAnswer(call->row);
        when(projects.selectIdentityForUpdate(any())).thenAnswer(call->row);
        var source=new ProjectLegacySourceDO();source.setTenantId(1L);source.setProjectId(100L);
        source.setSourceProjectKey("10");source.setCurrentSnapshotId(20L);
        when(sources.selectSource(any())).thenReturn(source);when(sources.selectSourceForUpdate(any())).thenReturn(source);
        var company=new CompanyRespDTO();company.setId(1L);company.setCode("001");company.setName("正式公司");
        when(companies.getCompanyByCode("001")).thenReturn(company);
        var dept=new DeptRespDTO();dept.setId(8L);dept.setCode("162007");when(departments.getDept(8L)).thenReturn(dept);
        when(historicalUsers.getHistoricalIdentities(1L,Set.of(41L))).thenReturn(List.of(new SystemUserMigrationApi.HistoricalIdentity(41L,"x01861")));
        original=new LinkedHashMap<>();ProjectMasterMigrationApi.ORIGINAL_COLUMNS.forEach(k->original.put(k,null));
        original.put("projectId","10");original.put("projectType","10");original.put("projectCode","162007-old-code");
        original.put("projectName","来源项目");original.put("projectState","100");original.put("column001","162007");original.put("compId","1");
        original.put("column010","10");original.put("column011","20");original.put("column012","1");
        original.put("createTime","2023-01-02 03:04:05");original.put("createBy","x01861");
        original.put("projectCloseTime","2024-12-23 09:46:31");original.put("disabled","0");
        original.put("customInfo","{\"phone\":\"123\",\"amount\":12345678901234567890.123456789}");
        record=new ProjectLegacyRecordDO().setSourceTable("pm_project_header").setSourceKey("10");saveOriginal();
        when(sources.selectRecords(any())).thenReturn(List.of(record));
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }
    private void saveOriginal() {
        Map<String,Object> payload=new LinkedHashMap<>();original.forEach((k,v)->payload.put(k,v==null?tools.jackson.databind.node.NullNode.getInstance():v));
        record.setPayloadJson(JsonUtils.toJsonString(payload));
    }
    private Entry entry(Long expected,Facts last) { return new Entry("10",last==null?null:100L,expected,
            new LinkedHashMap<>(original),"001","162007","41",null,last); }
    private Request request(Entry entry,boolean adopt) { return new Request(1L,1L,"integration:1:12",List.of(entry),adopt); }
    @Test void previewsFormalHistoryWithoutChangingRuntimeOrWritingAnything() {
        var result=service.preview(request(entry(3L,null),true)).getFirst();
        assertEquals("UPDATED",result.action());assertEquals("162007-old-code",result.after().projectCode());
        assertEquals("NORMAL_CLOSED",result.after().lifecycleStatus());assertEquals("S6",result.after().currentStage());
        assertEquals(LocalDateTime.of(2024,12,23,9,46,31),result.after().closedAt());
        assertEquals("41",result.after().creator());assertNull(result.after().updater());
        assertEquals(result.after().createTime(),result.after().updateTime());assertEquals("SUPERVISION",result.after().implementationMode());
        assertTrue(result.after().customInfo().contains("12345678901234567890.123456789"));
        assertEquals(99L,row.getActivePlanVersionId());assertEquals(7L,row.getTaskTreeVersion());assertEquals(9L,row.getTaskProgressVersion());
        assertEquals("PJT-FROZEN",row.getProjectCode());assertEquals("S4",row.getCurrentStage());
        verify(projects,never()).initializeFormalIfMatch(any());verify(sources,never()).selectSourceForUpdate(any());
    }
    @Test void missingAdoptionAndStaleVersionBlockBeforeAnyBusinessWrite() {
        assertEquals("ISSUE",service.preview(request(entry(3L,null),false)).getFirst().action());
        assertEquals("ISSUE",service.preview(request(entry(2L,null),true)).getFirst().action());
        verify(projects,never()).initializeFormalIfMatch(any());
    }
    @Test void sourceChangesCannotReplaceImmutableProjectEvidence() {
        original.put("column004","后来改动");
        assertEquals("ISSUE",service.preview(request(entry(3L,null),true)).getFirst().action());
        verify(projects,never()).initializeFormalIfMatch(any());
    }
    @Test void duplicateOldCodeIncludingDeletedIdentityCannotBeTakenOver() {
        when(projects.selectExactCodeIds(any())).thenReturn(List.of(99L));
        assertEquals("ISSUE",service.preview(request(entry(3L,null),true)).getFirst().action());
        verify(projects,never()).initializeFormalIfMatch(any());
    }
    @Test void currentCompanyDepartmentAndActorMustMatchPreciseIdentity() {
        var wrong=entry(3L,null);wrong=new Entry(wrong.sourceKey(),wrong.targetId(),wrong.expectedProjectVersion(),
                wrong.originalFields(),"001","different",wrong.creator(),wrong.updater(),null);
        assertEquals("ISSUE",service.preview(request(wrong,true)).getFirst().action());
        when(historicalUsers.getHistoricalIdentities(1L,Set.of(41L))).thenReturn(List.of());
        assertThrows(IllegalArgumentException.class,()->service.preview(request(entry(3L,null),true)));
    }
    @Test void subsequentProjectChangesCannotBeOverwrittenOnRepeat() {
        var last=ProjectMasterMigrationService.snapshot(row);row.setTaskTreeVersion(8L);row.setVersion(4L);
        assertEquals("ISSUE",service.preview(request(entry(null,last),false)).getFirst().action());
        verify(projects,never()).initializeFormalIfMatch(any());
    }
    @Test void crossTenantTargetAndIncompleteOriginalHeaderAreRejected() {
        row.setTenantId(2L);assertThrows(IllegalArgumentException.class,()->service.preview(request(entry(3L,null),true)));
        row.setTenantId(1L);original.remove("customConfig");
        assertThrows(IllegalArgumentException.class,()->service.preview(request(entry(3L,null),true)));
    }
    @Test void sourceClosedProjectCannotOverwriteExistingCurrentClosure() {
        row.setLifecycleStatus("EXCEPTION_CLOSED");row.setClosedAt(LocalDateTime.of(2026,1,1,0,0));
        assertThrows(IllegalArgumentException.class,()->service.preview(request(entry(3L,null),true)));
    }
    @Test void nonEmptyUnmappedConfigurationRemainsPendingInsteadOfBeingDiscarded() {
        original.put("customConfig","{\"setting\":true}");saveOriginal();
        assertEquals("ISSUE",service.preview(request(entry(3L,null),true)).getFirst().action());
    }
    @Test void unadjudicatedImplementationAndInvalidJsonAreRejected() {
        original.put("column012","99");saveOriginal();
        assertThrows(IllegalArgumentException.class,()->service.preview(request(entry(3L,null),true)));
        original.put("column012","1");original.put("customInfo","{\"a\":1,\"a\":2}");saveOriginal();
        assertThrows(IllegalArgumentException.class,()->service.preview(request(entry(3L,null),true)));
    }
    @Test void exactSourceNullActorCannotBeFilledWithMigrationOperator() {
        var e=entry(3L,null);e=new Entry(e.sourceKey(),e.targetId(),e.expectedProjectVersion(),e.originalFields(),
                e.companyCode(),e.departmentCode(),e.creator(),"1",null);
        Entry invalid=e;assertThrows(IllegalArgumentException.class,()->service.preview(request(invalid,true)));
    }
    @Test void originalFieldNormalizationDoesNotHideNonDateChanges() {
        var other=new LinkedHashMap<>(original);other.put("createTime","2023-01-02T03:04:05.000");other.put("disabled","false");
        assertTrue(ProjectMasterMigrationService.sameOriginal(original,other));
        other.put("projectName","别的项目");assertFalse(ProjectMasterMigrationService.sameOriginal(original,other));
    }
    private void newSource() {
        when(sources.selectSource(any())).thenReturn(null);when(sources.selectSourceForUpdate(any())).thenReturn(null);
        when(historicalDepartments.getCatalogue()).thenReturn(List.of(new SystemDepartmentMigrationApi.Department(
                8L,"162007","杭州办事处",0L,0,null,null,null,0,0,null,null,null,null)));
        when(organization.hasScope(1L,1L,8L)).thenReturn(true);
    }
    @Test void newHistoricalProjectPreviewNeedsNoSourceSnapshotOrCurrentTemplate() {
        newSource();original.put("projectCloseTime",null);
        var result=service.preview(request(entry(null,null),false)).getFirst();
        assertEquals("CREATED",result.action());assertNull(result.before());assertNull(result.after().id());
        assertEquals("NORMAL_CLOSED",result.after().lifecycleStatus());assertNull(result.after().closedAt());
        assertEquals("162007-old-code",result.after().projectCode());assertEquals(0L,result.after().version());
        verify(creationAuthorization).assertCanCreate(1L);verify(organization).hasScope(1L,1L,8L);
        verify(sources,never()).selectRecords(any());verifyNoInteractions(access);
        verify(projects,never()).insertHistoricalRoot(any());verify(projects,never()).initializeFormalIfMatch(any());
        verifyNoInteractions(treeProjection);
    }
    @Test void newSourceCannotAdoptExistingOrBypassCreationPermissionAndOrganizationScope() {
        newSource();
        assertThrows(IllegalArgumentException.class,()->service.preview(request(entry(null,null),true)));
        doThrow(new IllegalArgumentException("FORBIDDEN")).when(creationAuthorization).assertCanCreate(1L);
        assertThrows(IllegalArgumentException.class,()->service.preview(request(entry(null,null),false)));
        doNothing().when(creationAuthorization).assertCanCreate(1L);when(organization.hasScope(1L,1L,8L)).thenReturn(false);
        doThrow(new IllegalArgumentException("FORBIDDEN")).when(creationAuthorization).assertCanImportAcrossOrganizations(1L);
        assertThrows(IllegalArgumentException.class,()->service.preview(request(entry(null,null),false)));
        verify(projects,never()).insertHistoricalRoot(any());
    }
    @Test void authorizedMigrationAdministratorCanPreviewAnotherOfficeWithoutGrantingScope() {
        newSource();when(organization.hasScope(1L,1L,8L)).thenReturn(false);
        assertEquals("CREATED",service.preview(request(entry(null,null),false)).getFirst().action());
        verify(creationAuthorization).assertCanImportAcrossOrganizations(1L);
        verify(organization).hasScope(1L,1L,8L);
        verifyNoMoreInteractions(organization);
        verify(projects,never()).insertHistoricalRoot(any());
        verify(projects,never()).initializeFormalIfMatch(any());
    }
    @Test void securityTypesAreSupportedInTheDesignAndNullAttributesRemainNull() {
        newSource();original.put("projectType","afss");original.put("projectState","50");
        original.put("column010",null);original.put("column011",null);original.put("column012",null);
        var result=service.preview(request(entry(null,null),false)).getFirst();
        assertEquals("ACTIVE",result.after().lifecycleStatus());assertEquals("S6",result.after().currentStage());
        assertNull(result.after().closedAt());assertNotNull(result.after().projectCloseTime());
        assertNull(result.after().projectCategory());assertNull(result.after().signingMethod());assertNull(result.after().implementationMode());
        verify(projects,never()).insertHistoricalRoot(any());
    }
    @Test void reviewedCrossTypeCodePlanPreservesOriginalAndRejectsInvalidOrdering() {
        original.put("projectCode","P-0");var entry=entry(null,null);
        var allocation=new CodeAllocation("P-0",List.of(new CodeCollisionProject(5L,"afxx"),new CodeCollisionProject(10L,"10")),"P-2");
        var planned=new Entry(entry.sourceKey(),entry.targetId(),entry.expectedProjectVersion(),entry.originalFields(),entry.companyCode(),
                entry.departmentCode(),entry.creator(),entry.updater(),entry.lastApplied(),allocation);
        assertEquals("P-2",ProjectMasterMigrationService.formalCode(planned));assertEquals("P-0",planned.originalFields().get("projectCode"));
        for(var invalid:List.of(new CodeAllocation("P-0",allocation.orderedProjects(),"P-0"),
                new CodeAllocation("P-0",allocation.orderedProjects(),"Q-1"),
                new CodeAllocation("P-0",List.of(new CodeCollisionProject(10L,"10"),new CodeCollisionProject(20L,"afxx")),"P-1"),
                new CodeAllocation("P-0",List.of(new CodeCollisionProject(5L,"10"),new CodeCollisionProject(10L,"10")),"P-1"))) {
            var rejected=new Entry(entry.sourceKey(),entry.targetId(),entry.expectedProjectVersion(),entry.originalFields(),entry.companyCode(),
                    entry.departmentCode(),entry.creator(),entry.updater(),entry.lastApplied(),invalid);
            assertThrows(IllegalArgumentException.class,()->ProjectMasterMigrationService.formalCode(rejected));
        }
    }
    @Test void emptyImplementationUsesTheOriginalReadonlyValueWithoutChangingRawFields() {
        assertEquals("DIRECT_SERVICE",ProjectMasterMigrationService.implementation("","0"));
        assertEquals("SUPERVISION",ProjectMasterMigrationService.implementation(null,"1"));
        assertEquals("AGENT_SELF_SERVICE",ProjectMasterMigrationService.implementation("3","0"));
        assertNull(ProjectMasterMigrationService.implementation(null,"-1"));
        assertNull(ProjectMasterMigrationService.implementation(null,null));
        original.put("column012","");original.put("columno12_readonly","0");saveOriginal();
        assertEquals("DIRECT_SERVICE",service.preview(request(entry(3L,null),true)).getFirst().after().implementationMode());
        assertEquals("",original.get("column012"));assertEquals("0",original.get("columno12_readonly"));
    }
    @Test void approvedImplementationCodesAreCarriedWithoutReplacingOriginalValues() {
        for(var mapping:Map.of("2","DIRECT_SUBCONTRACT","4","FACTORY_INTEGRATION").entrySet()) {
            original.put("column012",mapping.getKey());saveOriginal();
            assertEquals(mapping.getValue(),service.preview(request(entry(3L,null),true)).getFirst().after().implementationMode());
            assertEquals(mapping.getKey(),original.get("column012"));
            assertEquals(mapping.getValue(),ProjectMasterMigrationService.implementation(null,mapping.getKey()));
        }
    }
    @Test void approvedUnlinkedAuditCodesStayExactWithoutBecomingPlatformUsers() {
        newSource();original.put("createBy","sys");original.put("updateBy","jiangyating");
        var input=new Entry("10",null,null,new LinkedHashMap<>(original),"001","162007","sys","jiangyating",null);
        var result=service.preview(request(input,false)).getFirst();
        assertEquals("sys",result.after().creator());assertEquals("jiangyating",result.after().updater());
        verifyNoInteractions(historicalUsers);
        var invalid=new Entry("10",null,null,new LinkedHashMap<>(original),"001","162007","1","jiangyating",null);
        assertThrows(IllegalArgumentException.class,()->service.preview(request(invalid,false)));
    }

    @Test void approvedMissingOrganizationStaysNullAndRequiresTheSpecialOwnerPermission() {
        newSource();original.put("compId",null);original.put("column001",null);
        var input=new Entry("10",null,null,new LinkedHashMap<>(original),null,null,"41",null,null);
        var result=service.preview(request(input,false)).getFirst();
        assertEquals("CREATED",result.action());assertNull(result.after().companyId());
        assertNull(result.after().companyCode());assertNull(result.after().companyName());
        verify(creationAuthorization).assertCanImportWithoutOrganization(1L);
        verifyNoInteractions(companies,departments,historicalDepartments,organization);
        doThrow(new IllegalArgumentException("FORBIDDEN")).when(creationAuthorization).assertCanImportWithoutOrganization(1L);
        assertThrows(IllegalArgumentException.class,()->service.preview(request(input,false)));
        verify(projects,never()).insertHistoricalRoot(any());
    }

    @Test void missingCompanyDoesNotHideAMissingNonNullDepartmentDirectory() {
        newSource();original.put("compId",null);
        var input=new Entry("10",null,null,new LinkedHashMap<>(original),null,"unmatched","41",null,null);
        assertEquals("ISSUE",service.preview(request(input,false)).getFirst().action());
        verify(projects,never()).insertHistoricalRoot(any());
    }

    @Test void originalNullCannotBeFilledByGuessingOrClearAnExistingFormalOrganization() {
        newSource();original.put("compId",null);
        assertThrows(IllegalArgumentException.class,()->service.preview(request(entry(null,null),false)));
        original.put("compId","1");
        var input=new Entry("10",null,null,new LinkedHashMap<>(original),null,"162007","41",null,null);
        assertThrows(IllegalArgumentException.class,()->service.preview(request(input,false)));
        original.put("compId",null);original.put("column001",null);
        var source=new ProjectLegacySourceDO();source.setTenantId(1L);source.setProjectId(100L);source.setCurrentSnapshotId(20L);
        when(sources.selectSource(any())).thenReturn(source);saveOriginal();
        var existingInput=new Entry("10",null,3L,new LinkedHashMap<>(original),null,null,"41",null,null);
        assertEquals("ISSUE",service.preview(request(existingInput,true)).getFirst().action());
        assertEquals(8L,row.getDepartmentId());assertEquals("162007",row.getDepartmentCode());
        verify(projects,never()).initializeFormalIfMatch(any());
    }
    @Test void identityMappingVerificationChecksPermissionAndAllFormalFieldsWithoutWriting() {
        var expected=ProjectMasterMigrationService.snapshot(row);
        var query=new BindingVerification(1L,1L,List.of(expected));
        service.verifyBindings(query);service.lockBindings(query);
        verify(access,times(2)).getProjectForManage(eq(100L),any());
        verify(projects).selectIdentityForUpdate(any());verify(projects,never()).initializeFormalIfMatch(any());
        row.setProjectName("后续修改");assertThrows(IllegalArgumentException.class,()->service.verifyBindings(query));
        row.setProjectName(expected.projectName());
        doThrow(new IllegalArgumentException("FORBIDDEN")).when(access).getProjectForManage(eq(100L),any());
        assertThrows(IllegalArgumentException.class,()->service.lockBindings(query));
        assertThrows(IllegalArgumentException.class,()->service.verifyBindings(new BindingVerification(2L,1L,List.of(expected))));
        assertThrows(IllegalArgumentException.class,()->service.verifyBindings(new BindingVerification(1L,1L,List.of(expected,expected))));
    }
    @Test void identityInspectionPreservesCurrentBusinessChangesAndBlocksActualIdentityChanges() {
        row.setVersion(12L);row.setProjectName("正常业务的新名称");
        var input=new IdentityInspection(1L,1L,List.of(new IdentityTarget(100L,"10","PJT-FROZEN")));
        var current=service.inspectBindings(input);
        assertEquals(List.of(ProjectMasterMigrationService.snapshot(row)),current);
        assertEquals(12L,current.getFirst().version());assertEquals("正常业务的新名称",current.getFirst().projectName());
        service.lockBindings(new BindingVerification(1L,1L,current));
        row.setVersion(13L);assertThrows(IllegalArgumentException.class,()->service.lockBindings(new BindingVerification(1L,1L,current)));
        row.setProjectCode("NEW-CODE");assertThrows(IllegalArgumentException.class,()->service.inspectBindings(input));
        verify(projects,never()).initializeFormalIfMatch(any());verify(projects,never()).insertHistoricalRoot(any());
    }
}
