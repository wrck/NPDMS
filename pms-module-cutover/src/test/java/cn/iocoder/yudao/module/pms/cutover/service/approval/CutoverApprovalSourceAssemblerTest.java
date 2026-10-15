package cn.iocoder.yudao.module.pms.cutover.service.approval;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.CutoverApprovalStartCommand;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistItemDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistItemResultDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.plan.CutoverPlanRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverAssessmentDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.*;
import cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverApprovalSourceSnapshotCodec;
import cn.iocoder.yudao.module.pms.cutover.service.plan.CutoverPlanStorage;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverProjectContextPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CutoverApprovalSourceAssemblerTest {
    private final CutoverTaskMapper tasks = mock(CutoverTaskMapper.class);
    private final CutoverAssessmentMapper assessments = mock(CutoverAssessmentMapper.class);
    private final CutoverChecklistMapper checklists = mock(CutoverChecklistMapper.class);
    private final CutoverChecklistItemMapper items = mock(CutoverChecklistItemMapper.class);
    private final CutoverChecklistItemResultMapper results = mock(CutoverChecklistItemResultMapper.class);
    private final CutoverPlanRevisionMapper plans = mock(CutoverPlanRevisionMapper.class);
    private final CutoverProjectContextPort projects = mock(CutoverProjectContextPort.class);
    private final CutoverPlanStorage storage = mock(CutoverPlanStorage.class);
    private final CutoverApprovalSourceSnapshotCodec codec = new CutoverApprovalSourceSnapshotCodec();
    private final CutoverApprovalSourceAssembler service = new CutoverApprovalSourceAssembler(tasks, assessments,
            checklists, items, results, plans, mock(CutoverPlanStepMapper.class),
            mock(CutoverSupportArrangementMapper.class), codec);
    private final CutoverTaskDO task = new CutoverTaskDO();
    private final CutoverAssessmentDO assessment = new CutoverAssessmentDO();
    private final CutoverPlanRevisionDO plan = new CutoverPlanRevisionDO();

    @BeforeEach
    void fixture() {
        ReflectionTestUtils.setField(service, "projectContext", projects);
        ReflectionTestUtils.setField(service, "planStorage", storage);
        task.setId(1001L); task.setTenantId(1L); task.setProjectId(3001L); task.setProjectVersion(9L);
        task.setVersion(5L); task.setCurrentStage("P4"); task.setTaskStatus("PLAN_DRAFTING");
        task.setCutoverType("VERSION_UPGRADE"); task.setScheduledTime(LocalDateTime.of(2026, 10, 16, 8, 0));
        assessment.setId(6001L); assessment.setAssessmentVersion(2L); assessment.setAssessmentStatus("SUBMITTED");
        assessment.setManualGrade("D"); assessment.setQuestionnaireTemplateVersion(7001L);
        assessment.setBusinessImportanceLevel("HIGH"); assessment.setOperationComplexityLevel("MEDIUM");
        assessment.setHiddenRiskLevel("LOW"); assessment.setSparePartAppliedFlag(false);
        assessment.setServiceLevelCode("GOLD"); assessment.setSubmittedBy(8001L);
        assessment.setSubmittedAt(LocalDateTime.of(2026, 10, 15, 8, 0));
        plan.setId(9001L); plan.setTenantId(1L); plan.setCutoverTaskId(1001L); plan.setRevisionNo(2);
        plan.setVersion(0L); plan.setStatusCode("DRAFT"); plan.setGradeCode("D");
        plan.setSourceProjectId(3001L); plan.setSourceProjectVersion(10L); plan.setSourceProjectScopeVersion(12L);
        plan.setEditModeCode("FULL_FILE_UPLOAD"); plan.setFileArtifactId(3301L); plan.setFileVersionNo(1);
        plan.setFileReferenceKey("plan-revision-2"); plan.setFileArtifactFactVersion(1L);
        plan.setFileReferenceFactVersion(1L); plan.setFileAvailabilityFactVersion(1L);
        plan.setFileScopeVersion(2L); plan.setFileSha256("a".repeat(64)); plan.setOwnershipConfirmed(true);
        when(plans.selectById(9001L)).thenReturn(plan);
        when(plans.selectByIdForUpdate(any())).thenReturn(plan);
        when(tasks.selectForUpdate(any())).thenReturn(task);
        when(assessments.selectForUpdate(any())).thenReturn(assessment);
        when(projects.inspect(any())).thenReturn(project(10));
        when(projects.lockAndRevalidate(any())).thenReturn(project(10));
        when(storage.sourceView(plan)).thenAnswer(invocation -> JsonUtils.parseTree("""
                {"snapshotVersion":1,"taskId":1001,"taskVersion":5,
                 "assessmentId":6001,"assessmentVersion":2,"grade":"D",
                 "checklistId":null,"checklistVersion":null,
                 "projectId":3001,"projectVersion":10,"projectScopeVersion":12,
                 "devices":[{"deviceId":3101,"serialNumber":"SN-001","projectAssignmentVersion":2,
                   "deviceTypeCode":"ROUTER","deviceTypeSourceVersion":"TYPE-V1"}],
                 "configurationRevisionId":3201,"configurationCode":"CUT-CONF","configurationRevisionNo":1,
                 "templateSections":[
                   {"stableSectionKey":"OPERATION","title":"Operation","sortOrder":1,
                    "cutoverTypeCodes":["VERSION_UPGRADE"],"levelCodes":["D"],"required":true},
                   {"stableSectionKey":"ROLLBACK","title":"Rollback","sortOrder":2,
                    "cutoverTypeCodes":["VERSION_UPGRADE"],"levelCodes":["D"],"required":true}],
                 "failedRiskFacts":[]}
                """));
    }

    @Test
    void revisedPlanUsesItsRevalidatedOwnerProjectWhileTaskHistoryStaysFrozen() {
        var assembled = service.lockAndAssemble(command("D"));
        var snapshot = codec.decode(assembled.sourceSnapshot());

        assertEquals(10, snapshot.project().projectVersion());
        assertEquals(10, snapshot.plan().sourceSnapshot().get("projectVersion").asInt());
        assertEquals(9L, task.getProjectVersion());
        var order = inOrder(projects, tasks);
        order.verify(projects).inspect(any());
        order.verify(projects).lockAndRevalidate(project(10));
        order.verify(tasks).selectForUpdate(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsProjectChangesBeforeLockingCutoverFacts(boolean changedDuringLock) {
        if (changedDuringLock) when(projects.lockAndRevalidate(any())).thenReturn(project(11));
        else when(projects.inspect(any())).thenReturn(project(11));

        var failure = assertThrows(CutoverApprovalApplicationException.class,
                () -> service.lockAndAssemble(command("D")));

        assertEquals(CutoverApprovalApplicationException.Code.SOURCE_STALE, failure.code());
        verifyNoInteractions(tasks, assessments, checklists, items, results);
        assertEquals(9L, task.getProjectVersion());
    }

    @Test
    void rejectsMissingPlanProjectSourceWithoutReadingAnUnscopedProject() {
        plan.setSourceProjectScopeVersion(null);

        var failure = assertThrows(CutoverApprovalApplicationException.class,
                () -> service.lockAndAssemble(command("D")));

        assertEquals(CutoverApprovalApplicationException.Code.SOURCE_STALE, failure.code());
        verifyNoInteractions(projects, tasks);
    }

    @Test
    void refusesNewApprovalFromLegacyManualCollectionWithoutFailureEvidence() {
        assessment.setManualGrade("A"); plan.setGradeCode("A");
        var checklist = new CutoverChecklistDO();
        checklist.setId(2001L); checklist.setStatusCode("SUBMITTED"); checklist.setChecklistVersion(3L);
        when(checklists.selectCurrentForUpdate(any())).thenReturn(checklist);
        var item = new CutoverChecklistItemDO(); item.setId(201L); item.setApplicableFlag(true);
        item.setItemName("系统日志"); item.setWorkModeCode("COLLECTION");
        var result = new CutoverChecklistItemResultDO(); result.setChecklistItemId(201L);
        result.setResultSourceCode("MANUAL"); result.setFactDescription("历史人工材料，无自动失败来源");
        when(items.selectListForUpdate(any())).thenReturn(List.of(item));
        when(results.selectCurrentByChecklistForUpdate(any())).thenReturn(List.of(result));

        var failure = assertThrows(CutoverApprovalApplicationException.class,
                () -> service.lockAndAssemble(command("A")));

        assertEquals(CutoverApprovalApplicationException.Code.BUSINESS_INCOMPLETE, failure.code());
        assertTrue(failure.getMessage().contains("人工降级缺少自动失败来源"));
        verify(plans, never()).selectByIdForUpdate(any());
        assertNull(result.getOriginalFailureCode());
        assertEquals("历史人工材料，无自动失败来源", result.getFactDescription());
    }

    private static CutoverApprovalStartCommand command(String grade) {
        return new CutoverApprovalStartCommand(1L, 1001L, 5L, 9001L, 2, grade, 6001L, 2L,
                "D".equals(grade) ? null : 2001L, "D".equals(grade) ? null : 3L, 1,
                LocalDateTime.of(2026, 10, 15, 9, 0), null, "approval-source", "approval-source");
    }

    private static CutoverProjectContextPort.ProjectContextFact project(int version) {
        return CutoverProjectContextPort.ProjectContextFact.builder().tenantId(1L).projectId(3001L)
                .projectVersion(version).projectCode("P-001").projectName("Current project")
                .customerId(4001L).customerCode("C-001").customerName("Customer")
                .departmentId(5001L).departmentCode("OFF-01").departmentName("Office")
                .projectScopeVersion(12L).build();
    }
}
