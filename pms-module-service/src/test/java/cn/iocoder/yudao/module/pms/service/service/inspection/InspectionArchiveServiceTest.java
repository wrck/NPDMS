package cn.iocoder.yudao.module.pms.service.service.inspection;

import cn.iocoder.yudao.module.pms.customer.api.inspection.CustomerInspectionHistoryApi;
import cn.iocoder.yudao.module.pms.customer.api.inspection.CustomerInspectionHistoryApi.Kind;
import cn.iocoder.yudao.module.pms.customer.api.inspection.CustomerInspectionHistoryApi.SourceReference;
import cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.*;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.*;
import cn.iocoder.yudao.module.pms.service.domain.inspection.InspectionTaskStateMachine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InspectionArchiveServiceTest {
    private static final String EVIDENCE_SHA256 = "a".repeat(64);
    @Mock InspectionAccessService access;
    @Mock InspectionTaskService tasks;
    @Mock InspectionClosureFacts closure;
    @Mock CustomerInspectionHistoryApi customerHistory;
    @Mock InspectionTaskDeviceMapper devices;
    @Mock InspectionExecutionMapper executions;
    @Mock InspectionDetectionResultMapper results;
    @Mock InspectionReportRevisionMapper reports;
    @Mock InspectionAnnotationMapper annotations;
    @Mock ServiceIssueMapper issues;
    @Mock ServiceIssueRemediationMapper remediations;
    @Mock InspectionEvidenceMapper evidence;
    @Mock FileEvidenceApi fileEvidence;
    @InjectMocks InspectionArchiveService service;

    @Test void failedCustomerRegistrationCannotMarkTheTaskArchived() {
        var task = closedTask(); when(access.task(any())).thenReturn(task);
        when(closure.current(any())).thenReturn(new InspectionClosureFacts.Facts(true, true, 0));
        when(customerHistory.register(any())).thenThrow(new IllegalStateException("CUS unavailable"));
        assertThrows(IllegalStateException.class, () -> service.archive(command()));
        assertEquals("CLOSED", task.getStatusCode()); assertNull(task.getCustomerArchiveId()); assertNull(task.getArchivedAt());
        verifyNoInteractions(tasks);
    }

    @Test void returnedReferenceMustMatchTheActualTaskAndFinalVersion() {
        var task = closedTask(); when(access.task(any())).thenReturn(task);
        when(closure.current(any())).thenReturn(new InspectionClosureFacts.Facts(true, true, 0));
        when(customerHistory.register(any())).thenReturn(new CustomerInspectionHistoryApi.ArchiveReference(100L, 999L, 6L));
        assertThrows(InspectionException.class, () -> service.archive(command()));
        assertNull(task.getCustomerArchiveId()); verifyNoInteractions(tasks);
    }

    @Test void archiveUsesManagerAuthorizationAndTheStateMachineAfterRegistration() {
        var task = closedTask(); when(access.task(any())).thenReturn(task);
        when(closure.current(any())).thenReturn(new InspectionClosureFacts.Facts(true, true, 0));
        when(customerHistory.register(any())).thenReturn(new CustomerInspectionHistoryApi.ArchiveReference(100L, 10L, 6L));
        service.archive(command());
        var permission = ArgumentCaptor.forClass(InspectionAccessService.TaskAccess.class); verify(access).task(permission.capture());
        assertTrue(permission.getValue().managerOnly()); assertTrue(permission.getValue().write());
        var change = ArgumentCaptor.forClass(InspectionTaskService.Change.class); verify(tasks).transition(change.capture());
        assertEquals(InspectionTaskStateMachine.Action.ARCHIVE, change.getValue().transition().action());
        assertEquals("ARCHIVED", InspectionTaskStateMachine.next(change.getValue().transition()));
        var source = ArgumentCaptor.forClass(CustomerInspectionHistoryApi.ArchiveCommand.class); verify(customerHistory).register(source.capture());
        assertEquals(6L, source.getValue().archivedTaskVersion()); assertEquals(100L, task.getCustomerArchiveId());
    }

    @Test void invalidFrozenEvidenceBlocksCustomerRegistration() {
        var task = closedTask(); when(access.task(any())).thenReturn(task);
        when(closure.current(any())).thenReturn(new InspectionClosureFacts.Facts(true, true, 0));
        var row = new InspectionEvidenceDO(); row.setId(55L); row.setTenantId(1L); row.setTaskId(10L); row.setArtifactId(8L);
        row.setFileVersionNo(2); row.setReferenceKey("issue:4:proof"); row.setSha256("digest");
        when(evidence.selectForTask(any())).thenReturn(List.of(row));
        assertThrows(InspectionException.class, () -> service.archive(command()));
        verify(fileEvidence).lockAndRevalidate(any()); verifyNoInteractions(customerHistory, tasks);
    }

    @Test void openTrackingOrMissingRequestCannotBeBypassed() {
        var task = closedTask(); when(access.task(any())).thenReturn(task);
        when(closure.current(any())).thenReturn(new InspectionClosureFacts.Facts(true, true, 1));
        assertThrows(InspectionException.class, () -> service.archive(command()));
        verifyNoInteractions(customerHistory, tasks);
    }

    @Test void reassignedEngineerMustSubmitANewArchiveRequest() {
        var task = closedTask(); task.setOwnerUserId(99L); when(access.task(any())).thenReturn(task);
        assertThrows(InspectionException.class, () -> service.request(command()));
        verifyNoInteractions(customerHistory, closure, tasks);
    }

    @Test
    void managerAuthorizationDenialStopsBeforeAnyClosureOrCustomerWrite() {
        var denied = new InspectionException(InspectionException.Reason.FORBIDDEN, "不是当前服务经理");
        when(access.task(any())).thenThrow(denied);

        assertSame(denied, assertThrows(InspectionException.class, () -> service.archive(command())));

        var permission = ArgumentCaptor.forClass(InspectionAccessService.TaskAccess.class);
        verify(access).task(permission.capture());
        assertEquals("pms:inspection-task:archive", permission.getValue().permission());
        assertTrue(permission.getValue().write());
        assertTrue(permission.getValue().managerOnly());
        verifyNoInteractions(closure, customerHistory, tasks, issues, evidence, fileEvidence);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("incompleteClosureCases")
    void incompleteBusinessClosureCannotRegisterCustomerHistory(String scenario,
            InspectionClosureFacts.Facts facts, boolean requested) {
        var task = closedTask();
        if (!requested) task.setArchiveRequestedAt(null);
        if (!facts.reportAvailable()) task.setCurrentReportId(null);
        when(access.task(any())).thenReturn(task);
        when(closure.current(any())).thenReturn(facts);

        assertThrows(InspectionException.class, () -> service.archive(command()));

        assertNull(task.getCustomerArchiveId());
        assertNull(task.getArchivedAt());
        verifyNoInteractions(customerHistory, tasks, evidence, fileEvidence);
    }

    private static Stream<Arguments> incompleteClosureCases() {
        return Stream.of(
                Arguments.of("未形成可用报告", new InspectionClosureFacts.Facts(false, true, 0), true),
                Arguments.of("异常标注未完成", new InspectionClosureFacts.Facts(true, false, 0), true),
                Arguments.of("尚无归档申请", new InspectionClosureFacts.Facts(true, true, 0), false));
    }

    @Test
    void closedIssueWithoutProcessingEvidenceCannotReachCustomerArchive() {
        var task = closedTask();
        allowClosedArchive(task);
        when(issues.selectForTask(any())).thenReturn(List.of(historicalClosedIssue()));
        when(remediations.selectForTask(any())).thenReturn(List.of(historicalProcess()));

        assertThrows(InspectionException.class, () -> service.archive(command()));

        assertNull(task.getCustomerArchiveId());
        verifyNoInteractions(customerHistory, tasks, fileEvidence);
    }

    @Test
    void evidenceAttachedOnlyToReassignmentCannotReplaceProcessingEvidence() {
        var task = closedTask();
        allowClosedArchive(task);
        var reassignment = historicalProcess();
        reassignment.setActionCode("REASSIGN");
        when(issues.selectForTask(any())).thenReturn(List.of(historicalClosedIssue()));
        when(remediations.selectForTask(any())).thenReturn(List.of(reassignment));
        when(evidence.selectForTask(any())).thenReturn(List.of(historicalEvidence()));

        assertThrows(InspectionException.class, () -> service.archive(command()));

        assertNull(task.getCustomerArchiveId());
        verifyNoInteractions(customerHistory, tasks, fileEvidence);
    }

    @Test
    void rejectedPlatformEvidenceCannotReachCustomerArchiveAndUsesItsExactFrozenScope() {
        var task = closedTask();
        allowClosedArchive(task);
        var proof = historicalEvidence();
        when(issues.selectForTask(any())).thenReturn(List.of(historicalClosedIssue()));
        when(remediations.selectForTask(any())).thenReturn(List.of(historicalProcess()));
        when(evidence.selectForTask(any())).thenReturn(List.of(proof));
        when(fileEvidence.lockAndRevalidate(any())).thenReturn(
                new FileEvidenceApi.Fact(false, "原证据版本已不可用", 2, 3, 4));

        assertThrows(InspectionException.class, () -> service.archive(command()));

        var query = ArgumentCaptor.forClass(FileEvidenceApi.Query.class);
        verify(fileEvidence).lockAndRevalidate(query.capture());
        assertEquals(new FileEvidenceApi.Query(1L, 8L, 2, "SRV", "INSPECTION_TASK", "10",
                "EVIDENCE", "issue:71:proof", EVIDENCE_SHA256), query.getValue());
        assertNull(task.getCustomerArchiveId());
        assertNull(task.getArchivedBy());
        verifyNoInteractions(customerHistory, tasks);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unconfirmedCustomerReferences")
    void absentOrWrongVersionCustomerConfirmationCannotSetArchiveMetadata(String scenario,
            CustomerInspectionHistoryApi.ArchiveReference reference) {
        var task = closedTask();
        allowClosedArchive(task);
        var report = new InspectionReportRevisionDO();
        report.setId(task.getCurrentReportId());
        when(reports.selectForTask(any())).thenReturn(List.of(report));
        when(customerHistory.register(any())).thenReturn(reference);

        assertThrows(InspectionException.class, () -> service.archive(command()));

        assertNull(task.getCustomerArchiveId());
        assertNull(task.getArchivedBy());
        assertNull(task.getArchivedAt());
        verifyNoInteractions(tasks);
    }

    private static Stream<Arguments> unconfirmedCustomerReferences() {
        return Stream.of(
                Arguments.of("CUS没有确认引用", null),
                Arguments.of("CUS引用缺少身份", new CustomerInspectionHistoryApi.ArchiveReference(null, 10L, 6L)),
                Arguments.of("CUS只确认了旧任务版本", new CustomerInspectionHistoryApi.ArchiveReference(100L, 10L, 5L)));
    }

    @Test
    void customerSourceSnapshotKeepsOldReportIssuesAndEvidenceBeforeTheArchiveTransition() {
        var task = closedTask();
        allowClosedArchive(task);
        var device = new InspectionTaskDeviceDO();
        device.setId(11L); device.setDeviceId(30L);
        var oldExecution = new InspectionExecutionDO();
        oldExecution.setId(91L); oldExecution.setStatusCode("FAILED");
        var currentExecution = new InspectionExecutionDO();
        currentExecution.setId(92L); currentExecution.setStatusCode("COMPLETE");
        var oldResult = new InspectionDetectionResultDO(); oldResult.setId(61L);
        var currentResult = new InspectionDetectionResultDO(); currentResult.setId(62L);
        var oldReport = new InspectionReportRevisionDO(); oldReport.setId(21L);
        var currentReport = new InspectionReportRevisionDO(); currentReport.setId(40L);
        var oldAnnotation = new InspectionAnnotationDO();
        oldAnnotation.setId(51L); oldAnnotation.setReportId(21L); oldAnnotation.setResultId(61L);
        var currentAnnotation = new InspectionAnnotationDO();
        currentAnnotation.setId(52L); currentAnnotation.setReportId(40L); currentAnnotation.setResultId(62L);
        var oldIssue = historicalClosedIssue();
        var currentIssue = historicalClosedIssue();
        currentIssue.setId(72L); currentIssue.setReportId(40L); currentIssue.setResultId(62L);
        var oldProcess = historicalProcess();
        var currentProcess = historicalProcess(); currentProcess.setId(82L); currentProcess.setIssueId(72L);
        var oldProof = historicalEvidence();
        var currentProof = historicalEvidence();
        currentProof.setId(56L); currentProof.setRemediationId(82L); currentProof.setArtifactId(9L);
        currentProof.setReferenceKey("issue:72:proof");
        when(devices.selectForTask(any())).thenReturn(List.of(device));
        when(executions.selectForTask(any())).thenReturn(List.of(oldExecution, currentExecution));
        when(results.selectForTask(any())).thenReturn(List.of(oldResult, currentResult));
        when(reports.selectForTask(any())).thenReturn(List.of(oldReport, currentReport));
        when(annotations.selectForTask(any())).thenReturn(List.of(oldAnnotation, currentAnnotation));
        when(issues.selectForTask(any())).thenReturn(List.of(oldIssue, currentIssue));
        when(remediations.selectForTask(any())).thenReturn(List.of(oldProcess, currentProcess));
        when(evidence.selectForTask(any())).thenReturn(List.of(oldProof, currentProof));
        when(fileEvidence.lockAndRevalidate(any())).thenReturn(new FileEvidenceApi.Fact(true, null, 2, 3, 4));
        when(customerHistory.register(any())).thenReturn(new CustomerInspectionHistoryApi.ArchiveReference(100L, 10L, 6L));

        service.archive(command());

        var source = ArgumentCaptor.forClass(CustomerInspectionHistoryApi.ArchiveCommand.class);
        var change = ArgumentCaptor.forClass(InspectionTaskService.Change.class);
        var order = inOrder(fileEvidence, customerHistory, tasks);
        order.verify(fileEvidence, times(2)).lockAndRevalidate(any());
        order.verify(customerHistory).register(source.capture());
        order.verify(tasks).transition(change.capture());
        assertEquals(6L, source.getValue().archivedTaskVersion());
        assertEquals(40L, source.getValue().reportId());
        assertEquals(10L, source.getValue().taskId());
        var expected = Set.of(
                new SourceReference(Kind.DEVICE, 30L),
                new SourceReference(Kind.EXECUTION, 91L), new SourceReference(Kind.EXECUTION, 92L),
                new SourceReference(Kind.RESULT, 61L), new SourceReference(Kind.RESULT, 62L),
                new SourceReference(Kind.REPORT, 21L), new SourceReference(Kind.REPORT, 40L),
                new SourceReference(Kind.ANNOTATION, 51L), new SourceReference(Kind.ANNOTATION, 52L),
                new SourceReference(Kind.ISSUE, 71L), new SourceReference(Kind.ISSUE, 72L),
                new SourceReference(Kind.REMEDIATION, 81L), new SourceReference(Kind.REMEDIATION, 82L),
                new SourceReference(Kind.EVIDENCE, 55L), new SourceReference(Kind.EVIDENCE, 56L));
        assertEquals(expected.size(), source.getValue().references().size());
        assertEquals(expected, Set.copyOf(source.getValue().references()));
        assertEquals(InspectionTaskStateMachine.Action.ARCHIVE, change.getValue().transition().action());
        assertTrue(change.getValue().transition().customerReferenceCreated());
        assertEquals(100L, task.getCustomerArchiveId());
    }

    private void allowClosedArchive(InspectionTaskDO task) {
        when(access.task(any())).thenReturn(task);
        when(closure.current(any())).thenReturn(new InspectionClosureFacts.Facts(true, true, 0));
    }

    private ServiceIssueDO historicalClosedIssue() {
        var issue = new ServiceIssueDO();
        issue.setId(71L); issue.setTenantId(1L); issue.setTaskId(10L); issue.setReportId(21L); issue.setResultId(61L);
        issue.setStatusCode("CLOSED"); issue.setClosedBy(7L); issue.setClosedAt(LocalDateTime.of(2026, 10, 15, 9, 0));
        return issue;
    }

    private ServiceIssueRemediationDO historicalProcess() {
        var process = new ServiceIssueRemediationDO();
        process.setId(81L); process.setTenantId(1L); process.setTaskId(10L); process.setIssueId(71L);
        process.setActionCode("PROCESS"); process.setContent("已完成设备问题处理并保留证据");
        return process;
    }

    private InspectionEvidenceDO historicalEvidence() {
        var proof = new InspectionEvidenceDO();
        proof.setId(55L); proof.setTenantId(1L); proof.setTaskId(10L); proof.setRemediationId(81L);
        proof.setArtifactId(8L); proof.setFileVersionNo(2); proof.setReferenceKey("issue:71:proof"); proof.setSha256(EVIDENCE_SHA256);
        return proof;
    }

    private InspectionArchiveService.Command command() {
        return new InspectionArchiveService.Command(InspectionRequestContext.builder().tenantId(1L).actorId(7L)
                .expectedVersion(5L).idempotencyKey("test").correlationId("test").build(), 10L, "确认资料齐全");
    }
    private InspectionTaskDO closedTask() {
        var task = new InspectionTaskDO(); task.setId(10L); task.setTenantId(1L); task.setVersion(5L); task.setStatusCode("CLOSED");
        task.setOwnerUserId(7L); task.setProjectId(20L); task.setCustomerId(30L); task.setCustomerCode("C30");
        task.setCurrentReportId(40L); task.setTaskCode("INS-10"); task.setTaskName("巡检");
        task.setArchiveRequestedAt(LocalDateTime.now()); task.setArchiveRequestedBy(7L); return task;
    }
}
