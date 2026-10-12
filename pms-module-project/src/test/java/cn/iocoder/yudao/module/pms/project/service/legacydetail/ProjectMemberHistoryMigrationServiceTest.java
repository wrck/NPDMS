package cn.iocoder.yudao.module.pms.project.service.legacydetail;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.legacydetail.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi;
import cn.iocoder.yudao.module.system.api.user.ActiveUserSelectionApi;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class ProjectMemberHistoryMigrationServiceTest {
    ProjectLegacyEntityMigrationService sources;
    ProjectMasterMapper projects; ProjectMemberAssignmentMapper members;
    SystemUserMigrationApi identities; ActiveUserSelectionApi qualification;
    ProjectMemberHistoryMigrationService service;
    ProjectLegacyRecordDO row; ProjectMasterDO project;
    final ProjectLegacyEntityMigrationService.Request request = new ProjectLegacyEntityMigrationService.Request(10L,20L,"a".repeat(64));
    final ProjectAccessActor actor = new ProjectAccessActor(1L,7L);
    @BeforeEach void setup() {
        sources=mock(ProjectLegacyEntityMigrationService.class);projects=mock(ProjectMasterMapper.class);
        members=mock(ProjectMemberAssignmentMapper.class);identities=mock(SystemUserMigrationApi.class);qualification=mock(ActiveUserSelectionApi.class);
        service=new ProjectMemberHistoryMigrationService(sources,projects,members,identities,qualification);
        project=new ProjectMasterDO();project.setId(100L);project.setTenantId(1L);project.setCompanyId(8L);
        project.setLifecycleStatus("NORMAL_CLOSED");project.setVersion(12L);
        when(projects.selectById(100L)).thenReturn(project);when(members.selectListByProjectId(100L)).thenReturn(List.of());
        when(identities.getHistoricalIdentitiesByUsername(1L,Set.of("z01010"))).thenReturn(List.of(new SystemUserMigrationApi.HistoricalIdentity(40L,"z01010")));
        when(qualification.page(any())).thenReturn(new PageResult<>(List.of(new ActiveUserSelectionApi.User(40L,"z01010","原人员",1L)),1L));
        row=new ProjectLegacyRecordDO();row.setId(30L);row.setTenantId(1L);row.setSnapshotId(20L);row.setDomainCode("members");row.setSourceKey("99");
        var values=new TreeMap<String,String>();
        for(var key:List.of("id","projectId","projectType","memberRole","memberCode","memberName","phoneNum","email","fromFlag","createTime","createBy","updateTime","updateBy","effectiveFrom","effectiveTo")) values.put(key,null);
        values.put("id","99");values.put("projectId","51922");values.put("projectType","10");values.put("memberRole","30");values.put("memberCode","z01010");
        values.put("createTime","2020-01-01 10:00:00");values.put("effectiveFrom","2020-01-01 10:00:00");
        row.setPayloadJson(tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(values));checksum(values);
        var source=new ProjectLegacySourceDO();source.setSourceProjectKey("51922");
        when(sources.loadCorrectionSource(100L,request,actor)).thenReturn(new ProjectLegacyEntityMigrationService.Loaded(source,new ProjectLegacySnapshotDO(),Map.of("members",List.of(row))));
    }
    void checksum(Map<String,String> values) {
        var canonical=new TreeMap<String,Object>();canonical.put("domain","members");canonical.put("key","99");
        canonical.put("parentDomain",null);canonical.put("parentKey",null);canonical.put("sourceUpdatedAt",null);canonical.put("values",values);
        row.setChecksum(DigestUtil.sha256Hex(JsonUtils.toJsonString(canonical)));
    }
    @Test void closedProjectUsesTheSameCompanyQualificationWithoutChangingLifecycleOrManager() {
        var report=service.preview(100L,request,actor);
        assertTrue(report.candidates().getFirst().qualified());assertEquals("OWNER_QUALIFIED",report.candidates().getFirst().result());
        var query=ArgumentCaptor.forClass(ActiveUserSelectionApi.Query.class);verify(qualification).page(query.capture());
        assertEquals(8L,query.getValue().qualification().companyId());assertEquals("PROJECT_MANAGER",query.getValue().qualification().scopeRole());
        assertEquals("NORMAL_CLOSED",project.getLifecycleStatus());assertNull(project.getManagerId());
        verify(projects,never()).updateById(any(ProjectMasterDO.class));verify(members,never()).insert(any(ProjectMemberAssignmentDO.class));
    }
    @Test void qualificationFailureDoesNotGrantSystemRolesOrProjectMembership() {
        when(qualification.page(any())).thenReturn(PageResult.empty());
        assertFalse(service.preview(100L,request,actor).candidates().getFirst().qualified());
        verify(members,never()).insert(any(ProjectMemberAssignmentDO.class));
    }
    @Test void currentPrimaryIsReportedInsteadOfSilentlyReassigned() {
        var current=new ProjectMemberAssignmentDO();current.setId(45L);current.setTenantId(1L);current.setUserId(1L);
        current.setMemberRole("PROJECT_MANAGER");current.setAssignmentType("PRIMARY");current.setStatus("ACTIVE");
        when(members.selectListByProjectId(100L)).thenReturn(List.of(current));
        var candidate=service.preview(100L,request,actor).candidates().getFirst();
        assertEquals("CURRENT_ROLE_CONFLICT",candidate.result());assertEquals(45L,candidate.currentRoleMembers().getFirst().id());
        verify(members,never()).updateById(any(ProjectMemberAssignmentDO.class));
    }
    @Test void aMissingOriginalFieldOrChecksumChangeStopsBeforeQualification() {
        row.setPayloadJson("{}");assertThrows(IllegalArgumentException.class,()->service.preview(100L,request,actor));
        verifyNoInteractions(qualification);
    }
    @Test void tenantMismatchStopsBeforeReadingMembersOrSelectingAccounts() {
        project.setTenantId(2L);assertThrows(IllegalArgumentException.class,()->service.preview(100L,request,actor));
        verifyNoInteractions(identities,qualification);verify(members,never()).selectListByProjectId(anyLong());
    }
}
