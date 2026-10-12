package cn.iocoder.yudao.module.pms.project.service.legacydetail;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.legacydetail.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectMemberHistoryMigrationMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;
import java.util.function.Function;
import static cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class ProjectMemberHistoryImportServiceTest {
    ProjectLegacyEntityMigrationService sources; ProjectMemberHistoryMigrationService qualification;
    ProjectMasterMapper projects; ProjectMemberHistoryMigrationMapper members;
    SystemUserMigrationApi identities; ProjectLegacyNativeEvidenceService evidence; PlatformCommandExecutionApi commands;
    ProjectMemberHistoryImportService service;
    final ProjectAccessActor actor=new ProjectAccessActor(1L,7L);
    final ProjectLegacyEntityMigrationService.Request sourceRequest=new ProjectLegacyEntityMigrationService.Request(10L,20L,"a".repeat(64));
    ProjectMasterDO project; ProjectLegacyRecordDO original; Map<String,String> raw;
    final List<ProjectMemberAssignmentDO> stored=new ArrayList<>();
    ProjectMemberHistoryImportService.Request request;
    SuccessFacts facts;
    @BeforeEach void setup() {
        stored.clear();sources=mock(ProjectLegacyEntityMigrationService.class);qualification=mock(ProjectMemberHistoryMigrationService.class);
        projects=mock(ProjectMasterMapper.class);members=mock(ProjectMemberHistoryMigrationMapper.class);
        identities=mock(SystemUserMigrationApi.class);evidence=mock(ProjectLegacyNativeEvidenceService.class);commands=mock(PlatformCommandExecutionApi.class);
        service=new ProjectMemberHistoryImportService(sources,qualification,projects,members,identities,evidence,commands);
        project=new ProjectMasterDO();project.setId(100L);project.setTenantId(1L);project.setVersion(12L);
        project.setLifecycleStatus("NORMAL_CLOSED");project.setCurrentStage("S6");project.setCompanyId(8L);
        project.setCreateTime(LocalDateTime.of(2020,1,1,0,0));project.setUpdateTime(project.getCreateTime());
        when(projects.selectByIdForUpdate(100L)).thenReturn(project);when(projects.selectById(100L)).thenReturn(project);
        when(members.selectProjectForUpdate(any())).thenAnswer(inv->List.copyOf(stored));
        when(members.insertHistorical(any())).thenAnswer(inv->{stored.add(inv.getArgument(0,ProjectMemberHistoryMigrationMapper.HistoricalInsert.class).values());return 1;});
        when(members.updateApprovedPrimary(any())).thenAnswer(inv->{
            var q=inv.getArgument(0,ProjectMemberHistoryMigrationMapper.PrimaryUpdate.class);
            project.setVersion(project.getVersion()+1);project.setManagerId(q.managerId());project.setManagerName(q.managerName());
            project.setManagerEmployeeNo(q.employeeNo());project.setAssignmentStatus(q.assignmentStatus());return 1;
        });
        when(members.closeApprovedInterval(any())).thenAnswer(inv->{
            var q=inv.getArgument(0,ProjectMemberHistoryMigrationMapper.CloseInterval.class);
            var row=stored.stream().filter(r->r.getId().equals(q.memberId())).findFirst().orElseThrow();
            row.setEffectiveTo(q.effectiveTo());row.setEndReason(q.reason());row.setVersion(row.getVersion()+1);row.setUpdater(q.actor());row.setUpdateTime(q.effectiveTo());return 1;
        });
        when(identities.getHistoricalMemberIdentities(1L,Set.of("z01010"))).thenReturn(List.of(new SystemUserMigrationApi.MemberIdentity(40L,"z01010","REAL-EHR-NO")));
        raw=new TreeMap<>();for(String field:List.of("id","projectId","projectType","memberRole","memberCode","memberName","phoneNum","email","fromFlag","createTime","createBy","updateTime","updateBy","effectiveFrom","effectiveTo"))raw.put(field,null);
        raw.put("id","99");raw.put("projectId","51922");raw.put("projectType","10");raw.put("memberRole","30");raw.put("memberCode","z01010");
        raw.put("memberName","历史姓名");raw.put("phoneNum","原座机");raw.put("email","");raw.put("fromFlag","1");
        raw.put("createTime","2020-01-01 00:00:00");raw.put("effectiveFrom","2020-01-01 00:00:00");
        original=new ProjectLegacyRecordDO();original.setId(30L);original.setTenantId(1L);original.setSnapshotId(20L);
        original.setSourceTable("pm_project_member");original.setSourceKey("99");original.setDomainCode("members");payload();
        var source=new ProjectLegacySourceDO();source.setSourceProjectKey("51922");source.setTenantId(1L);
        var snapshot=new ProjectLegacySnapshotDO();snapshot.setId(20L);
        when(sources.loadCorrectionSource(100L,sourceRequest,actor)).thenReturn(new ProjectLegacyEntityMigrationService.Loaded(source,snapshot,Map.of("members",List.of(original))));
        candidate(40L,true,"OWNER_QUALIFIED");request=request(Set.of());
        when(commands.execute(any(),anyString(),eq(ProjectMemberHistoryImportService.Report.class),any(),any())).thenAnswer(inv->{
            var report=inv.<Supplier<ProjectMemberHistoryImportService.Report>>getArgument(3).get();
            facts=inv.<Function<ProjectMemberHistoryImportService.Report,SuccessFacts>>getArgument(4).apply(report);
            return new ExecutionResult<>(Decision.NEW,report);
        });
    }
    void payload(){original.setPayloadJson(tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(raw));}
    void candidate(Long user,boolean approved,String reason) {
        when(qualification.preview(100L,sourceRequest,actor)).thenReturn(new ProjectMemberHistoryMigrationService.Report(100L,12L,"NORMAL_CLOSED",null,LocalDateTime.now(),
            List.of(new ProjectMemberHistoryMigrationService.Candidate("99",raw.get("memberCode"),raw.get("memberName"),"PROJECT_MANAGER",user,true,approved,reason,List.of()))));
    }
    ProjectMemberHistoryImportService.Request request(Set<Long> close) {
        return new ProjectMemberHistoryImportService.Request(sourceRequest,project.getVersion(),ProjectMemberHistoryImportService.fingerprint(stored),null,"z01010",close);
    }
    ProjectMemberAssignmentDO admin() {
        var row=new ProjectMemberAssignmentDO();row.setId(45L);row.setProjectId(100L);row.setTenantId(1L);row.setUserId(1L);
        row.setMemberRole("PROJECT_MANAGER");row.setAssignmentType("PRIMARY");row.setStatus("ACTIVE");row.setVersion(0);
        row.setEffectiveFrom(LocalDateTime.now().minusDays(1));row.setCreateTime(row.getEffectiveFrom());return row;
    }
    @Test void closedOwnerUsesFormalEmployeeNumberAndPreservesHistoricalContactsAndNullActors() {
        var result=service.migrate(100L,request,actor);var row=result.report().targets().getFirst().member();
        assertEquals("REAL-EHR-NO",row.getEmployeeNo());assertEquals("z01010",row.getMemberCode());
        assertEquals("历史姓名",row.getMemberName());assertEquals("原座机",row.getPhoneNumber());assertEquals("",row.getEmail());
        assertNull(row.getCreator());assertNull(row.getUpdater());assertEquals(row.getCreateTime(),row.getUpdateTime());assertTrue(row.getAuthorizationConfirmed());
        assertEquals("NORMAL_CLOSED",project.getLifecycleStatus());assertEquals("S6",project.getCurrentStage());
        assertEquals(LocalDateTime.of(2020,1,1,0,0),project.getUpdateTime());assertEquals(40L,project.getManagerId());
        assertNull(facts.eventType());assertTrue(facts.businessEvents().isEmpty());
        verify(evidence).reconcile(any(),any(),eq("members"),eq(List.of(original)),anyMap(),eq("MEMBER_HISTORY_V1"));
    }
    @Test void importPreviewVerifiesFormalEmployeeAndAuditIdentitiesBeforeAnyWrites() {
        var preview=service.preview(100L,sourceRequest,actor);
        assertEquals("REAL-EHR-NO",preview.memberIdentities().getFirst().employeeNo());
        assertEquals(request.existingMembersChecksum(),preview.existingMembersChecksum());
        verify(members,never()).insertHistorical(any());verifyNoInteractions(commands,evidence);
    }
    @Test void unqualifiedUnlinkedHistoryDoesNotCreateAuthorizationAndKeepsNullAuditDates() {
        raw.put("memberCode","missing");raw.put("createTime",null);payload();candidate(null,false,"NO_EXACT_ACCOUNT");
        request=new ProjectMemberHistoryImportService.Request(sourceRequest,12L,ProjectMemberHistoryImportService.fingerprint(stored),null,null,Set.of());
        var row=service.migrate(100L,request,actor).report().targets().getFirst().member();
        assertNull(row.getUserId());assertFalse(row.getAuthorizationConfirmed());assertNull(row.getCreateTime());assertNull(row.getUpdateTime());assertNull(row.getAssignmentType());
        assertEquals("missing",row.getMemberCode());assertNull(project.getManagerId());
    }
    @Test void adjudicatedAdministratorIsEndedWithoutDeletingOrOverwritingCreationHistory() {
        var admin=admin();stored.add(admin);request=request(Set.of(45L));var create=admin.getCreateTime();
        var report=service.migrate(100L,request,actor).report();assertEquals(1,report.closedIntervals().size());
        assertEquals(report.executedAt(),admin.getEffectiveTo());assertEquals(create,admin.getCreateTime());assertEquals(1L,admin.getUserId());assertEquals(2,stored.size());
    }
    @Test void aLaterProjectVersionStopsBeforeAnyBusinessWrite() {
        project.setVersion(13L);assertThrows(IllegalArgumentException.class,()->service.migrate(100L,request,actor));verify(members,never()).insertHistorical(any());verifyNoInteractions(evidence);
    }
    @Test void aLaterMemberFieldStopsBeforeAnyBusinessWrite() {
        stored.add(admin());assertThrows(IllegalArgumentException.class,()->service.migrate(100L,request,actor));verify(members,never()).closeApprovedInterval(any());verify(members,never()).insertHistorical(any());
    }
    @Test void anUnadjudicatedPrimaryConflictCannotBeSilentlyReassigned() {
        stored.add(admin());request=request(Set.of());assertThrows(IllegalArgumentException.class,()->service.migrate(100L,request,actor));verify(members,never()).insertHistorical(any());
    }
    @Test void nonAdministratorOrNonCurrentClosureCannotEscapeTheApprovedBoundary() {
        var row=admin();row.setUserId(50L);stored.add(row);request=request(Set.of(45L));
        assertThrows(IllegalArgumentException.class,()->service.migrate(100L,request,actor));verify(members,never()).closeApprovedInterval(any());
    }
    @Test void duplicateOverlappingIntervalsAreBlockedButAdjacentIntervalsAreAllowed() {
        var a=admin();var b=admin();b.setId(46L);b.setEffectiveFrom(a.getEffectiveFrom().plusHours(1));
        assertThrows(IllegalArgumentException.class,()->ProjectMemberHistoryImportService.validateIntervals(List.of(a,b)));
        a.setEffectiveTo(b.getEffectiveFrom());assertDoesNotThrow(()->ProjectMemberHistoryImportService.validateIntervals(List.of(a,b)));
    }
    @Test void unknownAuditActorIsPendingRatherThanReplacedByAdministrator() {
        raw.put("createBy","unknown");payload();assertThrows(IllegalArgumentException.class,()->service.migrate(100L,request,actor));verify(members,never()).insertHistorical(any());
    }
    @Test void replayCannotHideAMissingOrChangedImmutableSourceBinding() {
        var report=service.migrate(100L,request,actor).report();
        when(commands.execute(any(),anyString(),eq(ProjectMemberHistoryImportService.Report.class),any(),any())).thenReturn(new ExecutionResult<>(Decision.REPLAY_COMPLETED,report));
        when(evidence.statuses(eq(1L),eq("51922"),eq(20L),anyList())).thenReturn(Map.of());
        assertThrows(IllegalArgumentException.class,()->service.migrate(100L,request,actor));
        verify(members,times(1)).insertHistorical(any());
    }
    @Test void mysqlJsonDecimalNormalizationDoesNotHideARealProgressChange() {
        project.setProgress(new java.math.BigDecimal("0.00"));var original=service.migrate(100L,request,actor).report();
        var normalized=cn.hutool.core.bean.BeanUtil.copyProperties(original.projectAfter(),ProjectMasterDO.class);
        normalized.setProgress(new java.math.BigDecimal("0.0"));
        var cached=new ProjectMemberHistoryImportService.Report(original.sourceId(),original.snapshotId(),normalized,original.targets(),original.closedIntervals(),original.executedAt());
        when(commands.execute(any(),anyString(),eq(ProjectMemberHistoryImportService.Report.class),any(),any())).thenReturn(new ExecutionResult<>(Decision.REPLAY_COMPLETED,cached));
        when(evidence.statuses(eq(1L),eq("51922"),eq(20L),anyList())).thenReturn(Map.of("99",new ProjectLegacyNativeEvidenceService.RowStatus("MAPPED",null,null,
            List.of(new cn.iocoder.yudao.module.pms.platform.api.migration.dto.ExternalTargetMapping("PROJ","ProjectMemberAssignment","proj_project_member_assignment",original.targets().getFirst().member().getId(),"MEMBER_HISTORY",0)))));
        assertEquals("REPLAY_COMPLETED",service.migrate(100L,request,actor).decision());
        project.setProgress(new java.math.BigDecimal("0.01"));
        assertThrows(IllegalArgumentException.class,()->service.migrate(100L,request,actor));
    }
    @Test void anUnexpectedLifecycleOrHistoricalAuditChangeCannotProduceSuccessfulEvidence() {
        doAnswer(inv->{project.setLifecycleStatus("ACTIVE");return 1;}).when(members).updateApprovedPrimary(any());
        assertThrows(IllegalArgumentException.class,()->service.migrate(100L,request,actor));
        verifyNoInteractions(evidence);assertNull(facts);
    }
    @Test void replayProtectsSubsequentMemberChangesAndUsesAnOperatorIndependentInitialKey() {
        var report=service.migrate(100L,request,actor).report();
        when(evidence.statuses(eq(1L),eq("51922"),eq(20L),anyList())).thenReturn(Map.of("99",new ProjectLegacyNativeEvidenceService.RowStatus("MAPPED",null,null,
            List.of(new cn.iocoder.yudao.module.pms.platform.api.migration.dto.ExternalTargetMapping("PROJ","ProjectMemberAssignment","proj_project_member_assignment",report.targets().getFirst().member().getId(),"MEMBER_HISTORY",0)))));
        when(commands.execute(any(),anyString(),eq(ProjectMemberHistoryImportService.Report.class),any(),any())).thenReturn(new ExecutionResult<>(Decision.REPLAY_COMPLETED,report));
        assertEquals("REPLAY_COMPLETED",service.migrate(100L,request,actor).decision());
        var changed=JsonUtils.parseObject(JsonUtils.toJsonString(stored.getFirst()),ProjectMemberAssignmentDO.class);changed.setMemberName("人工修改");stored.set(0,changed);
        assertThrows(IllegalArgumentException.class,()->service.migrate(100L,request,actor));
        verify(commands,atLeastOnce()).execute(argThat(scope->scope.actorId()==0L && scope.key().equals("DPPMS-MEMBER-INITIAL:100:10")),anyString(),eq(ProjectMemberHistoryImportService.Report.class),any(),any());
    }
}
