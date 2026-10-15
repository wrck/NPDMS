package cn.iocoder.yudao.module.pms.service.service.inspection;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.collection.CollectionSourceAdapter;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.InspectionAttemptDO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.InspectionExecutionDO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.InspectionTaskDO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.InspectionTaskDeviceDO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.InspectionCollectionReceiptDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.InspectionAttemptMapper;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.InspectionExecutionMapper;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.InspectionTaskDeviceMapper;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.InspectionTaskRuleSnapshotMapper;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.InspectionCollectionReceiptMapper;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.InspectionTaskRows;
import cn.iocoder.yudao.module.pms.service.service.inspection.InspectionOnlineAuthorization.Connection;
import cn.iocoder.yudao.module.pms.service.service.inspection.InspectionOnlineAuthorization.Precheck;
import cn.iocoder.yudao.module.pms.service.service.inspectionrule.InspectionRuleQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InspectionOnlineAuthorizationTest {
    private static final LocalDateTime CHECKED_AT = LocalDateTime.of(2026, 10, 15, 8, 0);
    private static final LocalDateTime EXPIRES_AT = CHECKED_AT.plusMinutes(15);
    private final InspectionAccessService access = mock(InspectionAccessService.class);
    private final InspectionExecutionMapper executions = mock(InspectionExecutionMapper.class);
    private final InspectionAttemptMapper attempts = mock(InspectionAttemptMapper.class);
    private final InspectionTaskDeviceMapper devices = mock(InspectionTaskDeviceMapper.class);
    private final InspectionTaskRuleSnapshotMapper snapshots = mock(InspectionTaskRuleSnapshotMapper.class);
    private final InspectionRuleQueryService rules = mock(InspectionRuleQueryService.class);
    private final InspectionCollectionReceiptMapper receipts = mock(InspectionCollectionReceiptMapper.class);
    private InspectionOnlineAuthorization authorization;
    private InspectionTaskDO task;
    private InspectionAttemptDO attempt;
    private InspectionExecutionDO execution;
    private InspectionTaskDeviceDO device;

    @BeforeEach
    void setup() {
        TenantContextHolder.setTenantId(1L);
        authorization = new InspectionOnlineAuthorization(access, executions, attempts, devices, snapshots, rules, receipts);
        task = new InspectionTaskDO();
        task.setId(10L); task.setTenantId(1L); task.setVersion(5L); task.setProjectId(100L);
        task.setMode("ONLINE"); task.setStatusCode("PRECHECK_PENDING"); task.setCurrentAttemptId(20L);
        attempt = new InspectionAttemptDO();
        attempt.setId(20L); attempt.setTenantId(1L); attempt.setTaskId(10L);
        attempt.setMode("ONLINE"); attempt.setStatusCode("ACTIVE");
        execution = sourceExecution();
        device = new InspectionTaskDeviceDO();
        device.setId(60L); device.setTenantId(1L); device.setTaskId(10L); device.setDeviceId(30L);
        when(access.task(any(InspectionAccessService.TaskAccess.class))).thenReturn(task);
        when(executions.selectById(50L)).thenAnswer(call -> execution);
        when(attempts.selectById(20L)).thenReturn(attempt);
        when(devices.selectForTask(any(InspectionTaskRows.class))).thenReturn(List.of(device));
        when(executions.updateById(any(InspectionExecutionDO.class))).thenReturn(1);
    }

    @AfterEach
    void clearTenant() { TenantContextHolder.clear(); }

    @Test
    void newReceiptVersionInvalidatesPrecheckWithoutRewritingItsPassedHistory() {
        var previous = activeCollect();
        var later = new InspectionCollectionReceiptDO(); later.setResultVersion(2L);
        when(receipts.selectForExecutionForUpdate(any())).thenReturn(List.of(later));
        var error = assertThrows(InspectionException.class, () -> authorization.authorize(executeQuery(3L)));
        assertEquals(InspectionException.Reason.PRECHECK_REQUIRED, error.getReason());
        assertEquals("PASSED", previous.getStatusCode()); assertEquals(1, previous.getCollectionResultVersion());
        verify(executions, never()).updateById(any(InspectionExecutionDO.class));
        when(receipts.selectForExecutionForUpdate(any())).thenReturn(List.of());
        assertThrows(InspectionException.class, () -> authorization.authorize(executeQuery(3L)));
    }

    @Test
    void fifteenMinutePrecheckIsValidJustBeforeExpiryAndRejectedAtAndAfterExpiry() {
        var collect = collectExecution();
        var previous = passedPrecheck();
        var actual = InspectionOnlineAuthorization.connection(previous);
        assertEquals(CHECKED_AT.plusMinutes(15), previous.getPrecheckExpiresAt());
        assertDoesNotThrow(() -> InspectionOnlineAuthorization.verifyPrecheck(
                new Precheck(collect, previous, actual, EXPIRES_AT.minusNanos(1))));
        precheckRejected(new Precheck(collect, previous, actual, EXPIRES_AT));
        precheckRejected(new Precheck(collect, previous, actual, EXPIRES_AT.plusNanos(1)));
    }

    private record ContextChange(String name, Consumer<InspectionExecutionDO> edit) { }

    @Test
    void aPassedPrecheckCannotBeReusedAcrossTenantTaskAttemptDeviceActorOrFrozenRules() {
        var changes = List.of(
                new ContextChange("租户", row -> row.setTenantId(2L)),
                new ContextChange("任务", row -> row.setTaskId(11L)),
                new ContextChange("尝试", row -> row.setAttemptId(21L)),
                new ContextChange("设备", row -> row.setDeviceId(31L)),
                new ContextChange("原提交人", row -> row.setSubmittedBy(41L)),
                new ContextChange("冻结规则", row -> row.setRuleDigest("different-rule-digest")));
        for (var change : changes) {
            var previous = passedPrecheck();
            change.edit().accept(previous);
            var error = assertThrows(InspectionException.class, () -> InspectionOnlineAuthorization.verifyPrecheck(
                    new Precheck(collectExecution(), previous, InspectionOnlineAuthorization.connection(previous), CHECKED_AT)), change.name());
            assertEquals(InspectionException.Reason.PRECHECK_REQUIRED, error.getReason());
        }
    }

    @Test
    void actualConnectionMustMatchEveryFrozenFieldEvenWhenPrecheckHasNotExpired() {
        var previous = passedPrecheck();
        var changes = List.of(
                new Connection("other.example", 22, "SSH", "operator", 70L, 4L, 80L),
                new Connection("device.example", 23, "SSH", "operator", 70L, 4L, 80L),
                new Connection("device.example", 22, "TELNET", "operator", 70L, 4L, 80L),
                new Connection("device.example", 22, "SSH", "other-user", 70L, 4L, 80L),
                new Connection("device.example", 22, "SSH", "operator", 71L, 4L, 80L),
                new Connection("device.example", 22, "SSH", "operator", 70L, 5L, 80L),
                new Connection("device.example", 22, "SSH", "operator", 70L, 4L, 81L));
        for (var actual : changes) precheckRejected(new Precheck(collectExecution(), previous, actual, CHECKED_AT));
        assertDoesNotThrow(() -> InspectionOnlineAuthorization.verifyPrecheck(new Precheck(collectExecution(), previous,
                new Connection(" DEVICE.EXAMPLE ", 22, "SSH", " operator ", 70L, 4L, 80L), CHECKED_AT)));
    }

    @Test
    void failedIncompleteAndUnfrozenPrecheckFactsDoNotAuthorizeCollection() {
        var changes = List.of(
                new ContextChange("失败预检", row -> row.setStatusCode("FAILED")),
                new ContextChange("普通采集", row -> row.setPurposeCode("COLLECT")),
                new ContextChange("未冻结连接", row -> row.setCredentialSnapshotRecorded(false)),
                new ContextChange("没有有效期", row -> row.setPrecheckExpiresAt(null)));
        for (var change : changes) {
            var previous = passedPrecheck();
            change.edit().accept(previous);
            precheckRejected(new Precheck(collectExecution(), previous, null, CHECKED_AT));
        }
        precheckRejected(new Precheck(collectExecution(), null, null, CHECKED_AT));
    }

    @Test
    void executeRequiresLockedCurrentTaskAndTheSourceRowVersionRatherThanTaskVersion() {
        var result = authorization.authorize(executeQuery(3L));
        assertSame(task, result.task());
        assertSame(execution, result.execution());
        assertSame(device, result.device());
        var checked = ArgumentCaptor.forClass(InspectionAccessService.TaskAccess.class);
        verify(access, times(2)).task(checked.capture());
        assertFalse(checked.getAllValues().getFirst().write());
        var locked = checked.getAllValues().getLast();
        assertTrue(locked.write());
        assertEquals("pms:inspection-task:execute", locked.permission());
        assertEquals(5L, locked.context().expectedVersion());
        assertEquals(40L, locked.context().actorId());
        assertThrows(InspectionException.class, () -> authorization.authorize(executeQuery(2L)));
    }

    @Test
    void terminalAndWrongPhaseTasksAndHistoricalAttemptsRejectNewExecution() {
        for (String status : List.of("ARCHIVED", "CANCELLED", "PREPARING", "REPORT_PENDING", "ANNOTATION_PENDING", "TRACKING")) {
            task.setStatusCode(status);
            assertThrows(InspectionException.class, () -> authorization.authorize(executeQuery(3L)), status);
        }
        task.setStatusCode("PRECHECK_PENDING");
        task.setCurrentAttemptId(21L);
        assertThrows(InspectionException.class, () -> authorization.authorize(executeQuery(3L)));
    }

    @Test
    void offlineCompletedForeignOrMissingAttemptsRejectOnlineExecution() {
        attempt.setMode("OFFLINE");
        assertThrows(InspectionException.class, () -> authorization.authorize(executeQuery(3L)));
        attempt.setMode("ONLINE");
        attempt.setStatusCode("COMPLETED");
        assertThrows(InspectionException.class, () -> authorization.authorize(executeQuery(3L)));
        attempt.setStatusCode("ACTIVE");
        attempt.setTenantId(2L);
        assertThrows(InspectionException.class, () -> authorization.authorize(executeQuery(3L)));
        when(attempts.selectById(20L)).thenReturn(null);
        assertThrows(InspectionException.class, () -> authorization.authorize(executeQuery(3L)));
    }

    @Test
    void anotherSubmitterAndAlreadyDispatchedOrFrozenSourceRowsCannotBeReused() {
        var changes = List.of(
                new ContextChange("提交人改变", row -> row.setSubmittedBy(41L)),
                new ContextChange("非待执行状态", row -> row.setStatusCode("DISPATCHED")),
                new ContextChange("已有平台绑定", row -> row.setCollectionExecutionId(900L)),
                new ContextChange("连接已冻结", row -> row.setCredentialSnapshotRecorded(true)));
        for (var change : changes) {
            execution = sourceExecution();
            change.edit().accept(execution);
            assertThrows(InspectionException.class, () -> authorization.authorize(executeQuery(3L)), change.name());
        }
    }

    @Test
    void queryCannotSubstituteTenantOrDeviceAndRemovedProjectDevicesAreRejected() {
        var foreignTenant = CollectionSourceAdapter.AuthorizationQuery.builder().tenantId(2L).actorId(40L)
                .objectId(50L).deviceId(30L).access(CollectionSourceAdapter.Access.EXECUTE).expectedVersion(3L).build();
        assertThrows(InspectionException.class, () -> authorization.authorize(foreignTenant));
        var otherDevice = CollectionSourceAdapter.AuthorizationQuery.builder().tenantId(1L).actorId(40L)
                .objectId(50L).deviceId(31L).access(CollectionSourceAdapter.Access.EXECUTE).expectedVersion(3L).build();
        assertThrows(InspectionException.class, () -> authorization.authorize(otherDevice));
        when(devices.selectForTask(any(InspectionTaskRows.class))).thenReturn(List.of());
        assertThrows(InspectionException.class, () -> authorization.authorize(executeQuery(3L)));
    }

    @Test
    void collectNeedsExecutingPhaseAndItsStillValidPrecheck() {
        var previous = activeCollect();
        task.setStatusCode("PRECHECK_PENDING");
        assertThrows(InspectionException.class, () -> authorization.authorize(executeQuery(3L)));
        task.setStatusCode("EXECUTING");
        assertDoesNotThrow(() -> authorization.authorize(executeQuery(3L)));
        previous.setPrecheckExpiresAt(LocalDateTime.now().minusSeconds(1));
        var error = assertThrows(InspectionException.class, () -> authorization.authorize(executeQuery(3L)));
        assertEquals(InspectionException.Reason.PRECHECK_REQUIRED, error.getReason());
    }

    @Test
    void savedCredentialFreezesOnlyPltResolvedValuesAndThenRejectsDuplicateBinding() {
        execution.setCredentialId(70L);
        execution.setHost("client-only.example"); execution.setPort(2200); execution.setUsername("client-only-user");
        var actual = new Connection(" RESOLVED.EXAMPLE ", 2222, "SSH", " resolved-user ", 70L, 6L, 90L);

        authorization.authorizeConnection(prepared(actual));

        var saved = ArgumentCaptor.forClass(InspectionExecutionDO.class);
        verify(executions).updateById(saved.capture());
        assertSame(execution, saved.getValue());
        assertEquals("resolved.example", saved.getValue().getHost());
        assertEquals(2222, saved.getValue().getPort());
        assertEquals("resolved-user", saved.getValue().getUsername());
        assertEquals(6L, saved.getValue().getCredentialVersion());
        assertEquals(90L, saved.getValue().getCredentialGrantId());
        assertTrue(saved.getValue().getCredentialSnapshotRecorded());
        assertThrows(InspectionException.class, () -> authorization.authorizeConnection(prepared(actual)));
        verify(executions, times(1)).updateById(any(InspectionExecutionDO.class));
    }

    @Test
    void savedCredentialIdentityOrProtocolSubstitutionDoesNotWriteAnySnapshot() {
        execution.setCredentialId(70L);
        assertThrows(InspectionException.class, () -> authorization.authorizeConnection(prepared(
                new Connection("resolved.example", 2222, "SSH", "resolved-user", 71L, 4L, 80L))));
        assertThrows(InspectionException.class, () -> authorization.authorizeConnection(prepared(
                new Connection("resolved.example", 2222, "TELNET", "resolved-user", 70L, 4L, 80L))));
        verify(executions, never()).updateById(any(InspectionExecutionDO.class));
        assertFalse(execution.getCredentialSnapshotRecorded());
    }

    @Test
    void changedTemporaryHostPortOrUsernameCannotReplaceTheRegisteredContext() {
        for (var actual : List.of(
                new Connection("other.example", 22, "SSH", "operator", null, null, null),
                new Connection("device.example", 23, "SSH", "operator", null, null, null),
                new Connection("device.example", 22, "SSH", "other-user", null, null, null))) {
            assertThrows(InspectionException.class, () -> authorization.authorizeConnection(prepared(actual)));
        }
        verify(executions, never()).updateById(any(InspectionExecutionDO.class));
        assertEquals("device.example", execution.getHost());
        assertFalse(execution.getCredentialSnapshotRecorded());
    }

    @Test
    void identicalTemporaryContextWithCanonicalHostAndUsernameIsAllowed() {
        authorization.authorizeConnection(prepared(new Connection(" DEVICE.EXAMPLE ", 22, "SSH", " operator ", null, null, null)));
        verify(executions).updateById(execution);
        assertTrue(execution.getCredentialSnapshotRecorded());
        assertNull(execution.getCredentialVersion());
        assertNull(execution.getCredentialGrantId());
    }

    @Test
    void preparedCredentialVersionOrGrantChangeStillRequiresAnotherPrecheck() {
        activeCollect();
        for (var actual : List.of(
                new Connection("device.example", 22, "SSH", "operator", 70L, 5L, 80L),
                new Connection("device.example", 22, "SSH", "operator", 70L, 4L, 81L))) {
            var error = assertThrows(InspectionException.class, () -> authorization.authorizeConnection(prepared(actual)));
            assertEquals(InspectionException.Reason.PRECHECK_REQUIRED, error.getReason());
        }
        verify(executions, never()).updateById(any(InspectionExecutionDO.class));
        assertFalse(execution.getCredentialSnapshotRecorded());
    }

    @Test
    void unchangedActualCollectConnectionCanBeFrozenAfterItsSuccessfulPrecheck() {
        var previous = activeCollect();
        authorization.authorizeConnection(prepared(InspectionOnlineAuthorization.connection(previous)));
        verify(executions).updateById(execution);
        assertEquals(4L, execution.getCredentialVersion());
        assertEquals(80L, execution.getCredentialGrantId());
        assertTrue(execution.getCredentialSnapshotRecorded());
    }

    @Test
    void staleSourceVersionOrRevokedOwnerAccessCannotFreezeResolvedCredentials() {
        execution.setCredentialId(70L);
        execution.setVersion(4L);
        var actual = new Connection("resolved.example", 2222, "SSH", "resolved-user", 70L, 6L, 90L);
        assertThrows(InspectionException.class, () -> authorization.authorizeConnection(prepared(actual)));
        execution.setVersion(3L);
        when(access.task(any(InspectionAccessService.TaskAccess.class)))
                .thenThrow(new InspectionException(InspectionException.Reason.FORBIDDEN, "Owner权限已撤回"));
        var error = assertThrows(InspectionException.class, () -> authorization.authorizeConnection(prepared(actual)));
        assertEquals(InspectionException.Reason.FORBIDDEN, error.getReason());
        verify(executions, never()).updateById(any(InspectionExecutionDO.class));
        assertFalse(execution.getCredentialSnapshotRecorded());
    }

    private static void precheckRejected(Precheck input) {
        var error = assertThrows(InspectionException.class, () -> InspectionOnlineAuthorization.verifyPrecheck(input));
        assertEquals(InspectionException.Reason.PRECHECK_REQUIRED, error.getReason());
    }

    private static InspectionExecutionDO sourceExecution() {
        var row = new InspectionExecutionDO();
        row.setId(50L); row.setTenantId(1L); row.setVersion(3L);
        row.setTaskId(10L); row.setAttemptId(20L); row.setDeviceId(30L); row.setSubmittedBy(40L);
        row.setPurposeCode("PRECHECK"); row.setStatusCode("PENDING"); row.setRuleDigest("frozen-rule-digest");
        row.setHost("device.example"); row.setPort(22); row.setProtocol("SSH"); row.setUsername("operator");
        row.setCredentialSnapshotRecorded(false);
        return row;
    }

    private static InspectionExecutionDO collectExecution() {
        var row = sourceExecution();
        row.setPurposeCode("COLLECT"); row.setCredentialId(70L); row.setPrecheckExecutionId(51L);
        return row;
    }

    private static InspectionExecutionDO passedPrecheck() {
        var row = sourceExecution();
        row.setId(51L); row.setStatusCode("PASSED"); row.setCredentialId(70L);
        row.setCredentialVersion(4L); row.setCredentialGrantId(80L); row.setCredentialSnapshotRecorded(true);
        row.setCompletedAt(CHECKED_AT); row.setPrecheckExpiresAt(EXPIRES_AT); row.setPrecheckTtlSeconds(900);
        row.setCollectionResultVersion(1);
        return row;
    }

    private InspectionExecutionDO activeCollect() {
        execution = collectExecution();
        task.setStatusCode("EXECUTING");
        var previous = passedPrecheck();
        previous.setPrecheckExpiresAt(LocalDateTime.now().plusMinutes(15));
        when(executions.selectById(51L)).thenReturn(previous);
        var receipt = new InspectionCollectionReceiptDO(); receipt.setResultVersion(1L);
        when(receipts.selectForExecutionForUpdate(any())).thenReturn(List.of(receipt));
        return previous;
    }

    private static CollectionSourceAdapter.AuthorizationQuery executeQuery(Long version) {
        return CollectionSourceAdapter.AuthorizationQuery.builder().tenantId(1L).actorId(40L).objectId(50L)
                .deviceId(30L).access(CollectionSourceAdapter.Access.EXECUTE).expectedVersion(version).build();
    }

    private CollectionSourceAdapter.PreparedConnection prepared(Connection actual) {
        var source = CollectionSourceAdapter.Source.builder().entry("inspection").objectId(50L)
                .sourceContext("SRV").sourceObjectType("InspectionExecution").projectId(100L).deviceId(30L)
                .deviceName("device").version(3L).manualAllowed(false).canExecute(true)
                .completionMode("BUSINESS_CONSUMPTION").title("在线巡检").build();
        return CollectionSourceAdapter.PreparedConnection.builder().authorization(executeQuery(3L)).source(source)
                .host(actual.host()).port(actual.port()).protocol(actual.protocol()).username(actual.username())
                .credentialId(actual.credentialId()).credentialVersion(actual.credentialVersion()).grantId(actual.grantId()).build();
    }
}
