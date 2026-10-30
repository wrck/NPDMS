package cn.iocoder.yudao.module.pms.service.service.inspection;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.collection.*;
import cn.iocoder.yudao.module.pms.platform.api.collection.dto.CollectionTaskDTO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.*;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.*;
import cn.iocoder.yudao.module.pms.service.framework.InspectionCollectionResultReceiver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InspectionCollectionResultReceiverTest {
    private final InspectionExecutionMapper executions = mock(InspectionExecutionMapper.class);
    private final InspectionTaskMapper tasks = mock(InspectionTaskMapper.class);
    private final InspectionCollectionReceiptMapper receipts = mock(InspectionCollectionReceiptMapper.class);
    private final CollectionTaskApi platform = mock(CollectionTaskApi.class);
    private final InspectionOnlineAuthorization authorization = mock(InspectionOnlineAuthorization.class);
    private final InspectionCollectionResultReceiver receiver = new InspectionCollectionResultReceiver(executions, tasks, receipts, platform, authorization);
    private final InspectionExecutionDO row = new InspectionExecutionDO();
    private final InspectionTaskDO task = new InspectionTaskDO();

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        task.setId(10L); task.setTenantId(1L); task.setProjectId(20L); task.setStatusCode("EXECUTING"); task.setVersion(7L);
        row.setId(30L); row.setTenantId(1L); row.setTaskId(10L); row.setDeviceId(40L); row.setSubmittedBy(50L);
        row.setPurposeCode("COLLECT"); row.setProtocol("SSH"); row.setCredentialSnapshotRecorded(true);
        row.setCollectionRequestKey("source-request"); row.setStatusCode("RUNNING");
        when(executions.selectById(30L)).thenReturn(row);
        when(executions.updateById(any(InspectionExecutionDO.class))).thenReturn(1);
        when(tasks.selectForUpdate(any())).thenReturn(task);
        when(receipts.selectForExecution(any())).thenReturn(List.of());
        when(authorization.profile(row)).thenReturn(new CollectionExecutionProfile("INSPECTION", "show version"));
        when(platform.getTask(1L, "plt-task")).thenReturn(remote(1L, 60L));
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }

    @Test void firstReceiptFreezesResultWithoutAdvancingBusinessOrExecutionState() {
        receiver.receive(result(1L, 60L));
        assertEquals(1, row.getCollectionResultVersion()); assertEquals(60L, row.getCollectionFileVersionId());
        assertEquals(70L, row.getCollectionExecutionId()); assertEquals("RUNNING", row.getStatusCode());
        assertEquals("EXECUTING", task.getStatusCode()); assertEquals(7L, task.getVersion());
        verify(tasks, never()).updateById(any(InspectionTaskDO.class));
        verify(receipts).insert(any(InspectionCollectionReceiptDO.class));
    }

    @Test void duplicateAlreadyReceivedVersionIsIdempotentAfterPlatformHasAdvanced() {
        when(receipts.selectForExecution(any())).thenReturn(List.of(receipt(1L, 60L)));
        when(platform.getTask(1L, "plt-task")).thenReturn(remote(2L, 61L));
        receiver.receive(result(1L, 60L));
        verify(receipts, never()).insert(any(InspectionCollectionReceiptDO.class));
        verify(executions, never()).updateById(any(InspectionExecutionDO.class));
    }

    @Test void sameVersionDifferentFileCannotRewriteReceipt() {
        when(receipts.selectForExecution(any())).thenReturn(List.of(receipt(1L, 60L)));
        when(platform.getTask(1L, "plt-task")).thenReturn(remote(1L, 61L));
        assertThrows(InspectionException.class, () -> receiver.receive(result(1L, 61L)));
        verify(receipts, never()).insert(any(InspectionCollectionReceiptDO.class));
        verify(executions, never()).updateById(any(InspectionExecutionDO.class));
    }

    @Test void laterVersionIsSeparateFactAndDoesNotReplaceSelectedFile() {
        row.setCollectionResultVersion(1); row.setCollectionFileVersionId(60L);
        row.setCollectionExecutionId(70L); row.setCollectionTaskId("plt-task"); row.setStatusCode("COMPLETE");
        when(platform.getTask(1L, "plt-task")).thenReturn(remote(2L, 61L));
        receiver.receive(result(2L, 61L));
        var captured = ArgumentCaptor.forClass(InspectionCollectionReceiptDO.class);
        verify(receipts).insert(captured.capture()); assertEquals(2L, captured.getValue().getResultVersion());
        assertEquals(1, row.getCollectionResultVersion()); assertEquals(60L, row.getCollectionFileVersionId());
        assertEquals("COMPLETE", row.getStatusCode()); verify(executions, never()).updateById(any(InspectionExecutionDO.class));
    }

    @Test void lateCallbackRemainsEvidenceAfterReassignmentAndCancellation() {
        task.setOwnerUserId(99L); task.setStatusCode("CANCELLED"); row.setStatusCode("CANCELLED");
        receiver.receive(result(1L, 60L));
        assertEquals("CANCELLED", task.getStatusCode()); assertEquals("CANCELLED", row.getStatusCode());
        verify(receipts).insert(any(InspectionCollectionReceiptDO.class));
    }

    @Test void unknownOlderResultCannotBeAcceptedAgainstNewCurrentPlatformVersion() {
        when(platform.getTask(1L, "plt-task")).thenReturn(remote(2L, 61L));
        assertThrows(InspectionException.class, () -> receiver.receive(result(1L, 60L)));
        verify(receipts, never()).insert(any(InspectionCollectionReceiptDO.class));
    }

    @Test void wrongDeviceOrUnfrozenConnectionOrChangedCommandRejectsBeforeWriting() {
        row.setDeviceId(41L);
        assertThrows(InspectionException.class, () -> receiver.receive(result(1L, 60L)));
        row.setDeviceId(40L); row.setCredentialSnapshotRecorded(false);
        assertThrows(InspectionException.class, () -> receiver.receive(result(1L, 60L)));
        row.setCredentialSnapshotRecorded(true);
        when(authorization.profile(row)).thenReturn(new CollectionExecutionProfile("INSPECTION", "other command"));
        assertThrows(InspectionException.class, () -> receiver.receive(result(1L, 60L)));
        verify(receipts, never()).insert(any(InspectionCollectionReceiptDO.class));
        verify(executions, never()).updateById(any(InspectionExecutionDO.class));
    }

    @Test void crossTenantCallbackIsRejectedBeforeLookup() {
        TenantContextHolder.setTenantId(2L);
        assertThrows(InspectionException.class, () -> receiver.receive(result(1L, 60L)));
        verifyNoInteractions(executions, tasks, receipts, platform, authorization);
    }

    private static CollectionBusinessResultReceiver.Result result(Long version, Long file) {
        return new CollectionBusinessResultReceiver.Result(1L, "inspection", 30L, "SRV", "INSPECTION_EXECUTION", 20L, 40L,
                70L, 50L, "plt-task", version, file, "SSH", "SUCCEEDED", null, "show version", null);
    }
    private static CollectionTaskDTO remote(Long version, Long file) {
        return new CollectionTaskDTO(80L, 90L, "plt-task", "SRV", "INSPECTION_EXECUTION", "30", "20", "40", "device", "host", 22,
                "SSH", null, null, "hash", "TEMPORARY", null, null, "source-request", "BUSINESS_CONSUMPTION", "COMPLETED", "COMPLETE",
                version, file, "SRV", "INSPECTION_EXECUTION", "30", "SUCCEEDED", null);
    }
    private static InspectionCollectionReceiptDO receipt(Long version, Long file) {
        var receipt = new InspectionCollectionReceiptDO(); receipt.setCollectionExecutionId(70L); receipt.setCollectionTaskId("plt-task");
        receipt.setResultVersion(version); receipt.setFileVersionId(file); receipt.setExternalStatus("SUCCEEDED"); receipt.setActorId(50L); return receipt;
    }
}
