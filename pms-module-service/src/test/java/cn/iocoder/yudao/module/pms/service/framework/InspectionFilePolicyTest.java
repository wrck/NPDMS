package cn.iocoder.yudao.module.pms.service.framework;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectReferenceSetQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectReferenceSetRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetKey;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FrozenFileVersionAccessQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.NativeGeneratedFilePolicyQuery;
import cn.iocoder.yudao.module.pms.project.api.reference.ProjectBusinessContextApi;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.InspectionEvidenceDO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.InspectionTaskDO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.ServiceIssueDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.InspectionEvidenceMapper;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.InspectionExecutionMapper;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.InspectionReportFileMapper;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.InspectionTaskMapper;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.ServiceIssueMapper;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.InspectionTaskRows;
import cn.iocoder.yudao.module.pms.service.service.inspection.InspectionAccessService;
import cn.iocoder.yudao.module.pms.service.service.inspection.InspectionException;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/** Checks owner-policy decisions with different snapshot/current rows; this is not a database isolation test. */
class InspectionFilePolicyTest {
    private static final Long TENANT_ID = 1L;
    private static final Long ACTOR_ID = 7L;
    private static final Long TASK_ID = 10L;
    private static final Long SCOPE_VERSION = 87L;
    private static final String REFERENCE_KEY = "issue:30:evidence";
    private static final InspectionTaskRows TASK_ROWS = new InspectionTaskRows(TENANT_ID, TASK_ID);

    private final InspectionTaskMapper tasks = mock(InspectionTaskMapper.class);
    private final InspectionReportFileMapper reportFiles = mock(InspectionReportFileMapper.class);
    private final InspectionExecutionMapper executions = mock(InspectionExecutionMapper.class);
    private final InspectionEvidenceMapper evidence = mock(InspectionEvidenceMapper.class);
    private final ServiceIssueMapper issues = mock(ServiceIssueMapper.class);
    private final InspectionAccessService access = mock(InspectionAccessService.class);
    private final PermissionApi permissions = mock(PermissionApi.class);
    private final InspectionFilePolicy policy = new InspectionFilePolicy(
            tasks, reportFiles, executions, evidence, issues, access, permissions);
    private InspectionTaskDO task;

    @BeforeEach
    void setUpTenantAndProjectScope() {
        TenantContextHolder.setTenantId(TENANT_ID);
        task = new InspectionTaskDO();
        task.setId(TASK_ID); task.setTenantId(TENANT_ID); task.setProjectId(20L);
        task.setVersion(5L); task.setOwnerUserId(99L); task.setStatusCode("TRACKING");
        when(tasks.selectById(TASK_ID)).thenReturn(task);
        when(tasks.selectForUpdate(TASK_ROWS)).thenReturn(task);
        // Project version is deliberately different from scopeVersion; only scopeVersion authorizes the file scope.
        when(access.project(any())).thenReturn(ProjectBusinessContextApi.Context.builder()
                .tenantId(TENANT_ID).projectId(20L).projectVersion(41L).scopeVersion(SCOPE_VERSION)
                .projectCode("P20").projectName("巡检项目").customerId(50L).customerCode("C50")
                .customerName("客户").departmentId(60L).departmentCode("D60").departmentName("区域").build());
        when(permissions.hasAnyPermissions(ACTOR_ID, "pms:service-issue:remediate")).thenReturn(true);
        when(permissions.hasAnyPermissions(ACTOR_ID, "pms:service-issue:query")).thenReturn(true);
    }

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    @Test
    void reassignmentAfterTaskLockRevokesAnOldSnapshotUploadGrant() {
        var snapshot = issue();
        var current = issue(); current.setResponsibleUserId(8L);
        when(issues.selectById(30L)).thenReturn(snapshot);
        when(issues.selectForTaskForUpdate(TASK_ROWS)).thenReturn(List.of(current));
        var query = scope(UPLOAD);

        assertTrue(policy.inspect(query).allowed());
        assertFalse(policy.lockAndRevalidate(revalidation(query)).allowed());

        verify(issues, times(1)).selectById(30L);
        var order = inOrder(tasks, issues);
        order.verify(tasks).selectForUpdate(TASK_ROWS);
        order.verify(issues).selectForTaskForUpdate(TASK_ROWS);
    }

    @Test
    void assignmentToTheActorAfterTaskLockAllowsOpenIssueEvidenceUpload() {
        var snapshot = issue(); snapshot.setResponsibleUserId(8L);
        var current = issue();
        when(issues.selectById(30L)).thenReturn(snapshot);
        when(issues.selectForTaskForUpdate(TASK_ROWS)).thenReturn(List.of(current));
        var query = scope(UPLOAD);

        assertFalse(policy.inspect(query).allowed());
        var fact = policy.lockAndRevalidate(revalidation(query));

        assertTrue(fact.allowed());
        assertEquals(SCOPE_VERSION, fact.scopeVersion());
        assertEquals("MUTABLE", fact.referenceMutability());
        verify(permissions).hasAnyPermissions(ACTOR_ID, "pms:service-issue:remediate");
        verify(issues, times(1)).selectById(30L);
        var order = inOrder(tasks, issues);
        order.verify(tasks).selectForUpdate(TASK_ROWS);
        order.verify(issues).selectForTaskForUpdate(TASK_ROWS);
    }

    @Test
    void reassignmentAlsoRevokesFrozenContentAccessUnderTheTaskLock() {
        var snapshot = issue();
        var current = issue(); current.setResponsibleUserId(8L);
        when(issues.selectById(30L)).thenReturn(snapshot);
        when(issues.selectForTaskForUpdate(TASK_ROWS)).thenReturn(List.of(current));
        when(evidence.selectForTask(TASK_ROWS)).thenReturn(List.of(frozenEvidence()));
        var query = frozenVersion(40L, 2);

        assertTrue(policy.inspectFrozenFileVersion(query).allowed());
        assertFalse(policy.lockAndRevalidateFrozenFileVersion(query).allowed());

        verify(issues, times(1)).selectById(30L);
        var order = inOrder(tasks, issues);
        order.verify(tasks).selectForUpdate(TASK_ROWS);
        order.verify(issues).selectForTaskForUpdate(TASK_ROWS);
    }

    @Test
    void anAlreadyClosedIssueCannotAcceptMoreEvidence() {
        task.setStatusCode("CLOSED");
        var closed = closedIssue();
        when(issues.selectForTaskForUpdate(TASK_ROWS)).thenReturn(List.of(closed));

        assertFalse(policy.lockAndRevalidate(revalidation(scope(UPLOAD))).allowed());
    }

    @Test
    void archivedTaskCannotAcceptEvidenceEvenForItsOwner() {
        task.setStatusCode("ARCHIVED"); task.setOwnerUserId(ACTOR_ID);

        assertFalse(policy.lockAndRevalidate(revalidation(scope(UPLOAD))).allowed());

        verifyNoInteractions(issues);
    }

    @Test
    void cancelledTaskStillAllowsTheCurrentResponsibleToEvidenceAnOpenIssue() {
        task.setStatusCode("CANCELLED");
        when(issues.selectForTaskForUpdate(TASK_ROWS)).thenReturn(List.of(issue()));

        assertTrue(policy.lockAndRevalidate(revalidation(scope(UPLOAD))).allowed());
        verify(permissions).hasAnyPermissions(ACTOR_ID, "pms:service-issue:remediate");
    }

    @Test
    void cancellationDoesNotReopenClosedIssueEvidenceUploads() {
        task.setStatusCode("CANCELLED");
        var closed = closedIssue();
        when(issues.selectForTaskForUpdate(TASK_ROWS)).thenReturn(List.of(closed));

        assertFalse(policy.lockAndRevalidate(revalidation(scope(UPLOAD))).allowed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"REPORT", "LOG"})
    void cancelledTaskCannotReceiveReportsOrNativeCollectionLogs(String purpose) {
        task.setStatusCode("CANCELLED"); task.setOwnerUserId(ACTOR_ID);
        when(permissions.hasAnyPermissions(ACTOR_ID, "pms:inspection-report:generate")).thenReturn(true);
        when(permissions.hasAnyPermissions(ACTOR_ID, "pms:inspection-task:execute")).thenReturn(true);

        var fact = policy.lockAndRevalidateNativeGeneratedFile(new NativeGeneratedFilePolicyQuery(
                TENANT_ID, ACTOR_ID, "SRV", "INSPECTION_TASK", TASK_ID, 5L, purpose, SCOPE_VERSION));

        assertFalse(fact.allowed());
    }

    @Test
    void tenantContextCannotBeReplacedByAQueryTenant() {
        var query = new FileBusinessObjectPolicyQuery(2L, ACTOR_ID, "SRV", "INSPECTION_TASK",
                "10", "EVIDENCE", REFERENCE_KEY, UPLOAD);

        assertFalse(policy.inspect(query).allowed());

        verifyNoInteractions(tasks, access, issues);
    }

    @Test
    void aTaskFromAnotherTenantCannotAuthorizeFiles() {
        task.setTenantId(2L);

        assertFalse(policy.inspect(scope(UPLOAD)).allowed());

        verifyNoInteractions(access, issues);
    }

    @ParameterizedTest(name = "issue tenant={0}, task={1}")
    @CsvSource({"2, 10", "1, 11"})
    void issueIdentityMustBelongToBothTheTaskAndTenant(long tenantId, long taskId) {
        var unrelated = issue(); unrelated.setTenantId(tenantId); unrelated.setTaskId(taskId);
        when(issues.selectById(30L)).thenReturn(unrelated);

        assertFalse(policy.inspect(scope(UPLOAD)).allowed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"issue:31:evidence", "issue:invalid:evidence"})
    void referenceCannotBorrowAuthorizationFromAnotherIssue(String referenceKey) {
        when(issues.selectForTaskForUpdate(TASK_ROWS)).thenReturn(List.of(issue()));
        var query = new FileBusinessObjectPolicyQuery(TENANT_ID, ACTOR_ID, "SRV", "INSPECTION_TASK",
                "10", "EVIDENCE", referenceKey, UPLOAD);

        assertFalse(policy.lockAndRevalidate(revalidation(query)).allowed());

        verify(issues, never()).selectById(anyLong());
    }

    @Test
    void aDifferentTaskObjectCannotFallBackToTheKnownTask() {
        var query = new FileBusinessObjectPolicyQuery(TENANT_ID, ACTOR_ID, "SRV", "INSPECTION_TASK",
                "11", "EVIDENCE", REFERENCE_KEY, UPLOAD);

        assertFalse(policy.inspect(query).allowed());

        verify(tasks).selectById(11L);
        verifyNoInteractions(access, issues);
    }

    @Test
    void responsibleAssignmentDoesNotReplaceTheRemediationPermission() {
        when(issues.selectForTaskForUpdate(TASK_ROWS)).thenReturn(List.of(issue()));
        when(permissions.hasAnyPermissions(ACTOR_ID, "pms:service-issue:remediate")).thenReturn(false);

        assertFalse(policy.lockAndRevalidate(revalidation(scope(UPLOAD))).allowed());
    }

    @Test
    void responsibleAssignmentDoesNotReplaceProjectScopeAuthorization() {
        var denied = new InspectionException(InspectionException.Reason.FORBIDDEN, "项目范围已收回");
        when(access.project(any())).thenThrow(denied);

        assertFalse(policy.lockAndRevalidate(revalidation(scope(UPLOAD))).allowed());

        verifyNoInteractions(issues);
        verify(tasks, never()).selectForUpdate(any());
    }

    @Test
    void reportReadReturnsDeniedWhenTheOwnersProjectScopeHasBeenRevoked() {
        task.setOwnerUserId(ACTOR_ID);
        when(permissions.hasAnyPermissions(ACTOR_ID, "pms:inspection-task:query")).thenReturn(true);
        when(access.project(any())).thenThrow(
                new InspectionException(InspectionException.Reason.FORBIDDEN, "项目范围已收回"));
        var query = new FileBusinessObjectPolicyQuery(TENANT_ID, ACTOR_ID, "SRV", "INSPECTION_TASK",
                "10", "REPORT", "report:3:pdf", READ);

        assertFalse(policy.inspect(query).allowed());

        verifyNoInteractions(issues, reportFiles);
        verify(tasks, never()).selectForUpdate(any());
    }

    @Test
    void lockedManagerScopeRevocationReturnsDeniedBeforeTaskLockOrIssueQueries() {
        when(access.manager(any())).thenThrow(
                new InspectionException(InspectionException.Reason.FORBIDDEN, "服务经理项目范围已收回"));

        assertFalse(policy.lockAndRevalidate(revalidation(scope(UPLOAD))).allowed());

        var order = inOrder(access);
        order.verify(access).project(argThat(InspectionAccessService.ProjectAccess::lock));
        order.verify(access).manager(argThat(InspectionAccessService.ProjectAccess::lock));
        verifyNoInteractions(issues);
        verify(tasks, never()).selectForUpdate(any());
    }

    @Test
    void projectDependencyFailurePropagatesWithoutBecomingPermissionDenial() {
        var unavailable = new InspectionException(InspectionException.Reason.DEPENDENCY_UNAVAILABLE,
                "项目范围依赖不可用");
        when(access.project(any())).thenThrow(unavailable);

        assertSame(unavailable, assertThrows(InspectionException.class, () -> policy.inspect(scope(READ))));

        verifyNoInteractions(issues);
        verify(tasks, never()).selectForUpdate(any());
    }

    @Test
    void unexpectedManagerFailurePropagatesWithoutBecomingPermissionDenial() {
        var failure = new IllegalStateException("项目范围查询失败");
        when(access.manager(any())).thenThrow(failure);

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> policy.lockAndRevalidate(revalidation(scope(UPLOAD)))));

        verifyNoInteractions(issues);
        verify(tasks, never()).selectForUpdate(any());
    }

    @Test
    void anOutdatedProjectScopeVersionCannotAuthorizeAnUpload() {
        var query = scope(UPLOAD);
        var revalidation = new FileBusinessObjectPolicyRevalidationQuery(query.tenantId(), query.actorUserId(),
                query.ownerContext(), query.objectType(), query.objectId(), query.purposeCode(), query.referenceKey(),
                query.requiredAction(), 88L);

        assertFalse(policy.lockAndRevalidate(revalidation).allowed());

        verifyNoInteractions(issues);
        verify(tasks, never()).selectForUpdate(any());
    }

    @Test
    void archivedEvidenceKeepsItsExactFrozenVersionReadableByItsResponsible() {
        task.setStatusCode("ARCHIVED");
        var closed = closedIssue();
        when(issues.selectForTaskForUpdate(TASK_ROWS)).thenReturn(List.of(closed));
        when(evidence.selectForTask(TASK_ROWS)).thenReturn(List.of(frozenEvidence()));

        var fact = policy.lockAndRevalidateFrozenFileVersion(frozenVersion(40L, 2));

        assertTrue(fact.allowed());
        assertEquals(SCOPE_VERSION, fact.scopeVersion());
        assertEquals("IMMUTABLE", fact.referenceMutability());
        verify(permissions).hasAnyPermissions(ACTOR_ID, "pms:service-issue:query");
    }

    @ParameterizedTest(name = "artifact={0}, version={1}")
    @CsvSource({"40, 3", "41, 2"})
    void contentAccessCannotReplaceTheFrozenArtifactOrVersion(long artifactId, int versionNo) {
        when(issues.selectById(30L)).thenReturn(issue());
        when(evidence.selectForTask(TASK_ROWS)).thenReturn(List.of(frozenEvidence()));

        assertTrue(policy.inspectFrozenFileVersion(frozenVersion(40L, 2)).allowed());
        assertFalse(policy.inspectFrozenFileVersion(frozenVersion(artifactId, versionNo)).allowed());
    }

    @ParameterizedTest
    @ValueSource(strings = {UPLOAD, REFERENCE, REPLACE, DETACH})
    void anExistingFrozenReferenceCannotBeReplacedOrDetached(String action) {
        when(issues.selectForTaskForUpdate(TASK_ROWS)).thenReturn(List.of(issue()));
        when(evidence.selectForTask(TASK_ROWS)).thenReturn(List.of(frozenEvidence()));

        assertFalse(policy.lockAndRevalidate(revalidation(scope(action))).allowed());
    }

    @Test
    void currentResponsibleCanPassGroupUploadBeforeTheirExactEvidenceReferenceWithoutBeingOwnerOrManager() {
        when(issues.selectForTask(TASK_ROWS)).thenReturn(List.of(issue()));
        when(issues.selectForTaskForUpdate(TASK_ROWS)).thenReturn(List.of(issue()));
        var group = referenceSet(UPLOAD);

        assertTrue(policy.inspectReferenceSet(group).allowed());
        var groupFact = policy.lockAndRevalidateReferenceSet(revalidation(group));
        assertTrue(groupFact.allowed());
        assertEquals(SCOPE_VERSION, groupFact.scopeVersion());
        assertTrue(policy.lockAndRevalidate(revalidation(scope(UPLOAD))).allowed());
    }

    @Test
    void groupUploadGrantCannotBypassAnotherIssueFrozenReferenceOrSpoofedGroupKey() {
        var other = issue(); other.setId(31L); other.setResponsibleUserId(8L);
        when(issues.selectForTaskForUpdate(TASK_ROWS)).thenReturn(List.of(issue(), other));
        when(evidence.selectForTask(TASK_ROWS)).thenReturn(List.of(frozenEvidence()));
        var otherReference = new FileBusinessObjectPolicyQuery(TENANT_ID, ACTOR_ID, "SRV", "INSPECTION_TASK",
                "10", "EVIDENCE", "issue:31:evidence", UPLOAD);
        var spoofedGroupKey = new FileBusinessObjectPolicyQuery(TENANT_ID, ACTOR_ID, "SRV", "INSPECTION_TASK",
                "10", "EVIDENCE", "reference-set", UPLOAD);

        assertTrue(policy.lockAndRevalidateReferenceSet(revalidation(referenceSet(UPLOAD))).allowed());
        assertFalse(policy.lockAndRevalidate(revalidation(otherReference)).allowed());
        assertFalse(policy.lockAndRevalidate(revalidation(scope(UPLOAD))).allowed());
        assertFalse(policy.inspect(spoofedGroupKey).allowed());
        assertFalse(policy.lockAndRevalidate(revalidation(spoofedGroupKey)).allowed());
    }

    @Test
    void groupUploadRechecksCurrentAssignmentAfterTaskLockInsteadOfTrustingTheSnapshot() {
        var reassigned = issue(); reassigned.setResponsibleUserId(8L);
        when(issues.selectForTask(TASK_ROWS)).thenReturn(List.of(issue()));
        when(issues.selectForTaskForUpdate(TASK_ROWS)).thenReturn(List.of(reassigned));
        var group = referenceSet(UPLOAD);

        assertTrue(policy.inspectReferenceSet(group).allowed());
        assertFalse(policy.lockAndRevalidateReferenceSet(revalidation(group)).allowed());

        verify(issues).selectForTask(TASK_ROWS);
        verify(issues, never()).selectById(anyLong());
        var order = inOrder(tasks, issues);
        order.verify(tasks).selectForUpdate(TASK_ROWS);
        order.verify(issues).selectForTaskForUpdate(TASK_ROWS);
    }

    @ParameterizedTest(name = "responsible={0}, status={1}, remediate={2}")
    @CsvSource({"8, PROCESSING, true", "7, CLOSED, true", "7, PROCESSING, false"})
    void groupUploadRequiresACurrentOpenIssueAndRemediationPermission(long responsibleId, String status,
                                                                     boolean permitted) {
        var current = issue(); current.setResponsibleUserId(responsibleId); current.setStatusCode(status);
        when(issues.selectForTaskForUpdate(TASK_ROWS)).thenReturn(List.of(current));
        when(permissions.hasAnyPermissions(ACTOR_ID, "pms:service-issue:remediate")).thenReturn(permitted);

        assertFalse(policy.lockAndRevalidateReferenceSet(revalidation(referenceSet(UPLOAD))).allowed());
    }

    @Test
    void responsibleAccessToOneExactReferenceDoesNotAllowReadingTheWholeEvidenceGroup() {
        when(issues.selectById(30L)).thenReturn(issue());
        when(issues.selectForTask(TASK_ROWS)).thenReturn(List.of(issue()));
        when(issues.selectForTaskForUpdate(TASK_ROWS)).thenReturn(List.of(issue()));

        assertTrue(policy.inspect(scope(READ)).allowed());
        assertFalse(policy.inspectReferenceSet(referenceSet(READ)).allowed());
        assertFalse(policy.lockAndRevalidateReferenceSet(revalidation(referenceSet(READ))).allowed());
    }

    @ParameterizedTest(name = "task={0}, issue={1}, allowed={2}")
    @CsvSource({"ARCHIVED, PROCESSING, false", "CANCELLED, PROCESSING, true", "CANCELLED, CLOSED, false"})
    void groupUploadPreservesArchivedAndCancelledTaskRestrictions(String taskStatus, String issueStatus,
                                                                 boolean allowed) {
        task.setOwnerUserId(ACTOR_ID); task.setStatusCode(taskStatus);
        var current = issue(); current.setStatusCode(issueStatus);
        when(issues.selectForTaskForUpdate(TASK_ROWS)).thenReturn(List.of(current));

        assertEquals(allowed, policy.lockAndRevalidateReferenceSet(revalidation(referenceSet(UPLOAD))).allowed());
        if ("ARCHIVED".equals(taskStatus)) verifyNoInteractions(issues);
    }

    @ParameterizedTest(name = "issue tenant={0}, task={1}")
    @CsvSource({"2, 10", "1, 11"})
    void groupUploadCannotBorrowAResponsibleIssueFromAnotherTenantOrTask(long tenantId, long taskId) {
        var unrelated = issue(); unrelated.setTenantId(tenantId); unrelated.setTaskId(taskId);
        when(issues.selectForTaskForUpdate(TASK_ROWS)).thenReturn(List.of(unrelated));

        assertFalse(policy.lockAndRevalidateReferenceSet(revalidation(referenceSet(UPLOAD))).allowed());
    }

    @Test
    void groupUploadCannotReplaceTheTenantContextOrUseAnOutdatedProjectScope() {
        var group = referenceSet(UPLOAD);
        var otherTenant = new FileBusinessObjectReferenceSetQuery(2L, ACTOR_ID, group.key(), UPLOAD);

        assertFalse(policy.inspectReferenceSet(otherTenant).allowed());
        assertFalse(policy.lockAndRevalidateReferenceSet(revalidation(otherTenant)).allowed());
        verifyNoInteractions(tasks, access, issues);

        var outdated = new FileBusinessObjectReferenceSetRevalidationQuery(TENANT_ID, ACTOR_ID,
                group.key(), UPLOAD, SCOPE_VERSION + 1);
        assertFalse(policy.lockAndRevalidateReferenceSet(outdated).allowed());
        verifyNoInteractions(issues);
        verify(tasks, never()).selectForUpdate(any());
    }

    private FileBusinessObjectReferenceSetQuery referenceSet(String action) {
        var query = scope(action);
        return new FileBusinessObjectReferenceSetQuery(query.tenantId(), query.actorUserId(),
                new FileReferenceSetKey(query.ownerContext(), query.objectType(), query.objectId(), query.purposeCode()),
                query.requiredAction());
    }

    private FileBusinessObjectReferenceSetRevalidationQuery revalidation(FileBusinessObjectReferenceSetQuery query) {
        return new FileBusinessObjectReferenceSetRevalidationQuery(query.tenantId(), query.actorUserId(), query.key(),
                query.requiredAction(), SCOPE_VERSION);
    }

    private FileBusinessObjectPolicyQuery scope(String action) {
        return new FileBusinessObjectPolicyQuery(TENANT_ID, ACTOR_ID, "SRV", "INSPECTION_TASK",
                "10", "EVIDENCE", REFERENCE_KEY, action);
    }

    private FileBusinessObjectPolicyRevalidationQuery revalidation(FileBusinessObjectPolicyQuery query) {
        return new FileBusinessObjectPolicyRevalidationQuery(query.tenantId(), query.actorUserId(), query.ownerContext(),
                query.objectType(), query.objectId(), query.purposeCode(), query.referenceKey(), query.requiredAction(), SCOPE_VERSION);
    }

    private FrozenFileVersionAccessQuery frozenVersion(Long artifactId, Integer versionNo) {
        return new FrozenFileVersionAccessQuery(scope(DOWNLOAD), artifactId, versionNo, SCOPE_VERSION);
    }

    private ServiceIssueDO issue() {
        var issue = new ServiceIssueDO();
        issue.setId(30L); issue.setTenantId(TENANT_ID); issue.setTaskId(TASK_ID);
        issue.setResponsibleUserId(ACTOR_ID); issue.setStatusCode("PROCESSING");
        return issue;
    }

    private InspectionEvidenceDO frozenEvidence() {
        var row = new InspectionEvidenceDO();
        row.setId(90L); row.setTenantId(TENANT_ID); row.setTaskId(TASK_ID);
        row.setRemediationId(81L);
        row.setArtifactId(40L); row.setFileVersionNo(2); row.setReferenceKey(REFERENCE_KEY);
        return row;
    }

    private ServiceIssueDO closedIssue() {
        var closed = issue();
        closed.setStatusCode("CLOSED"); closed.setClosedBy(ACTOR_ID);
        closed.setClosedAt(LocalDateTime.of(2026, 10, 15, 9, 0));
        return closed;
    }
}
