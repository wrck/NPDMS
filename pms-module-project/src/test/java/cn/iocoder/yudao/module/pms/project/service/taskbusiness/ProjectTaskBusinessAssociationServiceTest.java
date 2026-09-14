package cn.iocoder.yudao.module.pms.project.service.taskbusiness;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider;
import cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider.AssociationCandidate;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectNodeExecutionDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskbusiness.ProjectTaskBusinessLinkDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectTaskExecutionContractMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskbusiness.ProjectTaskBusinessLinkMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskRuntimeMapper;
import org.junit.jupiter.api.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectTaskBusinessAssociationServiceTest {
    private final ProjectTaskRuntimeMapper tasks = mock(ProjectTaskRuntimeMapper.class);
    private final ProjectTaskExecutionContractMapper contracts = mock(ProjectTaskExecutionContractMapper.class);
    private final ProjectTaskBusinessLinkMapper links = mock(ProjectTaskBusinessLinkMapper.class);
    private final ProjectNodeExecutionMapper executions = mock(ProjectNodeExecutionMapper.class);
    private final cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper graph = mock(cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper.class);
    private final TaskBusinessObjectProvider owner = mock(TaskBusinessObjectProvider.class);
    private final OperationAuditApi audit = mock(OperationAuditApi.class);
    private ProjectTaskBusinessAssociationService service;
    private ProjectTaskInstanceDO task;
    private ProjectNodeExecutionDO execution;
    private ProjectTaskExecutionContractDO contract;

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        when(owner.ownerContext()).thenReturn("SOL"); when(owner.objectType()).thenReturn("SITE_SURVEY");
        service = new ProjectTaskBusinessAssociationService(tasks, contracts, links,
                new TaskBusinessProviderRegistry(List.of(owner)), audit, executions, graph);
        var project = new ProjectMasterDO(); project.setId(9L); project.setTenantId(1L); project.setLifecycleStatus("ACTIVE"); project.setActivePlanVersionId(50L);
        task = new ProjectTaskInstanceDO(); task.setId(11L); task.setProjectId(9L); task.setStatus("PENDING_ASSIGN");
        contract = new ProjectTaskExecutionContractDO(); contract.setId(31L); contract.setContractVersion(1);
        contract.setWorkBindingTypeCode("BUSINESS_COMPONENT"); contract.setTargetContextCode("SOL"); contract.setTargetObjectType("SITE_SURVEY");
        execution = new ProjectNodeExecutionDO(); execution.setId(70L); execution.setNodeKind("TASK"); execution.setNodeInstanceId(11L);
        execution.setContractId(31L); execution.setPlanVersionId(50L); execution.setStatus("PENDING"); execution.setRoundNo(7);
        when(tasks.selectProjectForCommandForUpdate(any())).thenReturn(project);
        when(tasks.selectTaskForAssignmentForUpdate(any())).thenReturn(task);
        when(contracts.selectCurrentByTaskIdForUpdate(any())).thenReturn(contract);
        when(executions.selectCurrentForUpdate(any())).thenReturn(List.of(execution));
        when(links.insertLink(any())).thenReturn(1); when(links.unlinkIfMatch(any())).thenReturn(1);
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }

    @Test void newRecordReceivesLatestPendingExecutionWithoutBusinessRoundOrTimestamp() {
        when(owner.associationCandidates(any(),isNull(),eq(100))).thenReturn(List.of(new AssociationCandidate("41","v1")));
        service.synchronize(9L,11L,"business-created");
        verify(links).insertLink(argThat(link -> link.getTaskId()==11L && link.getNodeExecutionId()==70L
                && link.getObjectId().equals("41") && link.getLinkedBy()==0L));
        verify(tasks, never()).updateLifecycleIfMatch(any());
        verify(owner, never()).lockCompletionFact(any(),any());
    }

    @Test void newRoundGetsNewRecordsWhilePreviousAssociationsRemainHistory() {
        when(owner.associationCandidates(any(),isNull(),eq(100))).thenReturn(List.of(
                new AssociationCandidate("40","old-result"), new AssociationCandidate("41","new-result")));
        when(links.selectPreviouslyAssociatedObjectIds(any())).thenReturn(List.of("40"));
        var previous = link(40L,"40",60L); when(links.selectActiveForUpdate(any())).thenReturn(List.of(previous));
        service.synchronize(9L,11L,"new-round");
        verify(links).unlinkIfMatch(argThat(change -> change.linkId()==40L && change.expectedVersion()==0));
        verify(links).insertLink(argThat(link -> link.getObjectId().equals("41") && link.getNodeExecutionId()==70L));
        assertNull(previous.getUnlinkedAt()); // Persistence is a versioned close, never in-place history rewriting.
    }

    @Test void delayedBusinessEventResolvesLatestActiveRoundInsteadOfRememberingPreviousReceiver() {
        task.setStatus("IN_PROGRESS");
        execution.setStatus("ACTIVE");
        when(owner.associationCandidates(any(),isNull(),eq(100)))
                .thenReturn(List.of(new AssociationCandidate("41","v1")))
                .thenReturn(List.of(new AssociationCandidate("41","completed"), new AssociationCandidate("42","v1")));
        service.synchronize(9L,11L,"original-business-event");

        // Rework renews both the execution and its frozen contract before a delayed business wakeup arrives.
        var previous = link(51L,"41",70L);
        execution.setId(80L);
        execution.setRoundNo(8);
        execution.setContractId(32L); contract.setId(32L); contract.setContractVersion(2);
        when(links.selectActiveForUpdate(any())).thenReturn(List.of(previous));
        when(links.selectPreviouslyAssociatedObjectIds(any())).thenReturn(List.of("41"));
        service.synchronize(9L,11L,"delayed-business-event");

        var inserted = org.mockito.ArgumentCaptor.forClass(ProjectTaskBusinessLinkDO.class);
        verify(links,times(2)).insertLink(inserted.capture());
        assertEquals(List.of("41","42"), inserted.getAllValues().stream().map(ProjectTaskBusinessLinkDO::getObjectId).toList());
        assertEquals(List.of(70L,80L), inserted.getAllValues().stream().map(ProjectTaskBusinessLinkDO::getNodeExecutionId).toList());
        assertEquals(List.of(31L,32L), inserted.getAllValues().stream().map(ProjectTaskBusinessLinkDO::getExecutionContractId).toList());
        assertEquals(List.of(1,2), inserted.getAllValues().stream().map(ProjectTaskBusinessLinkDO::getContractVersion).toList());
        verify(links).unlinkIfMatch(argThat(change -> change.linkId()==51L));
        assertEquals(70L, previous.getNodeExecutionId());
        assertEquals(31L, previous.getExecutionContractId()); assertEquals(1, previous.getContractVersion());
        verify(tasks,never()).updateLifecycleIfMatch(any());
        verify(owner,never()).lockCompletionFact(any(),any());
    }

    @Test void liveInitialReferenceCanEnterAutomaticManagementWithoutReusingPreviousRoundHistory() {
        execution.setRoundNo(1);
        var existing = link(51L,"41",70L); existing.setNodeExecutionId(null);
        when(links.selectActiveForUpdate(any())).thenReturn(List.of(existing));
        when(owner.associationCandidates(any(),isNull(),eq(100))).thenReturn(List.of(new AssociationCandidate("41","v1")));
        when(links.selectPreviouslyAssociatedObjectIds(any())).thenReturn(List.of("41"));
        service.synchronize(9L,11L,"initial-live-reference");
        verify(links).unlinkIfMatch(any());
        verify(links).insertLink(argThat(link -> link.getNodeExecutionId()==70L && link.getObjectId().equals("41")));
    }

    @Test void repeatedEventsDoNotRecreateCurrentAssociationWhenOwnerVersionChanges() {
        when(owner.associationCandidates(any(),isNull(),eq(100))).thenReturn(List.of(new AssociationCandidate("41","v2")));
        when(links.selectActiveForUpdate(any())).thenReturn(List.of(link(51L,"41",70L)));
        service.synchronize(9L,11L,"repeat");
        verify(links,never()).insertLink(any()); verify(links,never()).unlinkIfMatch(any()); verifyNoInteractions(audit);
    }

    @Test void unavailableOwnerOrInconsistentCurrentExecutionDoesNotClearRelationships() {
        when(owner.associationCandidates(any(),any(),anyInt())).thenReturn(null);
        assertThrows(IllegalStateException.class, () -> service.synchronize(9L,11L,"unavailable"));
        verify(links,never()).unlinkIfMatch(any());
        execution.setContractId(999L);
        assertThrows(IllegalStateException.class, () -> service.synchronize(9L,11L,"stale"));
        verify(links,never()).insertLink(any());
    }

    @Test void completedTaskHistoryIsNotReassociated() {
        task.setStatus("DONE"); service.synchronize(9L,11L,"late-business-event");
        verifyNoInteractions(contracts,links,executions);
    }

    @Test void stageUsesTheSameReconciliationWithItsOwnExecutionAndNoTaskIdentity() {
        stageReceiver();
        when(owner.associationCandidates(any(),isNull(),eq(100))).thenReturn(List.of(new AssociationCandidate("41","v1")));
        service.synchronizeStage(9L,90L,"stage-business-created");
        verify(links).insertLink(argThat(link -> link.getTaskId()==null && link.getStageId()==90L
                && link.getNodeExecutionId()==70L && link.getExecutionContractId()==31L && link.getObjectId().equals("41")));
        verify(links).selectPreviouslyAssociatedObjectIds(argThat(query -> query.stageId()==90L && query.taskId()==null));
        verify(tasks,never()).selectTaskForAssignmentForUpdate(any());
        verifyNoInteractions(contracts);
    }

    @Test void stageReworkExcludesPriorRecordsAndOnlyAssociatesNewRecordsToTheCurrentStageRound() {
        stageReceiver();
        var previous = link(51L,"40",60L); previous.setStageId(90L);
        when(links.selectActiveForUpdate(any())).thenReturn(List.of(previous));
        when(links.selectPreviouslyAssociatedObjectIds(any())).thenReturn(List.of("40"));
        when(owner.associationCandidates(any(),isNull(),eq(100))).thenReturn(List.of(new AssociationCandidate("40","completed"),new AssociationCandidate("41","new")));
        service.synchronizeStage(9L,90L,"stage-rework");
        verify(links).unlinkIfMatch(argThat(query -> query.taskId()==null && query.stageId()==90L && query.linkId()==51L));
        verify(links).insertLink(argThat(link -> link.getObjectId().equals("41") && link.getNodeExecutionId()==70L));
        assertEquals(60L,previous.getNodeExecutionId());
        verify(owner,never()).lockStageCompletionFact(any(),any());
    }

    @Test void stageWithStalePlanDoesNotAskOwnerOrAlterAnyAssociation() {
        stageReceiver(); execution.setPlanVersionId(49L);
        assertThrows(IllegalStateException.class, () -> service.synchronizeStage(9L,90L,"stale-stage"));
        verify(owner,never()).associationCandidates(any(),any(),anyInt());
        verifyNoInteractions(links);
    }

    private void stageReceiver() {
        var stage = new ProjectStageInstanceDO(); stage.setId(90L); stage.setTenantId(1L); stage.setProjectId(9L);
        stage.setGraphVersion(1L); stage.setStatus("ACTIVE");
        var stageContract = new cn.iocoder.yudao.module.pms.project.dal.dataobject.runtimegraph.ProjectStageExecutionContractDO();
        stageContract.setId(31L); stageContract.setTenantId(1L); stageContract.setProjectId(9L); stageContract.setStageId(90L);
        stageContract.setGraphVersion(1L); stageContract.setBindingVersion(1); stageContract.setSourceNodeKey("stage:prep");
        stageContract.setBindingType("BUSINESS_COMPONENT");
        stageContract.setBindingSnapshot("{\"type\":\"BUSINESS_COMPONENT\",\"targetContextCode\":\"SOL\",\"targetObjectType\":\"SITE_SURVEY\",\"parameters\":{}}");
        when(graph.selectStagesForUpdate(any())).thenReturn(List.of(stage));
        when(graph.selectContracts(any())).thenReturn(List.of(stageContract));
        execution.setNodeKind("STAGE"); execution.setNodeInstanceId(90L); execution.setNodeKey("stage:prep"); execution.setStatus("ACTIVE");
    }

    @Test void allPagesAreResolvedBeforeAssociationsAreWritten() {
        var first = java.util.stream.IntStream.rangeClosed(1,100).mapToObj(id -> new AssociationCandidate(String.valueOf(id),"v1")).toList();
        when(owner.associationCandidates(any(),isNull(),eq(100))).thenReturn(first);
        when(owner.associationCandidates(any(),eq("100"),eq(100))).thenReturn(List.of(new AssociationCandidate("101","v1")));
        service.synchronize(9L,11L,"bulk");
        verify(links,times(101)).insertLink(any());
    }

    private ProjectTaskBusinessLinkDO link(long id,String objectId,long executionId) {
        var link = new ProjectTaskBusinessLinkDO(); link.setId(id); link.setVersion(0); link.setObjectId(objectId);
        link.setExecutionContractId(31L); link.setContractVersion(1); link.setNodeExecutionId(executionId);
        link.setOwnerContext("SOL"); link.setObjectType("SITE_SURVEY"); return link;
    }
}
