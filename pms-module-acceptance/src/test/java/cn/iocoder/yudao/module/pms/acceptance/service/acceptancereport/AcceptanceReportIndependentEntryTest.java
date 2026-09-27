package cn.iocoder.yudao.module.pms.acceptance.service.acceptancereport;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.*;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptancereport.*;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual access provider, adapter and Owner writer; only persistence/file/idempotency infrastructure is mocked. */
class AcceptanceReportIndependentEntryTest {
    private static final String PREFIX = "ACC.ACCEPTANCE_REPORT.";
    private static ValidatorFactory validation;
    private final AcceptanceActivityMapper activities = mock(AcceptanceActivityMapper.class);
    private final AcceptanceReportVersionMapper reports = mock(AcceptanceReportVersionMapper.class);
    private final AcceptanceReportAttachmentMapper attachments = mock(AcceptanceReportAttachmentMapper.class);
    private final FileArtifactApi files = mock(FileArtifactApi.class);
    private final PlatformCommandExecutionApi commands = mock(PlatformCommandExecutionApi.class);
    private final ProjectScopeApi scopes = mock(ProjectScopeApi.class);
    private final PermissionApi permissions = mock(PermissionApi.class);
    private final AcceptanceReportQueryService queries = mock(AcceptanceReportQueryService.class);
    private final cn.iocoder.yudao.module.pms.project.api.acceptance.ProjectAcceptanceContextApi nativeProjects =
            mock(cn.iocoder.yudao.module.pms.project.api.acceptance.ProjectAcceptanceContextApi.class);
    private final AcceptanceActivityDO activity = new AcceptanceActivityDO();
    private final AcceptanceReportVersionDO report = new AcceptanceReportVersionDO();
    private final List<PlatformCommandExecutionApi.SuccessFacts> facts = new ArrayList<>();
    private AcceptanceReportOperationCommandAdapter adapter;
    private AcceptanceReportCommandService owner;
    private MockedStatic<SecurityFrameworkUtils> security;

    @BeforeAll static void startValidation() { validation = Validation.buildDefaultValidatorFactory(); }
    @AfterAll static void stopValidation() { validation.close(); }
    @BeforeEach void setUp() {
        TenantContextHolder.setTenantId(7L);
        security = mockStatic(SecurityFrameworkUtils.class);
        security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(19L);
        activity.setId(100L); activity.setTenantId(7L); activity.setProjectId(80L);
        activity.setProjectTaskId(90L); activity.setDeliverableId(50L);
        activity.setVersion(2L); activity.setActivityStatus("PENDING"); activity.setAcceptanceType("PRELIMINARY");
        report.setId(300L); report.setAcceptanceId(100L); report.setTenantId(7L);
        report.setReportVersionNo(1); report.setReportStatus("DRAFT"); report.setPublisherUserId(19L);
        report.setAcceptanceTime(LocalDateTime.of(2026, 9, 18, 1, 0));
        report.setConclusionCode("PASS"); report.setAcceptorName("验收人");
        when(activities.selectByIdForUpdate(any())).thenReturn(activity);
        when(activities.selectById(100L)).thenReturn(activity);
        when(activities.updateById(any(AcceptanceActivityDO.class))).thenReturn(1);
        when(reports.selectByIdForUpdate(any())).thenReturn(report);
        when(reports.selectById(300L)).thenReturn(report);
        when(reports.selectNextVersionNo(any())).thenReturn(2);
        when(reports.insert(any(AcceptanceReportVersionDO.class))).thenReturn(1);
        when(reports.updateById(any(AcceptanceReportVersionDO.class))).thenReturn(1);
        when(permissions.hasAnyPermissions(eq(19L), any(String.class))).thenReturn(true);
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(80L, 3L, Set.of(80L), Set.of()));
        when(queries.get(eq(100L), any())).thenAnswer(call -> new AcceptanceReportQueryService.ActivityView(
                100L, 80L, 90L, 50L, "PRELIMINARY", activity.getActivityStatus(),
                activity.getCurrentReportVersionId(), activity.getVersion(), null));
        var file = new FileArtifactVersionFact(11L, 2, "7a5d9177-2f67-4bb5-a211-b0b612e72e5f",
                "ACCEPTANCE_REPORT_ATTACHMENT", "report.pdf", 10L, "application/pdf", "a".repeat(64),
                "AVAILABLE", "ACTIVE", new FileFactVersion(3, 4, 5), 8L);
        var fileSet = new FileReferenceSetFact(new FileReferenceSetKey("ACC", "ACCEPTANCE_REPORT_VERSION",
                "300", "ACCEPTANCE_REPORT_ATTACHMENT"), 8L, List.of(file));
        when(files.inspectReferenceSets(any())).thenReturn(List.of(fileSet));
        when(files.lockAndRevalidateReferenceSets(any())).thenReturn(List.of(fileSet));
        var saved = new ArrayList<AcceptanceReportAttachmentDO>();
        when(attachments.insert(any(AcceptanceReportAttachmentDO.class))).thenAnswer(call -> {
            saved.add(call.getArgument(0)); return 1;
        });
        when(attachments.selectByReportVersion(300L)).thenReturn(saved);
        when(commands.execute(any(), any(), any(), any(), any())).thenAnswer(call -> {
            AcceptanceReportCommands.ReportResult result = call.<Supplier<AcceptanceReportCommands.ReportResult>>getArgument(3).get();
            facts.add(call.<Function<AcceptanceReportCommands.ReportResult, PlatformCommandExecutionApi.SuccessFacts>>getArgument(4).apply(result));
            return new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW, result);
        });
        owner = new AcceptanceReportCommandService(activities, reports, attachments, files, commands, scopes, nativeProjects);
        var access = new AcceptanceReportOperationAccessProvider(queries, scopes, permissions);
        adapter = new AcceptanceReportOperationCommandAdapter(of(owner), of(queries), of(access), of(validation.getValidator()));
    }
    @AfterEach void cleanUp() { security.close(); TenantContextHolder.clear(); }

    private void direct(String type) {
        activity.setOriginKind("DIRECT"); activity.setOriginKey("19:native-key");
        activity.setProjectTaskId(null); activity.setExecutionContractId(null); activity.setDeliverableId(null);
        activity.setAcceptanceType(type); activity.setRuleSnapshot(IndependentAcceptancePolicy.SNAPSHOT);
        report.setAcceptanceRuleSnapshot(IndependentAcceptancePolicy.SNAPSHOT);
        var context = new cn.iocoder.yudao.module.pms.project.api.acceptance.ProjectAcceptanceContextApi.Context(80L, 80L, 4L, 3L, "ACTIVE");
        when(nativeProjects.inspect(any())).thenReturn(context);
        when(nativeProjects.lock(any(), eq(4L), eq(3L))).thenReturn(context);
    }

    @Test void independentFinalPassHasNoImplicitPreliminaryDependencyAndKeepsNoFakeDeliveryTarget() {
        direct("FINAL");
        owner.publish(new AcceptanceReportCommands.PublishCommand(100L, 300L, 2L, 1, null, "native-publish", "digest"),
                new AcceptanceReportCommands.Actor(7L, 19L, "native-entry"));
        assertEquals("COMPLETED", activity.getActivityStatus());
        assertNull(activity.getProjectTaskId()); assertNull(activity.getExecutionContractId()); assertNull(activity.getDeliverableId());
        verify(activities, never()).selectByIdentityForUpdate(any());
        assertEquals(List.of("ACC.ProjectAcceptanceReportChanged.v1"), facts.getFirst().businessEvents().stream()
                .map(PlatformCommandExecutionApi.BusinessEvent::eventType).toList());
        var order = inOrder(nativeProjects, activities, files);
        order.verify(nativeProjects).lock(any(), eq(4L), eq(3L));
        order.verify(activities).selectByIdForUpdate(any());
        order.verify(files).lockAndRevalidateReferenceSets(any());
    }

    @Test void failedReportNeverCompletesIndependentAcceptanceAndMissingFilesCannotPublish() {
        direct("PRELIMINARY"); report.setConclusionCode("FAIL");
        owner.publish(new AcceptanceReportCommands.PublishCommand(100L, 300L, 2L, 1, null, "failed-publish", "digest"),
                new AcceptanceReportCommands.Actor(7L, 19L, "native-entry"));
        assertEquals("PENDING", activity.getActivityStatus());
        activity.setVersion(2L); activity.setCurrentReportVersionId(null); report.setReportStatus("DRAFT");
        when(files.inspectReferenceSets(any())).thenReturn(List.of());
        assertThrows(RuntimeException.class, () -> owner.publish(
                new AcceptanceReportCommands.PublishCommand(100L, 300L, 2L, 1, null, "no-file", "digest"),
                new AcceptanceReportCommands.Actor(7L, 19L, "native-entry")));
        assertEquals("DRAFT", report.getReportStatus());
    }

    @Test void completedIndependentAcceptanceCanRevokeCurrentReportWithoutRestoringHistory() {
        direct("FINAL"); activity.setActivityStatus("COMPLETED"); prepare("REVOKE");
        report.setPreviousVersionId(200L);
        owner.revoke(new AcceptanceReportCommands.RevokeCommand(100L, 2L, 300L, 1, "revoke", "digest"),
                new AcceptanceReportCommands.Actor(7L, 19L, "native-entry"));
        assertEquals("PENDING", activity.getActivityStatus()); assertNull(activity.getCurrentReportVersionId());
        assertEquals("REVOKED", report.getReportStatus()); assertEquals(200L, report.getPreviousVersionId());
    }

    @Test void closedProjectAndChangedFrozenReportPolicyBothPreventIndependentPublication() {
        direct("FINAL");
        var closed = new cn.iocoder.yudao.module.pms.project.api.acceptance.ProjectAcceptanceContextApi.Context(80L, 80L, 4L, 3L, "NORMAL_CLOSED");
        when(nativeProjects.lock(any(), eq(4L), eq(3L))).thenReturn(closed);
        var command = new AcceptanceReportCommands.PublishCommand(100L, 300L, 2L, 1, null, "publish", "digest");
        var actor = new AcceptanceReportCommands.Actor(7L, 19L, "native-entry");
        assertThrows(RuntimeException.class, () -> owner.publish(command, actor));
        verify(activities, never()).selectByIdForUpdate(any());
        direct("FINAL"); report.setAcceptanceRuleSnapshot("{}");
        assertThrows(RuntimeException.class, () -> owner.publish(command, actor));
        assertEquals("DRAFT", report.getReportStatus()); assertEquals("PENDING", activity.getActivityStatus());
    }

    @ParameterizedTest @ValueSource(strings = {"CREATE_DRAFT", "UPDATE_DRAFT", "PUBLISH", "REVOKE"})
    void projectAdapterReachesTheOriginalWriterWithoutInventingBusinessCompletion(String action) {
        prepare(action);
        var request = request(action);
        String before = request.input().toString();
        var result = adapter.invoke(PREFIX + action, request);
        assertEquals("100", result.objectId());
        assertEquals(before, request.input().toString());
        assertEquals("PENDING", activity.getActivityStatus());
        if (action.equals("PUBLISH") || action.equals("REVOKE")) {
            assertEquals(1, facts.size());
            assertEquals("PROJECT_OP:" + DigestUtil.sha256Hex(PREFIX + action + ":TASK:90:retry-key"), facts.getFirst().correlationId());
            assertEquals(List.of("AcceptanceReportVersionChanged", "ClosureGateRecheckRequested"),
                    facts.getFirst().businessEvents().stream().map(PlatformCommandExecutionApi.BusinessEvent::eventType).toList());
        } else assertTrue(facts.isEmpty());
    }

    @ParameterizedTest @ValueSource(strings = {"CREATE_DRAFT", "UPDATE_DRAFT", "PUBLISH", "REVOKE"})
    void ordinaryWriterKeepsItsOwnScopeAndDoesNotRequireAnActiveProjectNode(String action) {
        prepare(action);
        var actor = new AcceptanceReportCommands.Actor(7L, 19L, "ordinary-request");
        var result = switch (action) {
            case "CREATE_DRAFT" -> owner.createDraft(new AcceptanceReportCommands.CreateDraftCommand(100L, 2L, content()), actor);
            case "UPDATE_DRAFT" -> owner.updateDraft(new AcceptanceReportCommands.UpdateDraftCommand(100L, 300L, 2L, 1, content()), actor);
            case "PUBLISH" -> owner.publish(new AcceptanceReportCommands.PublishCommand(100L, 300L, 2L, 1, null, "key", "original-digest"), actor);
            case "REVOKE" -> owner.revoke(new AcceptanceReportCommands.RevokeCommand(100L, 2L, 300L, 1, "key", "original-digest"), actor);
            default -> throw new AssertionError(action);
        };
        assertEquals(100L, result.acceptanceId());
        assertEquals("PENDING", activity.getActivityStatus());
        verifyNoInteractions(queries, permissions);
    }

    @ParameterizedTest @ValueSource(strings = {"CREATE_DRAFT", "UPDATE_DRAFT", "PUBLISH", "REVOKE"})
    void removingWritePermissionStillPreventsEveryOwnerMutation(String action) {
        when(permissions.hasAnyPermissions(19L, "pms:acceptance:report:write")).thenReturn(false);
        assertThrows(RuntimeException.class, () -> adapter.invoke(PREFIX + action, request(action)));
        verifyNoInteractions(activities, reports, attachments, files, commands);
    }

    @Test void onlyAuditedReportCommandsDeclareProjectEntryOnlyControl() {
        var policy = new AcceptanceReportOperationProvider().controlScopes();
        assertEquals(Set.of(PREFIX + "CREATE_DRAFT", PREFIX + "UPDATE_DRAFT", PREFIX + "PUBLISH", PREFIX + "REVOKE"), policy.keySet());
        assertTrue(policy.values().stream().allMatch(scope -> scope == ProjectOperationControlScope.PROJECT_ENTRY_ONLY));
        assertThrows(UnsupportedOperationException.class, policy::clear);
    }

    private void prepare(String action) {
        if (action.equals("REVOKE")) {
            report.setReportStatus("EFFECTIVE"); activity.setCurrentReportVersionId(300L);
            when(reports.selectCurrentForUpdate(any())).thenReturn(report);
        }
    }
    private AcceptanceReportCommands.DraftContent content() {
        return new AcceptanceReportCommands.DraftContent(report.getAcceptanceTime(), "PASS", "通过", "验收人");
    }
    private ProjectOperationCommand request(String action) {
        String input = switch (action) {
            case "CREATE_DRAFT" -> "{}";
            case "UPDATE_DRAFT", "PUBLISH" -> "{\"reportVersionId\":300,\"expectedReportVersionNo\":1}";
            case "REVOKE" -> "{\"expectedCurrentReportVersionId\":300,\"expectedCurrentReportVersionNo\":1}";
            default -> throw new AssertionError(action);
        };
        return new ProjectOperationCommand(80L, "TASK", 90L, null, "100", 2L, "ACC:ACCEPTANCE:100:2", JsonUtils.parseTree(input), "retry-key");
    }
    @SuppressWarnings("unchecked") private static <T> ObjectProvider<T> of(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class); when(provider.getObject()).thenReturn(value); return provider;
    }
}
