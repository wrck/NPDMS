package cn.iocoder.yudao.module.pms.cutover.service.closure;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.closure.*;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverProjectScopePort;
import cn.iocoder.yudao.module.pms.platform.api.collection.*;
import cn.iocoder.yudao.module.pms.platform.api.collection.dto.CollectionTaskDTO;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CutoverClosureExecutionLinkServiceTest {
    final CutoverTaskMapper tasks = mock(CutoverTaskMapper.class);
    final CutoverClosureMapper closures = mock(CutoverClosureMapper.class);
    final CutoverCollectionEvidenceMapper evidence = mock(CutoverCollectionEvidenceMapper.class);
    final CutoverProjectScopePort scopes = mock(CutoverProjectScopePort.class);
    final CollectionExecutionQueryApi executions = mock(CollectionExecutionQueryApi.class);
    final OperationAuditApi audit = mock(OperationAuditApi.class);
    final CutoverClosureDO closure = new CutoverClosureDO();
    CutoverClosureExecutionLinkService service;
    final CutoverClosureExecutionLinkService.Command command = new CutoverClosureExecutionLinkService.Command(
            1L, 2L, 3L, 4L, 5L, 6L, "cutover-post-collection", 7L, "correlation");

    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        ObjectProvider<CollectionExecutionQueryApi> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(executions);
        service = new CutoverClosureExecutionLinkService(tasks, closures, evidence, scopes, provider, audit);
        var task = new CutoverTaskDO(); task.setId(3L); task.setTenantId(1L); task.setProjectId(8L);
        task.setOwnerUserId(2L); task.setCurrentStage("P6"); task.setTaskStatus("CLOSURE_IN_PROGRESS"); task.setVersion(5L);
        when(tasks.selectForUpdate(any())).thenReturn(task);
        closure.setId(4L); closure.setStatusCode("DRAFT"); closure.setVersion(6L);
        when(closures.selectByTaskForUpdate(any())).thenReturn(closure);
        var scope = new CutoverProjectScopePort.ProjectScopeFact(8L, 1L, true);
        when(scopes.inspect(2L, 8L, "ACTION_EDIT")).thenReturn(scope);
        when(scopes.lockAndRevalidate(CutoverProjectScopePort.RevalidationQuery.builder()
                .actorId(2L)
                .projectId(8L)
                .action("ACTION_EDIT")
                .expectedProjectScopeVersion(1L)
                .build())).thenReturn(scope);
        when(evidence.selectListByClosureForUpdate(any())).thenReturn(List.of());
        when(evidence.insert(any(CutoverCollectionEvidenceDO.class))).thenReturn(1);
        when(closures.advanceDraftVersionIfMatch(any())).thenReturn(1);
        execution("FAILED", "8");
    }

    @Test void terminalFailureWithoutLogRemainsRealEvidenceAndCanBeReplayed() {
        service.link(command);
        var capture = ArgumentCaptor.forClass(CutoverCollectionEvidenceDO.class);
        verify(evidence, times(2)).insert(capture.capture());
        var facts = capture.getAllValues();
        assertEquals(List.of("DISPATCH_ACCEPTED", "CALLBACK_FAILED"), facts.stream().map(CutoverCollectionEvidenceDO::getEvidenceTypeCode).toList());
        assertEquals("PLATFORM_EXECUTION:7", facts.get(1).getResultRef());
        assertEquals("EXECUTION_STATE_FAILED", facts.get(1).getResultVersion());
        assertNull(facts.get(1).getManualAttachmentId());
        when(evidence.selectListByClosureForUpdate(any())).thenReturn(facts);
        closure.setVersion(7L);
        service.link(command);
        verify(evidence, times(2)).insert(any(CutoverCollectionEvidenceDO.class));
        verify(closures).advanceDraftVersionIfMatch(any());
    }

    @Test void runningExecutionIsRecordedBeforeItHasAResult() {
        execution("RUNNING", "8"); service.link(command);
        var capture = ArgumentCaptor.forClass(CutoverCollectionEvidenceDO.class);
        verify(evidence).insert(capture.capture());
        assertEquals("DISPATCH_ACCEPTED", capture.getValue().getEvidenceTypeCode());
        assertNull(capture.getValue().getResultRef());
    }

    @Test void wrongProjectAndStaleClosureCannotWriteEvidence() {
        execution("FAILED", "999");
        assertThrows(IllegalArgumentException.class, () -> service.link(command));
        execution("FAILED", "8"); closure.setVersion(8L);
        assertThrows(IllegalArgumentException.class, () -> service.link(command));
        verify(evidence, never()).insert(any(CutoverCollectionEvidenceDO.class));
    }

    @Test void platformBindingAuthorizationFailureCannotBeBypassed() {
        when(executions.get(any())).thenThrow(new CollectionOperationException("wrong business binding"));
        assertThrows(CollectionOperationException.class, () -> service.link(command));
        verifyNoInteractions(tasks, closures, evidence, audit);
    }

    private void execution(String status, String project) {
        var task = new CollectionTaskDTO(9L, null, "PLT-7", "CUT", "CutoverClosure", "4", project, "10", "设备",
                null, null, null, null, null, null, "SAVED", null, null, "intent", "COLLECT", status,
                "DISPATCH", null, null, null, null, null, null, "CONNECTION_FAILED");
        when(executions.get(any())).thenReturn(new CollectionApplicationApi.Execution(7L, 2L, LocalDateTime.now(), null, task, null, "采集", null));
    }
}
