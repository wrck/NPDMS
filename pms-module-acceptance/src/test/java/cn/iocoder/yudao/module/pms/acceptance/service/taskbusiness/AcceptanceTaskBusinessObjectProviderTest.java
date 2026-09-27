package cn.iocoder.yudao.module.pms.acceptance.service.taskbusiness;

import cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider.AssociationContext;
import cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider.AssociationCandidate;
import cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider.CompletionContext;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider.Context;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptance.AccProjectDeliverableDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptancereport.*;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptance.AccProjectDeliverableMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptance.query.ProjectDeliverableIdLockQuery;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.*;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.query.*;
import cn.iocoder.yudao.module.pms.acceptance.service.acceptancereport.AcceptanceReportQueryService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AcceptanceTaskBusinessObjectProviderTest {
    private static final String KEY = "b8b04110-431e-47b7-b768-04a1751d6a7b";
    private final AcceptanceActivityMapper activities = mock(AcceptanceActivityMapper.class);
    private final AcceptanceReportVersionMapper reports = mock(AcceptanceReportVersionMapper.class);
    private final AcceptanceReportAttachmentMapper attachments = mock(AcceptanceReportAttachmentMapper.class);
    private final AccProjectDeliverableMapper deliverables = mock(AccProjectDeliverableMapper.class);
    private final ProjectDeliverableSourceVersionMapper sources = mock(ProjectDeliverableSourceVersionMapper.class);
    private final ProjectDeliverableSourceAttachmentMapper sourceAttachments = mock(ProjectDeliverableSourceAttachmentMapper.class);
    private final ProjectScopeApi scope = mock(ProjectScopeApi.class);
    private final PermissionApi permissions = mock(PermissionApi.class);
    private final FileArtifactApi files = mock(FileArtifactApi.class);
    private final cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi executions =
            mock(cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi.class);
    private final cn.iocoder.yudao.module.pms.acceptance.service.acceptancereport.AcceptanceReportBusinessResultSource reportResults =
            mock(cn.iocoder.yudao.module.pms.acceptance.service.acceptancereport.AcceptanceReportBusinessResultSource.class);
    // Exercise the existing Owner query service, rather than a second fake activity repository.
    private final AcceptanceReportQueryService queries = new AcceptanceReportQueryService(
            activities, reports, attachments, scope, files, sources);
    private final AcceptanceTaskBusinessObjectProvider provider = new AcceptanceTaskBusinessObjectProvider(
            queries, activities, reports, attachments, deliverables, sources, sourceAttachments, files, scope, permissions, executions, reportResults);
    private final Context context = new Context(3L, 9L, 100L, 200L, "acc-task-test");
    private final AcceptanceActivityDO activity = new AcceptanceActivityDO();
    private final AcceptanceReportVersionDO report = new AcceptanceReportVersionDO();
    private final AcceptanceReportAttachmentDO attachment = new AcceptanceReportAttachmentDO();
    private final ProjectDeliverableSourceVersionDO source = new ProjectDeliverableSourceVersionDO();
    private final AccProjectDeliverableDO deliverable = new AccProjectDeliverableDO();
    private final ProjectDeliverableSourceAttachmentDO sourceAttachment = new ProjectDeliverableSourceAttachmentDO();
    private final FileReferenceSetKey setKey = new FileReferenceSetKey("ACC", "ACCEPTANCE_REPORT_VERSION", "51", "ACCEPTANCE_REPORT_ATTACHMENT");

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(3L);
        LoginUser login = new LoginUser();
        login.setId(9L); login.setTenantId(3L);
        SecurityFrameworkUtils.setLoginUser(login, new MockHttpServletRequest());
        var visible = new ProjectScopeResult(100L, 1L, Set.of(100L), Set.of());
        when(scope.resolveCurrent(new ProjectCurrentScopeQuery(3L, 9L, 100L, ProjectScopeApi.ACTION_VIEW))).thenReturn(visible);
        when(scope.lockAndRevalidate(new ProjectScopeRevalidationQuery(3L, 9L, 100L, ProjectScopeApi.ACTION_VIEW, 1L))).thenReturn(visible);
        when(permissions.hasAnyPermissions(9L, "pms:acceptance:report:query")).thenReturn(true);
        activity.setId(42L); activity.setTenantId(3L); activity.setProjectId(100L);
        activity.setProjectTaskId(300L); activity.setExecutionContractId(400L);
        activity.setDeliverableId(91L);
        activity.setAcceptanceType("FINAL"); activity.setActivityStatus("PENDING");
        activity.setCurrentReportVersionId(51L); activity.setVersion(7L); activity.setDeleted(false);
        when(activities.selectById(42L)).thenReturn(activity);
        when(activities.selectByIdForUpdate(new AcceptanceActivityIdLockQuery(3L, 42L))).thenReturn(activity);
        report.setId(51L); report.setTenantId(3L); report.setAcceptanceId(42L); report.setReportVersionNo(2);
        report.setReportStatus("EFFECTIVE"); report.setAcceptanceTime(LocalDateTime.of(2026, 9, 10, 10, 0));
        report.setEffectiveFrom(report.getAcceptanceTime()); report.setConclusionCode("FAIL");
        report.setAcceptorName("验收人"); report.setDeleted(false);
        when(reports.selectById(51L)).thenReturn(report);
        when(reports.selectByIdForUpdate(new AcceptanceReportIdLockQuery(3L, 42L, 51L))).thenReturn(report);
        attachment.setId(61L); attachment.setTenantId(3L); attachment.setReportVersionId(51L);
        attachment.setAttachmentSequence(1); attachment.setFileArtifactId(71L); attachment.setFileVersionNo(3);
        attachment.setReferenceKey(KEY); attachment.setArtifactVersion(4); attachment.setReferenceVersion(5);
        attachment.setAvailabilityVersion(6); attachment.setScopeVersion(1L); attachment.setFileHash("a".repeat(64));
        when(attachments.selectByReportVersion(51L)).thenReturn(List.of(attachment));
        fileFact("AVAILABLE", 3);
    }

    @AfterEach
    void clean() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }

    @Test void automaticAssociationsFilterAcceptanceTypeAndDoNotRequireABrowserActor() {
        SecurityContextHolder.clearContext();
        var initial = new AcceptanceActivityDO(); initial.setId(41L); initial.setTenantId(3L); initial.setProjectId(100L);
        initial.setAcceptanceType("PRELIMINARY"); initial.setVersion(1L);
        when(activities.selectByProjectScope(any())).thenReturn(List.of(activity, initial));
        var initialContext = new AssociationContext(3L, 100L, "PROJECT_ACCEPTANCE", "{\"acceptanceType\":\"PRELIMINARY\"}");
        assertEquals(List.of("41"), provider.associationCandidates(initialContext, null, 100).stream().map(AssociationCandidate::objectId).toList());
        assertTrue(provider.associationCandidates(initialContext, "41", 100).isEmpty());
        assertEquals(List.of("42"), provider.associationCandidates(new AssociationContext(3L, 100L, "PROJECT_ACCEPTANCE",
                "{\"acceptanceType\":\"FINAL\"}"), null, 100).stream().map(AssociationCandidate::objectId).toList());
        assertThrows(RuntimeException.class, () -> provider.associationCandidates(new AssociationContext(4L, 100L, "PROJECT_ACCEPTANCE", "{}"), null, 100));
        assertThrows(RuntimeException.class, () -> provider.associationCandidates(new AssociationContext(3L, 100L, "PROJECT_ACCEPTANCE", "{\"acceptanceType\":\"UNKNOWN\"}"), null, 100));
        verifyNoInteractions(permissions, executions, reportResults);
    }

    @Test void unattendedCompletionRevalidatesTheExecutionAndExactCurrentReportBeforePassing() {
        SecurityContextHolder.clearContext(); report.setConclusionCode("PASS");
        var execution = new cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectTaskExecutionContext(
                100L, 1L, 200L, 1, 400L, 1, 500L, 600L, 1, 1, 700L, 1, true, report.getEffectiveFrom());
        when(executions.lockAndRevalidate(execution)).thenReturn(execution);
        var type = cn.iocoder.yudao.module.pms.acceptance.service.acceptancereport.AcceptanceReportBusinessResultSource.TYPE;
        var result = new cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource.Result(
                3L, 100L, type, "42", "51", "2", "7", cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource.Validity.CURRENT,
                report.getEffectiveFrom());
        when(reportResults.lockAndInspect(any())).thenReturn(cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource.Observation.available(result));
        var fact = provider.lockCompletionFact(new CompletionContext(3L, execution), "42");
        assertTrue(fact.handlingCompleted()); assertTrue(fact.completionFacts().get("FINAL_ACCEPTANCE_PASSED"));
        assertFalse(fact.completionFacts().get("PRELIMINARY_ACCEPTANCE_PASSED"));
        when(reportResults.lockAndInspect(any())).thenReturn(cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource.Observation.absent(
                cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource.Status.UNAVAILABLE, "FILE_INVALID"));
        var invalid = provider.lockCompletionFact(new CompletionContext(3L, execution), "42");
        assertFalse(invalid.handlingCompleted()); assertFalse(invalid.completionFacts().get("FINAL_ACCEPTANCE_PASSED"));
        assertNotEquals(fact.factVersion(), invalid.factVersion());
        when(executions.lockAndRevalidate(execution)).thenThrow(new IllegalArgumentException("STALE_EXECUTION"));
        assertThrows(RuntimeException.class, () -> provider.lockCompletionFact(new CompletionContext(3L, execution), "42"));
        verifyNoInteractions(permissions);
    }

    @Test
    void completionFactCatalogIsDeploymentMetadataWithoutBusinessAccess() {
        clean();
        assertEquals(Set.of("REPORT_EFFECTIVE", "PRELIMINARY_ACCEPTANCE_PASSED", "FINAL_ACCEPTANCE_PASSED"), provider.completionFactCodes());
        verifyNoInteractions(activities, reports, attachments, deliverables, sources, sourceAttachments,
                files, scope, permissions);
    }

    @Test
    void onlyAnEffectivePassingReportSuppliesTheMatchingAcceptanceType() {
        assertEquals("ACC", provider.ownerContext()); assertEquals("ACCEPTANCE", provider.objectType());
        for (String conclusion : List.of("FAIL", "RECTIFICATION", "UNKNOWN", "PASS")) {
            report.setConclusionCode(conclusion);
            var fact = provider.inspect(context, "42");
            assertEquals(Map.of("REPORT_EFFECTIVE", true, "PRELIMINARY_ACCEPTANCE_PASSED", false,
                    "FINAL_ACCEPTANCE_PASSED", "PASS".equals(conclusion)), fact.completionFacts());
            assertFalse(fact.completionFacts().containsKey("ACCEPTANCE_PASSED"));
            assertTrue(fact.artifacts().isEmpty());
        }
        activity.setActivityStatus("COMPLETED");
        assertFalse(provider.inspect(context, "42").completionFacts().containsKey("ACCEPTANCE_PASSED"));
        activity.setAcceptanceType("PRELIMINARY");
        var initial = provider.inspect(context, "42");
        assertTrue(initial.completionFacts().get("PRELIMINARY_ACCEPTANCE_PASSED"));
        assertFalse(initial.completionFacts().get("FINAL_ACCEPTANCE_PASSED"));
    }

    @Test
    void exactArchivedCurrentSourceReturnsImmutableFileTupleAndSourceIdentity() {
        archived();
        deliverable.setDeliverableCode("RENAMED_CUSTOM_REPORT");
        var fact = provider.inspect(context, "42");
        assertEquals(1, fact.artifacts().size());
        var artifact = fact.artifacts().getFirst();
        assertEquals("71", artifact.artifactId()); assertEquals(3, artifact.versionNo());
        assertEquals(KEY, artifact.referenceKey()); assertEquals("真实报告.pdf", artifact.displayName());
        assertEquals("ACC_REPORT:51:2:SOURCE:81", artifact.sourceVersion());
        assertEquals(fact, provider.lockAndRevalidate(context, "42", fact.factVersion()));
        verify(files).lockAndRevalidateReferenceSets(argThat(q -> q.collections().size() == 1
                && q.collections().getFirst().key().equals(setKey)
                && q.collections().getFirst().expectedActiveFacts().getFirst().versionNo() == 3));
    }

    @Test
    void archiveMustBelongToTheActivitysFrozenDeliverableNotJustTheSameProject() {
        archived(); activity.setDeliverableId(999L);
        assertThrows(RuntimeException.class,()->provider.inspect(context,"42"));
    }

    @Test
    void revalidationUsesCompleteObservedFileFactsAndKeepsTheFrozenAttachmentIdentity() {
        var observed = files.inspectReferenceSets(new FileReferenceSetCollectionQuery(List.of(setKey), "READ"));
        when(files.lockAndRevalidateReferenceSets(any())).thenAnswer(call -> {
            var query = call.<FileReferenceSetCollectionRevalidationQuery>getArgument(0);
            assertEquals(observed.getFirst().activeFacts(), query.collections().getFirst().expectedActiveFacts());
            return observed;
        });
        var fact = provider.inspect(context, "42");
        assertEquals(fact, provider.lockAndRevalidate(context, "42", fact.factVersion()));
        attachment.setFileHash("b".repeat(64));
        var changed = provider.inspect(context, "42");
        assertFalse(changed.completionFacts().get("REPORT_EFFECTIVE"));
        assertThrows(ServiceException.class, () -> provider.lockAndRevalidate(context, "42", fact.factVersion()));
    }

    @Test
    void pendingSupersededAndWrongCurrentSourcesNeverBecomeArtifacts() {
        archived();
        source.setArchiveStatus("PENDING_COMPENSATION");
        assertTrue(provider.inspect(context, "42").artifacts().isEmpty());
        source.setArchiveStatus("ARCHIVED"); source.setRelationStatus("SUPERSEDED");
        assertTrue(provider.inspect(context, "42").artifacts().isEmpty());
        source.setRelationStatus("CURRENT"); deliverable.setCurrentSourceVersionId(999L);
        assertTrue(provider.inspect(context, "42").artifacts().isEmpty());
    }

    @Test
    void archiveFromDifferentProjectOrReportVersionIsRejected() {
        archived(); deliverable.setProjectId(101L);
        assertThrows(ServiceException.class, () -> provider.inspect(context, "42"));
        deliverable.setProjectId(100L); source.setSourceVersion(1);
        assertThrows(ServiceException.class, () -> provider.inspect(context, "42"));
    }

    @Test
    void mismatchedArchiveAttachmentOrRawUrlCannotBecomeAnArtifact() {
        archived(); sourceAttachment.setFileVersionNo(2);
        assertThrows(ServiceException.class, () -> provider.inspect(context, "42"));
        sourceAttachment.setFileVersionNo(3); sourceAttachment.setReferenceKey("https://example/report.pdf");
        assertThrows(ServiceException.class, () -> provider.inspect(context, "42"));
        sourceAttachment.setReferenceKey(KEY); sourceAttachment.setFileHash("b".repeat(64));
        assertThrows(ServiceException.class, () -> provider.inspect(context, "42"));
    }

    @Test
    void unavailableOrDifferentFileVersionMakesReportIneffectiveAndProducesNoArtifact() {
        archived(); fileFact("INVALID", 3);
        assertEquals(Map.of("REPORT_EFFECTIVE", false, "PRELIMINARY_ACCEPTANCE_PASSED", false,
                "FINAL_ACCEPTANCE_PASSED", false), provider.inspect(context, "42").completionFacts());
        assertTrue(provider.inspect(context, "42").artifacts().isEmpty());
        fileFact("AVAILABLE", 4);
        assertFalse(provider.inspect(context, "42").completionFacts().get("REPORT_EFFECTIVE"));
    }

    @Test
    void revokedAndSupersededReportsDoNotRestoreHistoryEvenOnCompletedActivity() {
        archived(); var version = provider.inspect(context, "42").factVersion();
        activity.setActivityStatus("COMPLETED");
        for (String status : List.of("REVOKED", "SUPERSEDED", "DRAFT")) {
            report.setReportStatus(status);
            var fact = provider.inspect(context, "42");
            assertFalse(fact.completionFacts().get("REPORT_EFFECTIVE")); assertTrue(fact.artifacts().isEmpty());
            assertThrows(ServiceException.class, () -> provider.lockAndRevalidate(context, "42", version));
            assertEquals(status, report.getReportStatus()); assertEquals(7, activity.getVersion());
        }
        activity.setCurrentReportVersionId(null);
        assertFalse(provider.inspect(context, "42").completionFacts().get("REPORT_EFFECTIVE"));
        assertEquals("COMPLETED", activity.getActivityStatus());
    }

    @Test
    void missingRequiredEvidenceOrClosedEffectiveIntervalIsNotEffective() {
        report.setConclusionCode(" ");
        assertFalse(provider.inspect(context, "42").completionFacts().get("REPORT_EFFECTIVE"));
        report.setConclusionCode("FAIL"); report.setEffectiveTo(LocalDateTime.now());
        assertFalse(provider.inspect(context, "42").completionFacts().get("REPORT_EFFECTIVE"));
        report.setEffectiveTo(null); when(attachments.selectByReportVersion(51L)).thenReturn(List.of());
        assertFalse(provider.inspect(context, "42").completionFacts().get("REPORT_EFFECTIVE"));
    }

    @Test
    void contextActionsUseActualQueryAndEditPermissionsWithoutImplicitCreation() {
        assertEquals(Set.of("QUERY"), provider.inspectContext(context));
        when(permissions.hasAnyPermissions(9L, "pms:acceptance:report:write")).thenReturn(true);
        assertEquals(Set.of("QUERY"), provider.inspectContext(context));
        when(scope.resolveCurrent(new ProjectCurrentScopeQuery(3L, 9L, 100L, ProjectScopeApi.ACTION_EDIT)))
                .thenReturn(new ProjectScopeResult(100L, 1L, Set.of(100L), Set.of()));
        assertEquals(Set.of("QUERY", "MANAGE"), provider.inspectContext(context),
                () -> "permissions=" + mockingDetails(permissions).getInvocations()
                        + "; scope=" + mockingDetails(scope).getInvocations());
        verifyNoInteractions(activities, reports, attachments, sources, files);
        assertEquals(Set.of("QUERY", "MANAGE", "UPDATE", "REVOKE", "FILE_WRITE"), provider.inspect(context, "42").allowedActions());
        when(scope.resolveCurrent(new ProjectCurrentScopeQuery(3L, 9L, 100L, ProjectScopeApi.ACTION_MANAGE)))
                .thenReturn(new ProjectScopeResult(100L, 1L, Set.of(100L), Set.of()));
        assertEquals(Set.of("QUERY", "MANAGE", "UPDATE", "REVOKE", "LINK", "UNLINK", "FILE_WRITE"), provider.inspect(context, "42").allowedActions());
        activity.setActivityStatus("COMPLETED");
        assertEquals(Set.of("QUERY", "MANAGE", "LINK", "UNLINK"), provider.inspect(context, "42").allowedActions());
        activity.setActivityStatus("PENDING"); activity.setCurrentReportVersionId(null);
        assertEquals(Set.of("QUERY", "MANAGE", "UPDATE", "LINK", "UNLINK", "FILE_WRITE"), provider.inspect(context, "42").allowedActions());
        assertFalse(provider.inspect(context, "42").allowedActions().contains("PUBLISH"));
        when(permissions.hasAnyPermissions(9L, "pms:acceptance:report:write")).thenReturn(false);
        assertEquals(Set.of("QUERY"), provider.inspect(context, "42").allowedActions());
    }

    @Test
    void forgedTenantActorAndMissingPermissionFailBeforeAnyOwnerRead() {
        assertThrows(ServiceException.class, () -> provider.inspect(new Context(4L, 9L, 100L, 200L, "x"), "42"));
        assertThrows(ServiceException.class, () -> provider.candidates(new Context(3L, 8L, 100L, 200L, "x")));
        when(permissions.hasAnyPermissions(9L, "pms:acceptance:report:query")).thenReturn(false);
        assertThrows(ServiceException.class, () -> provider.inspectContext(context));
        assertThrows(ServiceException.class, () -> provider.lockAndRevalidate(context, "42", "x"));
        verifyNoInteractions(activities, reports, files);
    }

    @Test
    void emptyScopeNeverGrantsPlaceholderAccess() {
        when(scope.resolveCurrent(new ProjectCurrentScopeQuery(3L, 9L, 100L, ProjectScopeApi.ACTION_VIEW)))
                .thenReturn(new ProjectScopeResult(100L, 1L, Set.of(), Set.of(100L)));
        assertThrows(ServiceException.class, () -> provider.candidates(context));
        verifyNoInteractions(activities, reports, files);
    }

    @Test
    void actualQueryServiceRejectsForeignTenantAndProviderRejectsForeignProject() {
        activity.setTenantId(4L);
        assertThrows(ServiceException.class, () -> provider.inspect(context, "42"));
        activity.setTenantId(3L); activity.setProjectId(101L);
        when(scope.resolveCurrent(new ProjectCurrentScopeQuery(3L, 9L, 101L, ProjectScopeApi.ACTION_VIEW)))
                .thenReturn(new ProjectScopeResult(100L, 1L, Set.of(100L, 101L), Set.of()));
        assertThrows(ServiceException.class, () -> provider.inspect(context, "42"));
        verifyNoInteractions(reports, files);
    }

    @Test
    void lockedActivityAndReportTenantAndIdentityAreRechecked() {
        var fact = provider.inspect(context, "42");
        var foreign = new AcceptanceActivityDO(); foreign.setTenantId(4L);
        when(activities.selectByIdForUpdate(any())).thenReturn(foreign);
        assertThrows(ServiceException.class, () -> provider.lockAndRevalidate(context, "42", fact.factVersion()));
        when(activities.selectByIdForUpdate(any())).thenReturn(activity);
        report.setAcceptanceId(43L);
        assertThrows(ServiceException.class, () -> provider.lockAndRevalidate(context, "42", fact.factVersion()));
        report.setAcceptanceId(42L); report.setTenantId(4L);
        assertThrows(ServiceException.class, () -> provider.inspect(context, "42"));
    }

    @Test
    void candidatesReuseSameProjectQueryAndEmptyDoesNotCreateOrWrite() {
        var query = new AcceptanceActivityScopeQuery(3L, Set.of(100L));
        when(activities.selectByProjectScope(query)).thenReturn(List.of());
        assertTrue(provider.candidates(context).isEmpty());
        verify(activities).selectByProjectScope(query); verifyNoMoreInteractions(activities);
        verifyNoInteractions(reports, attachments, sources, sourceAttachments, deliverables, files);
    }

    @Test
    void candidatesReturnExistingActivityFromSameProjectEvenWhenItsOriginalTaskDiffers() {
        when(activities.selectByProjectScope(new AcceptanceActivityScopeQuery(3L, Set.of(100L))))
                .thenReturn(List.of(activity));
        var candidates = provider.candidates(context);
        assertEquals(1, candidates.size()); assertEquals("42", candidates.getFirst().objectId());
        assertEquals(300L, activity.getProjectTaskId()); // reference only, never rebind the Owner's original task
    }

    @Test
    void exactFileMetadataChangesAndIncompleteCollectionsNeverBecomeEffective() {
        attachment.setArtifactVersion(99);
        assertFalse(provider.inspect(context, "42").completionFacts().get("REPORT_EFFECTIVE"));
        attachment.setArtifactVersion(4); attachment.setScopeVersion(2L);
        assertFalse(provider.inspect(context, "42").completionFacts().get("REPORT_EFFECTIVE"));
        attachment.setScopeVersion(1L); attachment.setFileHash("b".repeat(64));
        assertFalse(provider.inspect(context, "42").completionFacts().get("REPORT_EFFECTIVE"));
        attachment.setFileHash("a".repeat(64));
        when(files.inspectReferenceSets(any())).thenReturn(List.of(new FileReferenceSetFact(setKey, 1L, List.of())));
        assertFalse(provider.inspect(context, "42").completionFacts().get("REPORT_EFFECTIVE"));
    }

    @Test
    void deletedLockedObjectOrUnknownActivityStateNeverBecomesEvidence() {
        var fact = provider.inspect(context, "42"); activity.setDeleted(true);
        assertThrows(ServiceException.class, () -> provider.lockAndRevalidate(context, "42", fact.factVersion()));
        activity.setDeleted(false); activity.setActivityStatus("UNKNOWN");
        assertThrows(ServiceException.class, () -> provider.inspect(context, "42"));
        activity.setActivityStatus("PENDING"); activity.setVersion(null);
        assertThrows(ServiceException.class, () -> provider.inspect(context, "42"));
    }

    @Test
    void inspectionAndLockedRevalidationOnlyUseReadOperations() {
        archived();
        var fact = provider.inspect(context, "42");
        provider.lockAndRevalidate(context, "42", fact.factVersion());
        for (Object dependency : List.of(activities, reports, attachments, sources, sourceAttachments, deliverables, files)) {
            assertTrue(mockingDetails(dependency).getInvocations().stream().allMatch(invocation -> {
                String method = invocation.getMethod().getName();
                return method.startsWith("select") || method.startsWith("inspect") || method.startsWith("lockAndRevalidate");
            }));
        }
        assertEquals("PENDING", activity.getActivityStatus()); assertEquals("EFFECTIVE", report.getReportStatus());
        assertEquals("CURRENT", source.getRelationStatus()); assertEquals("ARCHIVED", source.getArchiveStatus());
    }

    @Test
    void optimisticVersionIncludesArchiveAndFileChangesAndNullNeverMatches() {
        var old = provider.inspect(context, "42").factVersion();
        archived();
        assertThrows(ServiceException.class, () -> provider.lockAndRevalidate(context, "42", old));
        assertThrows(ServiceException.class, () -> provider.lockAndRevalidate(context, "42", null));
        var archived = provider.inspect(context, "42").factVersion();
        when(files.lockAndRevalidateReferenceSets(any())).thenThrow(new IllegalStateException("PLT unavailable"));
        assertThrows(IllegalStateException.class, () -> provider.lockAndRevalidate(context, "42", archived));
    }

    @Test
    void mandatoryProxyRejectsMissingTransactionBeforeAnyReads() {
        AbstractPlatformTransactionManager manager = new AbstractPlatformTransactionManager() {
            @Override protected Object doGetTransaction() { return new Object(); }
            @Override protected void doBegin(Object tx, org.springframework.transaction.TransactionDefinition definition) { fail("must not begin"); }
            @Override protected void doCommit(DefaultTransactionStatus status) { fail("must not commit"); }
            @Override protected void doRollback(DefaultTransactionStatus status) { fail("must not rollback"); }
        };
        ProxyFactory factory = new ProxyFactory(provider); factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        var proxy = (AcceptanceTaskBusinessObjectProvider) factory.getProxy();
        assertThrows(IllegalTransactionStateException.class, () -> proxy.lockAndRevalidate(context, "42", "unknown"));
        verifyNoInteractions(activities, scope, files, permissions);
    }

    private void fileFact(String availability, int version) {
        var fact = new FileArtifactVersionFact(71L, version, KEY, "ACCEPTANCE_REPORT_ATTACHMENT", "真实报告.pdf",
                100L, "application/pdf", "a".repeat(64), availability, "ACTIVE", new FileFactVersion(4, 5, 6), 1L);
        var set = new FileReferenceSetFact(setKey, 1L, List.of(fact));
        when(files.inspectReferenceSets(any())).thenReturn(List.of(set));
        when(files.lockAndRevalidateReferenceSets(any())).thenReturn(List.of(set));
    }

    private void archived() {
        source.setId(81L); source.setTenantId(3L); source.setDeliverableId(91L);
        source.setSourceRequirementId("ACC-03@V1"); source.setSourceObjectType("AcceptanceReportVersion");
        source.setSourceObjectId(51L); source.setSourceVersion(2); source.setRelationStatus("CURRENT");
        source.setArchiveStatus("ARCHIVED"); source.setArchiveTime(LocalDateTime.of(2026, 9, 10, 11, 0));
        when(sources.selectByReportVersionId(51L)).thenReturn(source);
        when(sources.selectByIdForUpdate(new DeliverableSourceIdLockQuery(3L, 81L))).thenReturn(source);
        deliverable.setId(91L); deliverable.setTenantId(3L); deliverable.setProjectId(100L);
        deliverable.setDeliverableCode("D-FINAL-REPORT"); deliverable.setCurrentSourceVersionId(81L); deliverable.setArchiveStatus("ARCHIVED");
        when(deliverables.selectById(91L)).thenReturn(deliverable);
        when(deliverables.selectByIdForUpdate(new ProjectDeliverableIdLockQuery(3L, 91L))).thenReturn(deliverable);
        sourceAttachment.setId(101L); sourceAttachment.setTenantId(3L); sourceAttachment.setDeliverableSourceVersionId(81L);
        sourceAttachment.setAttachmentSequence(1); sourceAttachment.setFileArtifactId(71L); sourceAttachment.setFileVersionNo(3);
        sourceAttachment.setReferenceKey(KEY); sourceAttachment.setArtifactVersion(4); sourceAttachment.setReferenceVersion(5);
        sourceAttachment.setAvailabilityVersion(6); sourceAttachment.setScopeVersion(1L); sourceAttachment.setFileHash("a".repeat(64));
        when(sourceAttachments.selectBySourceVersion(81L)).thenReturn(List.of(sourceAttachment));
    }
}
