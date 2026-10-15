package cn.iocoder.yudao.module.pms.service.service.inspection;

import cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.InspectionEvidenceDO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.InspectionTaskDO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.ServiceIssueDO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.ServiceIssueRemediationDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.InspectionEvidenceMapper;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.InspectionTaskMapper;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.ServiceIssueMapper;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.ServiceIssueRemediationMapper;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.InspectionTaskRows;
import cn.iocoder.yudao.module.pms.service.domain.inspection.InspectionTaskStateMachine;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServiceIssueServiceTest {
    private final InspectionAccessService access = mock(InspectionAccessService.class);
    private final InspectionTaskMapper taskMapper = mock(InspectionTaskMapper.class);
    private final InspectionTaskService tasks = mock(InspectionTaskService.class);
    private final InspectionClosureFacts closure = mock(InspectionClosureFacts.class);
    private final ServiceIssueMapper issues = mock(ServiceIssueMapper.class);
    private final ServiceIssueRemediationMapper remediations = mock(ServiceIssueRemediationMapper.class);
    private final InspectionEvidenceMapper evidence = mock(InspectionEvidenceMapper.class);
    private final InspectionFiles files = mock(InspectionFiles.class);
    private final FileEvidenceApi fileEvidence = mock(FileEvidenceApi.class);
    private final PermissionApi permissions = mock(PermissionApi.class);
    private final AdminUserApi users = mock(AdminUserApi.class);
    private ServiceIssueService service;
    private InspectionTaskDO task;
    private ServiceIssueDO snapshotIssue;
    private ServiceIssueDO currentIssue;
    private boolean taskLocked;

    @BeforeEach
    void setup() {
        service = new ServiceIssueService(access, taskMapper, tasks, closure, issues,
                remediations, evidence, files, fileEvidence, permissions, users);
        task = new InspectionTaskDO();
        task.setId(10L); task.setTenantId(1L); task.setVersion(5L); task.setProjectId(100L);
        task.setOwnerUserId(4L); task.setStatusCode("TRACKING");
        snapshotIssue = issue(); currentIssue = issue();
        when(issues.selectById(50L)).thenAnswer(call -> snapshotIssue);
        when(taskMapper.selectById(10L)).thenReturn(task);
        when(taskMapper.selectForUpdate(any(InspectionTaskRows.class))).thenAnswer(call -> {
            taskLocked = true; return task;
        });
        // Simulate the committed issue becoming visible only after waiting for the task lock.
        when(issues.selectForTaskForUpdate(any(InspectionTaskRows.class))).thenAnswer(call ->
                List.of(taskLocked ? currentIssue : snapshotIssue));
        when(issues.updateById(any(ServiceIssueDO.class))).thenReturn(1);
        when(remediations.selectForTask(any(InspectionTaskRows.class))).thenReturn(List.of());
        when(evidence.selectForTask(any(InspectionTaskRows.class))).thenReturn(List.of());
    }

    @Test
    void formerResponsibleUserCannotProcessWhenResponsibilityChangedWhileWaitingForTaskLock() {
        currentIssue.setResponsibleUserId(3L);

        var error = assertThrows(InspectionException.class, () -> service.process(process(context(2L, 7L))));

        assertEquals(InspectionException.Reason.FORBIDDEN, error.getReason());
        assertEquals("PENDING", currentIssue.getStatusCode());
        assertEquals(2L, snapshotIssue.getResponsibleUserId());
        assertNoWrites();
    }

    @Test
    void newlyAssignedResponsibleUserCanProcessTheLockedIssueWithoutRewritingTheOldSnapshot() {
        currentIssue.setResponsibleUserId(3L);

        var view = service.process(process(context(3L, 7L)));

        assertEquals("PROCESSING", view.getStatusCode());
        assertEquals("PROCESSING", currentIssue.getStatusCode());
        assertEquals("PENDING", snapshotIssue.getStatusCode());
        var note = ArgumentCaptor.forClass(ServiceIssueRemediationDO.class);
        verify(remediations).insert(note.capture());
        assertEquals(3L, note.getValue().getActorId());
        assertEquals(50L, note.getValue().getIssueId());
        assertEquals("已完成问题处理", note.getValue().getContent());
        verify(issues).updateById(same(currentIssue));
        verify(tasks, never()).transition(any());
    }

    @Test
    void versionCommittedWhileWaitingForLockRejectsThePreviouslyReadVersion() {
        currentIssue.setVersion(8L);

        var error = assertThrows(InspectionException.class, () -> service.process(process(context(2L, 7L))));

        assertEquals(InspectionException.Reason.VERSION_CONFLICT, error.getReason());
        assertEquals(8L, error.getCurrentVersion());
        assertEquals(7L, snapshotIssue.getVersion());
        assertNoWrites();
    }

    @Test
    void issueInAnotherTenantCannotBeProcessedEvenByItsRecordedResponsibleUser() {
        snapshotIssue.setTenantId(2L);

        var error = assertThrows(InspectionException.class, () -> service.process(process(context(2L, 7L))));

        assertEquals(InspectionException.Reason.NOT_FOUND, error.getReason());
        verify(taskMapper, never()).selectForUpdate(any());
        assertNoWrites();
    }

    @Test
    void issueCannotUseATaskFromAnotherTenantAsItsWriteScope() {
        task.setTenantId(2L);

        assertThrows(InspectionException.class, () -> service.process(process(context(2L, 7L))));

        verify(taskMapper, never()).selectForUpdate(any());
        assertNoWrites();
    }

    @Test
    void responsibleIdentityDoesNotReplaceTheRemediationPermission() {
        var actor = context(2L, 7L);
        doThrow(new InspectionException(InspectionException.Reason.FORBIDDEN, "没有处理权限"))
                .when(access).permission(actor, "pms:service-issue:remediate");

        assertEquals(InspectionException.Reason.FORBIDDEN,
                assertThrows(InspectionException.class, () -> service.process(process(actor))).getReason());
        assertEquals(InspectionException.Reason.FORBIDDEN,
                assertThrows(InspectionException.class, () -> service.requestClosure(new ServiceIssueService.Closure(actor, 50L, "申请闭环"))).getReason());

        verify(issues, never()).selectById(anyLong());
        assertNoWrites();
    }

    @Test
    void taskOwnerCannotConfirmWithoutTheIndependentConfirmationPermission() {
        var owner = context(4L, 7L);
        currentIssue.setStatusCode("CLOSURE_PENDING");
        doThrow(new InspectionException(InspectionException.Reason.FORBIDDEN, "没有确认权限"))
                .when(access).permission(owner, "pms:service-issue:confirm");

        var error = assertThrows(InspectionException.class,
                () -> service.confirmClosure(new ServiceIssueService.Closure(owner, 50L, "确认闭环")));

        assertEquals(InspectionException.Reason.FORBIDDEN, error.getReason());
        assertNoWrites();
    }

    @Test
    void responsibleUserWithConfirmationPermissionStillCannotConfirmTheirOwnIssueWithoutOwnerOrManagerRole() {
        currentIssue.setStatusCode("CLOSURE_PENDING");

        var error = assertThrows(InspectionException.class,
                () -> service.confirmClosure(new ServiceIssueService.Closure(context(2L, 7L), 50L, "确认闭环")));

        assertEquals(InspectionException.Reason.FORBIDDEN, error.getReason());
        assertNoWrites();
    }

    @Test
    void closureRequestRequiresProcessHistoryAndUsableEvidenceForThisIssue() {
        for (var state : InvalidProof.values()) {
            invalidProof(state);

            assertThrows(InspectionException.class,
                    () -> service.requestClosure(new ServiceIssueService.Closure(context(2L, 7L), 50L, "申请闭环")), state.name());
            assertEquals("PENDING", currentIssue.getStatusCode());
        }
        assertNoWrites();
    }

    @Test
    void confirmationRechecksProcessHistoryAndEvidenceRatherThanTrustingThePendingClosureState() {
        currentIssue.setStatusCode("CLOSURE_PENDING");
        for (var state : InvalidProof.values()) {
            invalidProof(state);

            assertThrows(InspectionException.class,
                    () -> service.confirmClosure(new ServiceIssueService.Closure(context(4L, 7L), 50L, "确认闭环")), state.name());
            assertEquals("CLOSURE_PENDING", currentIssue.getStatusCode());
            assertNull(currentIssue.getClosedAt());
        }
        assertNoWrites();
        verify(closure, never()).current(any());
    }

    @Test
    void archivedTaskRejectsBothResponsibleProcessingAndOwnerConfirmation() {
        task.setStatusCode("ARCHIVED");
        assertThrows(InspectionException.class, () -> service.process(process(context(2L, 7L))));
        currentIssue.setStatusCode("CLOSURE_PENDING");
        assertThrows(InspectionException.class,
                () -> service.confirmClosure(new ServiceIssueService.Closure(context(4L, 7L), 50L, "确认闭环")));

        assertEquals("ARCHIVED", task.getStatusCode());
        assertNoWrites();
    }

    @Test
    void cancelledTaskAllowsResponsibleRemediationWithoutAdvancingItsTerminalTaskState() {
        task.setStatusCode("CANCELLED");

        service.process(process(context(2L, 7L)));

        assertEquals("PROCESSING", currentIssue.getStatusCode());
        assertEquals("CANCELLED", task.getStatusCode());
        verify(remediations).insert(any(ServiceIssueRemediationDO.class));
        var save = ArgumentCaptor.forClass(InspectionTaskService.Save.class);
        verify(tasks).save(save.capture());
        assertEquals("CANCELLED", save.getValue().previousStatus());
        verify(tasks, never()).transition(any());
    }

    @Test
    void confirmingOneIssueKeepsTrackingWhenAnotherIssueRemainsOpen() {
        readyForConfirmation(new InspectionClosureFacts.Facts(true, true, 1));

        service.confirmClosure(new ServiceIssueService.Closure(context(4L, 7L), 50L, "确认处理证据有效"));

        assertEquals("CLOSED", currentIssue.getStatusCode());
        assertEquals("TRACKING", task.getStatusCode());
        assertEquals(4L, currentIssue.getClosedBy());
        assertNotNull(currentIssue.getClosedAt());
        verify(tasks).save(any(InspectionTaskService.Save.class));
        verify(tasks, never()).transition(any());
    }

    @Test
    void allIssuesClosedInTrackingRequestsTheDomainClosureTransitionWithCurrentFacts() {
        readyForConfirmation(new InspectionClosureFacts.Facts(true, true, 0));

        service.confirmClosure(new ServiceIssueService.Closure(context(4L, 7L), 50L, "确认处理证据有效"));

        var change = ArgumentCaptor.forClass(InspectionTaskService.Change.class);
        verify(tasks).transition(change.capture());
        assertEquals(InspectionTaskStateMachine.Action.ISSUES_CLOSED, change.getValue().transition().action());
        assertEquals("CLOSED", InspectionTaskStateMachine.next(change.getValue().transition()));
        verify(tasks, never()).save(any());
        assertEquals("CLOSED", currentIssue.getStatusCode());
    }

    @Test
    void allIssuesClosedOnCancelledTaskDoNotRequestAMainTaskTransition() {
        task.setStatusCode("CANCELLED");
        readyForConfirmation(new InspectionClosureFacts.Facts(true, true, 0));

        service.confirmClosure(new ServiceIssueService.Closure(context(4L, 7L), 50L, "确认处理证据有效"));

        assertEquals("CLOSED", currentIssue.getStatusCode());
        assertEquals("CANCELLED", task.getStatusCode());
        verify(tasks).save(any(InspectionTaskService.Save.class));
        verify(tasks, never()).transition(any());
    }

    private enum InvalidProof { NO_PROCESS, NO_EVIDENCE, OTHER_ISSUE, NON_PROCESS_RECORD, UNAVAILABLE_FILE }

    private void invalidProof(InvalidProof state) {
        var note = processNote();
        if (state == InvalidProof.OTHER_ISSUE) note.setIssueId(51L);
        if (state == InvalidProof.NON_PROCESS_RECORD) note.setActionCode("REQUEST_CLOSURE");
        when(remediations.selectForTask(any(InspectionTaskRows.class)))
                .thenReturn(state == InvalidProof.NO_PROCESS ? List.of() : List.of(note));
        when(evidence.selectForTask(any(InspectionTaskRows.class)))
                .thenReturn(state == InvalidProof.NO_EVIDENCE ? List.of() : List.of(processEvidence()));
        when(fileEvidence.lockAndRevalidate(any(FileEvidenceApi.Query.class)))
                .thenReturn(new FileEvidenceApi.Fact(state != InvalidProof.UNAVAILABLE_FILE, "文件不可用", 1, 1, 1));
    }

    private void readyForConfirmation(InspectionClosureFacts.Facts facts) {
        currentIssue.setStatusCode("CLOSURE_PENDING");
        when(remediations.selectForTask(any(InspectionTaskRows.class))).thenReturn(List.of(processNote()));
        when(evidence.selectForTask(any(InspectionTaskRows.class))).thenReturn(List.of(processEvidence()));
        when(fileEvidence.lockAndRevalidate(any(FileEvidenceApi.Query.class)))
                .thenReturn(new FileEvidenceApi.Fact(true, null, 1, 1, 1));
        when(closure.current(any(InspectionClosureFacts.Query.class))).thenAnswer(call -> {
            // Count facts are consumed only after this issue's confirmed closure is persisted.
            assertEquals("CLOSED", currentIssue.getStatusCode());
            verify(issues).updateById(same(currentIssue));
            return facts;
        });
    }

    private ServiceIssueRemediationDO processNote() {
        var row = new ServiceIssueRemediationDO();
        row.setId(70L); row.setTenantId(1L); row.setTaskId(10L); row.setIssueId(50L);
        row.setActionCode("PROCESS"); row.setContent("处理记录"); row.setActorId(2L);
        return row;
    }

    private InspectionEvidenceDO processEvidence() {
        var row = new InspectionEvidenceDO();
        row.setId(80L); row.setTenantId(1L); row.setTaskId(10L); row.setRemediationId(70L);
        row.setArtifactId(90L); row.setFileVersionNo(2); row.setReferenceKey("issue:50:evidence"); row.setSha256("digest");
        return row;
    }

    private static ServiceIssueDO issue() {
        var row = new ServiceIssueDO();
        row.setId(50L); row.setTenantId(1L); row.setVersion(7L); row.setTaskId(10L);
        row.setResponsibleUserId(2L); row.setStatusCode("PENDING");
        return row;
    }

    private static InspectionRequestContext context(Long actorId, Long version) {
        return new InspectionRequestContext(1L, actorId, "issue-action", "test", version);
    }

    private static ServiceIssueService.Process process(InspectionRequestContext context) {
        return new ServiceIssueService.Process(context, 50L, "  已完成问题处理  ", List.of());
    }

    private void assertNoWrites() {
        verify(remediations, never()).insert(any(ServiceIssueRemediationDO.class));
        verify(evidence, never()).insert(any(InspectionEvidenceDO.class));
        verify(issues, never()).updateById(any(ServiceIssueDO.class));
        verify(tasks, never()).save(any());
        verify(tasks, never()).transition(any());
    }
}
