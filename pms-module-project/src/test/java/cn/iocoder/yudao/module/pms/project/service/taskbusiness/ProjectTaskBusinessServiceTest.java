package cn.iocoder.yudao.module.pms.project.service.taskbusiness;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.businessview.*;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider;
import cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskbusiness.ProjectTaskBusinessLinkDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectTaskExecutionContractMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskbusiness.ProjectTaskBusinessLinkMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskRuntimeMapper;
import cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectTaskBusinessService.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProjectTaskBusinessServiceTest {
    TaskBusinessAccess access = mock(TaskBusinessAccess.class);
    TaskBusinessObjectProvider provider = mock(TaskBusinessObjectProvider.class);
    ProjectTaskBusinessLinkMapper links = mock(ProjectTaskBusinessLinkMapper.class);
    ProjectTaskRuntimeMapper tasks = mock(ProjectTaskRuntimeMapper.class);
    ProjectTaskExecutionContractMapper contracts = mock(ProjectTaskExecutionContractMapper.class);
    PlatformCommandExecutionApi commands = mock(PlatformCommandExecutionApi.class);
    OperationAuditApi audit = mock(OperationAuditApi.class);
    BusinessViewQueryApi views = mock(BusinessViewQueryApi.class);
    ProjectTaskBusinessService service;
    ProjectTaskInstanceDO task;
    ProjectMasterDO project;
    ProjectTaskExecutionContractDO contract;

    @BeforeEach void setup() {
        when(provider.ownerContext()).thenReturn("PRE"); when(provider.objectType()).thenReturn("SiteSurvey");
        service = service(List.of(provider));
        task = new ProjectTaskInstanceDO(); task.setId(10L); task.setTenantId(1L); task.setProjectId(20L);
        task.setVersion(3); task.setStatus("IN_PROGRESS");
        project = new ProjectMasterDO(); project.setId(20L); project.setTenantId(1L); project.setLifecycleStatus("ACTIVE");
        contract = new ProjectTaskExecutionContractDO(); contract.setId(30L); contract.setTenantId(1L);
        contract.setProjectTaskId(10L); contract.setContractVersion(2); contract.setTargetContextCode("PRE");
        contract.setTargetObjectType("SiteSurvey"); contract.setComponentKey("survey-list");
        contract.setWorkBindingTypeCode("BUSINESS_COMPONENT");
        contract.setBindingParameterSnapshot("{\"bindingType\":\"BUSINESS_COMPONENT\",\"businessViewRevisionId\":40,\"instanceResolutionStrategy\":\"REFERENCE_EXISTING\",\"contextMapping\":{\"project\":\"project\"}}");
        when(access.read(10L, 1L, 5L)).thenReturn(task); when(access.project(20L)).thenReturn(project);
        when(access.writable(any(), any(), any())).thenReturn(true);
        when(links.selectCurrentContract(any())).thenReturn(contract);
        when(links.selectActive(any())).thenReturn(List.of()); when(links.selectActiveForUpdate(any())).thenReturn(List.of());
        when(tasks.selectProjectForCommandForUpdate(any())).thenReturn(project);
        when(tasks.selectTaskForAssignmentForUpdate(any())).thenReturn(task);
        when(contracts.selectCurrentByTaskIdForUpdate(any())).thenReturn(contract);
        when(provider.inspectContext(any())).thenReturn(Set.of("QUERY", "CREATE"));
        when(provider.candidates(any())).thenReturn(List.of(fact("candidate", "v1")));
        when(provider.inspect(any(), anyString())).thenAnswer(i -> fact(i.getArgument(1), "v1"));
        when(provider.lockAndRevalidate(any(), anyString(), anyString())).thenAnswer(i -> fact(i.getArgument(1), i.getArgument(2)));
        when(links.insertLink(any())).thenReturn(1); when(links.unlinkIfMatch(any())).thenReturn(1);
        when(tasks.incrementTaskVersionIfMatch(any())).thenReturn(1);
        when(views.getRevision(any())).thenReturn(new BusinessViewRevision(40L, "SiteSurvey", "survey", 1L,
                "PRE", BusinessViewComponentProvider.ViewSource.PAGE, "survey-list", "1", null,
                JsonUtils.parseTree("{}"), JsonUtils.parseTree("[]"), "query", "command", "permission", null, null,
                1, "PUBLISHED", Set.of("UPDATE")));
        executeOperations();
    }
    ProjectTaskBusinessService service(List<TaskBusinessObjectProvider> providers) {
        return new ProjectTaskBusinessService(access, new TaskBusinessProviderRegistry(providers), links, tasks,
                contracts, commands, audit, views);
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    void executeOperations() {
        when(commands.execute(any(), anyString(), eq(LinkCommandResult.class), any(), any())).thenAnswer(i -> {
            TransactionSynchronizationManager.setActualTransactionActive(true);
            try { return new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW,
                    ((Supplier) i.getArgument(3)).get()); }
            finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        });
    }
    @AfterEach void clearTx() { TransactionSynchronizationManager.clear(); }
    BusinessObjectFact fact(String id, String version) {
        return new BusinessObjectFact(id, "Survey " + id, version, Set.of("LINK", "UNLINK", "QUERY"),
                Map.of("COMPLETED", true), List.of(new BusinessArtifact("a1", 1, "survey:artifact:1", "Report", "v1")));
    }
    ProjectTaskBusinessLinkDO row(long id, String objectId) {
        var row = new ProjectTaskBusinessLinkDO(); row.setId(id); row.setTenantId(1L); row.setProjectId(20L);
        row.setTaskId(10L); row.setExecutionContractId(30L); row.setContractVersion(2);
        row.setOwnerContext("PRE"); row.setObjectType("SiteSurvey"); row.setObjectId(objectId);
        row.setFactVersion("v0"); row.setVersion(0); row.setLinkedAt(LocalDateTime.now().minusDays(1));
        return row;
    }
    LinkCommand command() { return new LinkCommand(10L, "x", 3, 2, "key"); }

    @Test void contextReadsDoNotCreateBusinessOrRelationships() {
        var result = service.getContext(10L, 1L, 5L, "corr");
        assertNull(result.recoverableError()); assertEquals(Set.of("LINK"), result.allowedActions());
        assertEquals(Set.of("QUERY", "CREATE"), result.ownerActions());
        assertTrue(result.businessView().allowedActions().isEmpty());
        verify(links, never()).insertLink(any()); verifyNoInteractions(commands);
        verify(provider, never()).lockAndRevalidate(any(), any(), any());
    }
    @Test void multipleRecordsRemainIndependentlyLinked() {
        when(links.selectActive(any())).thenReturn(List.of(row(51, "x"), row(52, "y")));
        var result = service.inspectLinkedFactsSnapshot(10L, 1L, 5L, "corr");
        assertEquals(List.of("x", "y"), result.links().stream().map(TaskBusinessLinkFact::objectId).toList());
        assertEquals(64, result.factVersion().length());
        verify(provider).inspect(argThat(c -> c.projectId().equals(20L) && c.tenantId().equals(1L)), eq("x"));
    }
    @Test void missingAndDuplicateProvidersFailClosed() {
        assertEquals("OWNER_PROVIDER_NOT_REGISTERED", service(List.of()).getContext(10L, 1L, 5L, "c").recoverableError());
        assertEquals("OWNER_PROVIDER_AMBIGUOUS", service(List.of(provider, provider)).getContext(10L, 1L, 5L, "c").recoverableError());
        assertThrows(ServiceException.class, () -> service(List.of()).link(command(), 1L, 5L, "c"));
        verify(links, never()).insertLink(any());
    }
    @Test void crossTenantContractAndCrossProjectLinksFailBeforeOwnerInspect() {
        contract.setTenantId(2L);
        assertEquals("CONTRACT_IDENTITY_MISMATCH", service.getContext(10L, 1L, 5L, "c").recoverableError());
        contract.setTenantId(1L); var foreign = row(51, "x"); foreign.setProjectId(99L);
        when(links.selectActive(any())).thenReturn(List.of(foreign));
        assertThrows(ServiceException.class, () -> service.inspectLinkedFacts(10L, 1L, 5L, "c"));
        verify(provider, never()).inspect(any(), any());
    }
    @Test void ownerRejectsForeignProjectAndOriginalRelationsRemainUntouched() {
        when(provider.inspect(any(), eq("x"))).thenThrow(new SecurityException("different project"));
        assertThrows(SecurityException.class, () -> service.link(command(), 1L, 5L, "c"));
        verify(links, never()).insertLink(any()); verify(links, never()).unlinkIfMatch(any());
        verify(audit).record(eq(1L), eq(5L), eq("c"), any(), any(), any(), eq("REJECTED"), any());
    }
    @Test void staleTaskOrContractVersionRejectsBeforeOwnerLock() {
        assertThrows(ServiceException.class, () -> service.link(new LinkCommand(10L, "x", 2, 2, "k"), 1L, 5L, "c"));
        assertThrows(ServiceException.class, () -> service.link(new LinkCommand(10L, "x", 3, 1, "k"), 1L, 5L, "c"));
        verify(provider, never()).lockAndRevalidate(any(), any(), any()); verify(links, never()).insertLink(any());
    }
    @Test void linkUsesFrozenOwnerAndRetainsOtherLinks() {
        when(links.selectActiveForUpdate(any())).thenReturn(List.of(row(51, "other")));
        var result = service.link(command(), 1L, 5L, "c");
        assertTrue(result.active()); assertEquals(4, result.taskVersion());
        var captured = ArgumentCaptor.forClass(ProjectTaskBusinessLinkDO.class); verify(links).insertLink(captured.capture());
        assertEquals("PRE", captured.getValue().getOwnerContext()); assertEquals("x", captured.getValue().getObjectId());
        verify(links, never()).unlinkIfMatch(any());
        var order = inOrder(tasks, contracts, links, provider);
        order.verify(tasks).selectProjectForCommandForUpdate(any()); order.verify(tasks).selectTaskForAssignmentForUpdate(any());
        order.verify(contracts).selectCurrentByTaskIdForUpdate(any()); order.verify(links).selectActiveForUpdate(any());
        order.verify(provider).inspect(any(), eq("x")); order.verify(provider).lockAndRevalidate(any(), eq("x"), eq("v1"));
        order.verify(links).insertLink(any());
    }
    @Test void unlinkClosesIntervalOnlyAndDoesNotRewriteOriginalFact() {
        var original = row(51, "x"); when(links.selectActiveForUpdate(any())).thenReturn(List.of(original, row(52, "y")));
        var result = service.unlink(new UnlinkCommand(10L, 51L, 3, 2, "k"), 1L, 5L, "c");
        assertFalse(result.active()); assertEquals("v0", original.getFactVersion()); assertNull(original.getUnlinkedAt());
        verify(links).unlinkIfMatch(argThat(q -> q.linkId().equals(51L) && q.actorId().equals(5L) && q.expectedVersion() == 0));
        verify(links, never()).insertLink(any()); verify(provider, never()).inspect(any(), eq("y"));
    }
    @Test void ownerVersionChangeAndMissingActionRejectBeforeWrite() {
        when(provider.lockAndRevalidate(any(), any(), any())).thenReturn(fact("x", "changed"));
        assertThrows(ServiceException.class, () -> service.link(command(), 1L, 5L, "c"));
        when(provider.inspect(any(), any())).thenReturn(new BusinessObjectFact("x", "X", "v1", Set.of("QUERY"), Map.of(), List.of()));
        assertThrows(ServiceException.class, () -> service.link(command(), 1L, 5L, "c"));
        verify(links, never()).insertLink(any());
    }
    @Test void replayReturnsStoredResultWithoutReenteringOwnerOrCas() {
        var result = new LinkCommandResult(10L, 51L, 4, 2, true);
        when(commands.execute(any(), anyString(), eq(LinkCommandResult.class), any(), any()))
                .thenReturn(new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED, result));
        assertEquals(result, service.link(command(), 1L, 5L, "c"));
        verifyNoInteractions(tasks); verify(provider, never()).inspect(any(), any()); verify(links, never()).insertLink(any());
    }
    @Test void idempotencyConflictDoesNotWrite() {
        when(commands.execute(any(), anyString(), eq(LinkCommandResult.class), any(), any()))
                .thenReturn(new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.CONFLICT, null));
        assertThrows(ServiceException.class, () -> service.link(command(), 1L, 5L, "c")); verifyNoInteractions(tasks);
    }
    @Test void linkedFactSetChangeInvalidatesCompletionVersionAndRequiresTransaction() {
        var one = row(51, "x"); when(links.selectActive(any())).thenReturn(List.of(one));
        String version = service.inspectLinkedFactsSnapshot(10L, 1L, 5L, "c").factVersion();
        assertThrows(ServiceException.class, () -> service.lockAndRevalidateLinkedFacts(10L, 1L, 5L, "c", version));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        when(links.selectActiveForUpdate(any())).thenReturn(List.of(one, row(52, "y")));
        assertThrows(ServiceException.class, () -> service.lockAndRevalidateLinkedFacts(10L, 1L, 5L, "c", version));
        when(links.selectActiveForUpdate(any())).thenReturn(List.of(one));
        assertEquals(version, service.lockAndRevalidateLinkedFacts(10L, 1L, 5L, "c", version).factVersion());
    }
    @Test void aggregateVersionIsRecheckedAfterOwnerLocksNotOnlyBeforeThem() {
        var row = row(51, "x");
        when(links.selectActive(any())).thenReturn(List.of(row));
        when(links.selectActiveForUpdate(any())).thenReturn(List.of(row));
        String expected = service.inspectLinkedFactsSnapshot(10L, 1L, 5L, "c").factVersion();
        // Initial aggregate check sees v1; the second inspection and Owner lock both see v2.
        when(provider.inspect(any(), eq("x"))).thenReturn(fact("x", "v1"), fact("x", "v2"));
        TransactionSynchronizationManager.setActualTransactionActive(true);

        var error = assertThrows(ServiceException.class,
                () -> service.lockAndRevalidateLinkedFacts(10L, 1L, 5L, "c", expected));

        assertEquals("TASK_BUSINESS_FACT_VERSION_CONFLICT", error.getMessage());
        verify(provider).lockAndRevalidate(any(), eq("x"), eq("v2"));
        verify(links, never()).insertLink(any());
        verify(tasks, never()).incrementTaskVersionIfMatch(any());
    }
    @Test void ownerLockChangingVersionRejectsEvenIfPreviewWasCurrent() {
        var row = row(51, "x");
        when(links.selectActive(any())).thenReturn(List.of(row));
        when(links.selectActiveForUpdate(any())).thenReturn(List.of(row));
        String expected = service.inspectLinkedFactsSnapshot(10L, 1L, 5L, "c").factVersion();
        when(provider.lockAndRevalidate(any(), eq("x"), eq("v1"))).thenReturn(fact("x", "v2"));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        assertThrows(ServiceException.class,
                () -> service.lockAndRevalidateLinkedFacts(10L, 1L, 5L, "c", expected));
        verify(links, never()).insertLink(any());
    }
    @Test void contextReturnsSameAggregateVersionUsedByLockedCompletion() {
        var row = row(51, "x");
        when(links.selectActive(any())).thenReturn(List.of(row));
        when(links.selectActiveForUpdate(any())).thenReturn(List.of(row));
        var context = service.getContext(10L, 1L, 5L, "c");
        assertNull(context.recoverableError());
        assertEquals(Set.of("QUERY", "CREATE"), context.ownerActions());
        assertEquals(40L, context.businessView().id());
        TransactionSynchronizationManager.setActualTransactionActive(true);
        var locked = service.lockAndRevalidateLinkedFacts(10L, 1L, 5L, "c", context.factVersion());
        assertEquals(context.factVersion(), locked.factVersion());
        assertEquals(context.links(), locked.links());
    }
    @Test void untrustedArtifactUrlsFailClosedWithoutBecomingCompletionFacts() {
        for (String reference : List.of("https://example.test/report.html", "data:text/html,report", "blob:report", "//host/report")) {
            when(provider.candidates(any())).thenReturn(List.of(new BusinessObjectFact("x", "X", "v1",
                    Set.of("LINK"), Map.of(), List.of(new BusinessArtifact("artifact", 1, reference, "Report", "v1")))));
            assertEquals("OWNER_ARTIFACT_INVALID", service.getContext(10L, 1L, 5L, "c").recoverableError());
        }
        verify(links, never()).insertLink(any());
    }
    @Test void frozenViewIdAcceptsExactPositiveLongNumbersAndFrameworkDecimalStrings() {
        for (String json : List.of("40", "\"40\"", "9007199254740993", "\"9007199254740993\"",
                "9223372036854775807", "\"9223372036854775807\"")) {
            contract.setBindingParameterSnapshot("{\"businessViewRevisionId\":" + json + "}");
            assertEquals(Long.valueOf(json.replace("\"", "")), TaskBusinessBinding.parse(contract).businessViewRevisionId());
        }
    }
    @Test void malformedFrozenViewIdsFailWithoutNumericCoercion() {
        for (String json : List.of("\"040\"", "\"0\"", "0", "-1", "\"-1\"", "\"+40\"", "\" 40\"",
                "\"4e1\"", "4e1", "40.0", "null", "true", "\"\"", "9223372036854775808", "\"9223372036854775808\"")) {
            contract.setBindingParameterSnapshot("{\"businessViewRevisionId\":" + json + "}");
            assertEquals("TASK_BUSINESS_BINDING_SNAPSHOT_INVALID",
                    assertThrows(ServiceException.class, () -> TaskBusinessBinding.parse(contract)).getMessage(), json);
        }
    }
    @Test void contextLoadsLargeFrozenViewIdFromFrameworkSerializedBinding() {
        long largeId = 9007199254740993L;
        contract.setBindingParameterSnapshot(JsonUtils.toJsonString(Map.of("bindingType", "BUSINESS_COMPONENT",
                "businessViewRevisionId", largeId, "instanceResolutionStrategy", "REFERENCE_EXISTING")));
        when(views.getRevision(any())).thenReturn(new BusinessViewRevision(largeId, "SiteSurvey", "survey", 1L,
                "PRE", BusinessViewComponentProvider.ViewSource.PAGE, "survey-list", "1", null,
                JsonUtils.parseTree("{}"), JsonUtils.parseTree("[]"), "query", "command", "permission", null, null,
                1, "PUBLISHED", Set.of()));
        var context = service.getContext(10L, 1L, 5L, "c");
        assertNull(context.recoverableError());
        assertEquals(largeId, context.businessViewRevisionId());
        assertEquals(largeId, context.businessView().id());
        verify(views).getRevision(new BusinessViewQueryApi.Query(largeId, BusinessViewQueryApi.Purpose.HISTORICAL_REFERENCE));
    }
    @Test void legacyViewIsExplicitlyUnavailableButNeverChangesContract() {
        contract.setBindingParameterSnapshot("{}");
        var result = service.getContext(10L, 1L, 5L, "c");
        assertEquals("VIEW_NOT_FROZEN", result.recoverableError()); assertEquals("survey-list", result.componentKey());
        assertTrue(result.allowedActions().isEmpty()); verifyNoInteractions(views);
    }
    @Test void historicalDisabledViewIsReadWithoutManagementGrant() {
        var original = views.getRevision(new BusinessViewQueryApi.Query(40L, BusinessViewQueryApi.Purpose.HISTORICAL_REFERENCE));
        when(views.getRevision(any())).thenReturn(new BusinessViewRevision(original.id(), original.entityType(), original.viewKey(),
                original.revisionNo(), original.ownerContext(), original.viewSource(), original.componentKey(), original.componentVersion(),
                null, original.contextSchema(), original.supportedActions(), "q", "c", "p", LocalDateTime.now().minusDays(2),
                LocalDateTime.now(), 2, "DISABLED", Set.of("ENABLE")));
        var result = service.getContext(10L, 1L, 5L, "c");
        assertNull(result.recoverableError()); assertEquals("DISABLED", result.businessView().status());
        assertTrue(result.businessView().allowedActions().isEmpty());
    }
    @Test void contextQueryPermissionIsNeverDefaultedAndViewIdentityMustMatch() {
        when(provider.inspectContext(any())).thenReturn(Set.of());
        assertEquals("OWNER_CONTEXT_FORBIDDEN", service.getContext(10L, 1L, 5L, "c").recoverableError());
        verify(views, never()).getRevision(any());
    }
}
