package cn.iocoder.yudao.module.pms.acceptance.service.acceptance;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.BusinessEvent;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi.TemplateFrozenConvergence;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi.TemplateFrozenMaterialView;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi.TemplateFrozenSubmissionView;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi.TemplateFrozenSubmitOutcome;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi.TemplateFrozenView;
import cn.iocoder.yudao.module.pms.platform.api.file.FileDocumentSourceProvider;
import cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformBusinessEventApi;
import cn.iocoder.yudao.module.pms.project.api.deliverable.ProjectDeliverableRuleApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultChange;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.ProjectBusinessResultEvidenceApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static cn.iocoder.yudao.module.pms.acceptance.service.acceptance.ProjectDeliverableBusinessResultEvidenceProvider.compositeObjectId;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 统一交付件承接编排验证（P06R）：台账在平台 plt_delivery_*，本服务只做权限/来源/并发守卫与判定回填。
 * 覆盖：幂等重放与载荷冲突、计划快照与乐观并发、来源白名单与自动来源守卫、上传锚校验、
 * 数量下限、业务成果精确复核、文档/成果归集 keep-set、收敛判定阶梯与评估事件契约。
 */
class ProjectDeliverableSubmissionServiceTest {

    static final String CONFIG = """
            {"minimumQuantity":1,"allowedSources":["UPLOAD","BUSINESS_RESULT"],
             "automaticSources":["SOL.REQUIREMENT_DOCUMENT","SOL.REQUIREMENT_ANALYSIS.REQUIREMENT_ANALYSIS_COMPLETED"],
             "confirmationRule":{"predicate":"CONSTANT","parameters":{"value":true}}}
            """;

    final PlatformDeliveryRequirementApi platform = mock(PlatformDeliveryRequirementApi.class);
    final ProjectDeliverableRuleApi rules = mock(ProjectDeliverableRuleApi.class);
    final ProjectDeliverableAccess access = mock(ProjectDeliverableAccess.class);
    final FileEvidenceApi fileEvidence = mock(FileEvidenceApi.class);
    final ProjectBusinessResultEvidenceApi results = mock(ProjectBusinessResultEvidenceApi.class);
    final PlatformBusinessEventApi outbox = mock(PlatformBusinessEventApi.class);
    final ProjectDeliverableOwnerSources ownerSources = mock(ProjectDeliverableOwnerSources.class);
    final ProjectDocumentSourceRegistry documentSources = mock(ProjectDocumentSourceRegistry.class);
    final ProjectDeliverableRequirementResolver resolver =
            new ProjectDeliverableRequirementResolver(platform, rules, ownerSources, documentSources, fileEvidence);
    final ProjectDeliverableSubmissionService service = new ProjectDeliverableSubmissionService(
            platform, rules, access, fileEvidence, results, outbox, ownerSources, resolver);

    TemplateFrozenView view;
    ProjectDeliverableRuleApi.Context context;

    @BeforeEach
    void setup() {
        TenantContextHolder.setTenantId(7L);
        view = view("OPEN", 0, 1);
        context = new ProjectDeliverableRuleApi.Context(9L, 15L, "ACTIVE", 11L, "S1", "T1",
                JsonUtils.parseTree(CONFIG));
        when(rules.read(9L, "D1")).thenReturn(context);
        when(rules.lock(9L, "D1")).thenReturn(context);
        when(rules.evaluate(9L, "D1")).thenReturn(
                new ProjectDeliverableRuleApi.Decision(true, "DELIVERABLE_RULE_SATISFIED", "{}"));
        when(access.require(any(), anyBoolean(), anyBoolean())).thenReturn(11L);
        when(platform.findById(31L)).thenReturn(Optional.of(view));
        when(platform.lockById(31L)).thenReturn(Optional.of(view));
        when(platform.findSubmissionByRequestKey(eq(31L), anyString())).thenReturn(Optional.empty());
        when(platform.findCurrentSubmission(31L)).thenReturn(Optional.empty());
        when(platform.submitTemplateFrozen(any())).thenReturn(new TemplateFrozenSubmitOutcome(1001L, false, "SATISFIED", null));
        when(platform.registerTemplateFrozenFile(anyLong(), anyLong(), any(), anyString())).thenReturn(9001L);
        when(platform.registerTemplateFrozenBusinessResult(anyLong(), anyString(), anyString(), any(), any())).thenReturn(9002L);
        when(platform.registerTemplateFrozenDocument(anyLong(), anyString(), anyLong(), any())).thenReturn(9003L);
        when(platform.updateSubmissionDecision(anyLong(), anyString())).thenReturn(true);
        when(platform.listMaterials(31L)).thenReturn(List.of());
        when(ownerSources.ownerType(any())).thenReturn(null);
        // 统一上传锚默认可用（各用例按引用号精确覆写）。
        when(fileEvidence.inspectDocument(anyLong(), anyLong())).thenAnswer(
                i -> unifiedDocument(i.getArgument(1), true));
    }

    @AfterEach
    void cleanup() {
        TenantContextHolder.clear();
    }

    // ———— 夹具 ————

    static TemplateFrozenView view(String status, int version, int minimumQuantity) {
        return new TemplateFrozenView(31L, 9L, "D1", "施工材料", "S1", "T1", 15L, null, true,
                minimumQuantity, null, status, CONFIG, version);
    }

    static TemplateFrozenSubmissionView submission(Long id, String sourceType, String payloadJson,
                                                   String decisionJson, List<Long> materialIds) {
        return new TemplateFrozenSubmissionView(id, 31L, "k" + id, sourceType, "CURRENT",
                payloadJson, decisionJson, materialIds, LocalDateTime.parse("2026-09-20T12:00:00"));
    }

    static TemplateFrozenMaterialView fileMaterial(long id, Long referenceId, String businessObjectType, String status) {
        return new TemplateFrozenMaterialView(id, 31L, "FILE", referenceId, referenceId * 10, 1,
                "a".repeat(64), "proof.pdf", businessObjectType, null, null, status, "ARCHIVED", null, 0);
    }

    /** 统一上传锚：PLT/DELIVERY_MATERIAL/ACC:project_deliverable:{projectId}，purposeCode=交付件编码。 */
    static FileEvidenceApi.Document unifiedDocument(long referenceId, boolean available) {
        return new FileEvidenceApi.Document(referenceId, "PLT", "DELIVERY_MATERIAL", "ACC:project_deliverable:9",
                "D1", "plt/ref/" + referenceId, referenceId * 10, 1, "a".repeat(64), "proof.pdf", available);
    }

    static FileEvidenceApi.Document legacyDocument(long referenceId) {
        return new FileEvidenceApi.Document(referenceId, "ACC", "PROJECT_DELIVERABLE", "31",
                "PROJECT_DELIVERABLE_DOCUMENT", "acc/ref/" + referenceId, referenceId * 10, 1,
                "b".repeat(64), "legacy.pdf", true);
    }

    static FileEvidenceApi.Document collectedDocument(long referenceId, boolean available) {
        return new FileEvidenceApi.Document(referenceId, "SOL", "REQUIREMENT_DOCUMENT", "81",
                "FORM_FIELD_ATTACHMENT/report", "slot2", referenceId * 10, 1, "c".repeat(64),
                "requirement.pdf", available);
    }

    ProjectDeliverableSubmissionService.Submission upload(Long expectedVersion, Long... referenceIds) {
        return new ProjectDeliverableSubmissionService.Submission(15L, expectedVersion, "UPLOAD",
                Arrays.stream(referenceIds)
                        .map(referenceId -> new ProjectDeliverableSubmissionService.FileSelection(referenceId))
                        .toList(), null);
    }

    FileDocumentSourceProvider.Scope scope(long projectId) {
        return new FileDocumentSourceProvider.Scope(projectId, "SOL.REQUIREMENT_DOCUMENT");
    }

    // ———— 手工提交：幂等、并发、守卫 ————

    @Test
    void uploadSatisfiesReplaysByKeyAndFreezesConfirmationEvidence() {
        var submitted = service.submit(9L, 31L, "request1", upload(0L, 50L));
        assertEquals("ACCEPTED", submitted.status());
        assertEquals(1001L, submitted.submissionId());
        assertEquals(0L, submitted.version());
        assertTrue(submitted.evaluation().satisfied());
        verify(platform).registerTemplateFrozenFile(31L, 50L, null,
                PlatformDeliveryRequirementApi.MATERIAL_SOURCE_UPLOAD);
        var decisionCaptor = ArgumentCaptor.forClass(String.class);
        verify(platform).updateSubmissionDecision(eq(1001L), decisionCaptor.capture());
        assertTrue(decisionCaptor.getValue().contains("DELIVERABLE_RULE_SATISFIED"));
        verify(outbox, times(1)).append(anyString(), anyString(), any(BusinessEvent.class));

        var commandCaptor = ArgumentCaptor.forClass(PlatformDeliveryRequirementApi.TemplateFrozenSubmitCommand.class);
        verify(platform).submitTemplateFrozen(commandCaptor.capture());
        when(platform.findSubmissionByRequestKey(31L, "request1")).thenReturn(Optional.of(
                submission(1001L, "UPLOAD", commandCaptor.getValue().requestPayloadJson(),
                        decisionCaptor.getValue(), List.of(9001L))));
        // 首次提交后平台账本已翻转要求状态；重放回执按当前要求状态给出（不再冻结原回执）。
        var satisfiedView = view("SATISFIED", 1, 1);
        when(platform.findById(31L)).thenReturn(Optional.of(satisfiedView));
        when(platform.lockById(31L)).thenReturn(Optional.of(satisfiedView));
        var replayed = service.submit(9L, 31L, "request1", upload(0L, 50L));
        assertEquals(submitted.status(), replayed.status());
        assertTrue(replayed.evaluation().satisfied());
        verify(platform, times(1)).submitTemplateFrozen(any());
        verify(outbox, times(1)).append(anyString(), anyString(), any(BusinessEvent.class));
    }

    @Test
    void sameKeyDifferentIntentFailsWithoutTouchingLedger() {
        var first = upload(0L, 50L);
        when(platform.findSubmissionByRequestKey(31L, "request1")).thenReturn(Optional.of(
                submission(1001L, "UPLOAD", JsonUtils.toJsonString(first), null, List.of(9001L))));
        var conflict = assertThrows(RuntimeException.class,
                () -> service.submit(9L, 31L, "request1", upload(0L, 51L)));
        assertTrue(conflict.getMessage().contains("同一提交标识不能用于不同材料"));
        verify(platform, never()).submitTemplateFrozen(any());
        verify(platform, never()).registerTemplateFrozenFile(anyLong(), anyLong(), any(), anyString());
    }

    @Test
    void staleVersionOrPlanSnapshotRejectsBeforeCapture() {
        assertTrue(assertThrows(RuntimeException.class, () -> service.submit(9L, 31L, "stale", upload(1L, 50L)))
                .getMessage().contains("交付件或项目计划已变化"));
        assertTrue(assertThrows(RuntimeException.class, () -> service.submit(9L, 31L, "stale-plan",
                new ProjectDeliverableSubmissionService.Submission(16L, 0L, "UPLOAD",
                        List.of(new ProjectDeliverableSubmissionService.FileSelection(50L)), null)))
                .getMessage().contains("交付件或项目计划已变化"));
        verify(platform, never()).submitTemplateFrozen(any());
        verify(platform, never()).registerTemplateFrozenFile(anyLong(), anyLong(), any(), anyString());
    }

    @Test
    void disallowedSourceAndAutomaticOwnerTargetsAreRejected() {
        var restricted = new ProjectDeliverableRuleApi.Context(9L, 15L, "ACTIVE", 11L, "S1", "T1",
                JsonUtils.parseTree(CONFIG.replace("\"UPLOAD\",\"BUSINESS_RESULT\"", "\"BUSINESS_RESULT\"")));
        when(rules.lock(9L, "D1")).thenReturn(restricted);
        assertTrue(assertThrows(RuntimeException.class, () -> service.submit(9L, 31L, "r1", upload(0L, 50L)))
                .getMessage().contains("模板未允许此交付件来源"));

        when(rules.lock(9L, "D1")).thenReturn(context);
        when(ownerSources.ownerType(any())).thenReturn("ACCEPTANCE_REPORT");
        assertTrue(assertThrows(RuntimeException.class, () -> service.submit(9L, 31L, "r2", upload(0L, 50L)))
                .getMessage().contains("该交付件由来源业务自动关联"));

        when(ownerSources.ownerType(any())).thenReturn(null);
        when(platform.findCurrentSubmission(31L)).thenReturn(Optional.of(
                submission(1002L, PlatformDeliveryRequirementApi.SOURCE_AUTO_PROJECTION, "{}", null, List.of(9002L))));
        assertTrue(assertThrows(RuntimeException.class, () -> service.submit(9L, 31L, "r3", upload(0L, 50L)))
                .getMessage().contains("该交付件已有业务来源"));
        verify(platform, never()).submitTemplateFrozen(any());
    }

    @Test
    void foreignProjectOrMissingRequirementIsRejected() {
        assertTrue(assertThrows(RuntimeException.class, () -> service.submit(10L, 31L, "x", upload(0L, 50L)))
                .getMessage().contains("项目交付件不存在"));
        when(platform.findById(31L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> service.submit(9L, 31L, "missing", upload(0L, 50L)));
        verify(platform, never()).submitTemplateFrozen(any());
    }

    // ———— 上传捕获：可用性、锚归属、重复与数量下限 ————

    @Test
    void uploadCaptureValidatesAvailabilityAnchorAndDuplicates() {
        when(fileEvidence.inspectDocument(7L, 50L)).thenReturn(unifiedDocument(50L, false));
        assertTrue(assertThrows(RuntimeException.class, () -> service.submit(9L, 31L, "u1", upload(0L, 50L)))
                .getMessage().contains("文件引用不存在或不可用: 50"));

        when(fileEvidence.inspectDocument(7L, 50L)).thenReturn(null);
        assertThrows(RuntimeException.class, () -> service.submit(9L, 31L, "u2", upload(0L, 50L)));

        when(fileEvidence.inspectDocument(7L, 50L)).thenReturn(new FileEvidenceApi.Document(50L, "PLT",
                "DELIVERY_MATERIAL", "ACC:project_deliverable:9", "D2", "plt/ref/50", 500L, 1,
                "a".repeat(64), "proof.pdf", true));
        assertTrue(assertThrows(RuntimeException.class, () -> service.submit(9L, 31L, "u3", upload(0L, 50L)))
                .getMessage().contains("文件归属与交付件不一致"));

        assertThrows(RuntimeException.class, () -> service.submit(9L, 31L, "u4", upload(0L, 50L, 50L)));
        verify(platform, never()).submitTemplateFrozen(any());

        when(fileEvidence.inspectDocument(7L, 50L)).thenReturn(unifiedDocument(50L, true));
        service.submit(9L, 31L, "u5", upload(0L, 50L));
        verify(platform).registerTemplateFrozenFile(31L, 50L, null,
                PlatformDeliveryRequirementApi.MATERIAL_SOURCE_UPLOAD);
    }

    @Test
    void migratedLegacyAnchorStillUploads() {
        when(fileEvidence.inspectDocument(7L, 51L)).thenReturn(legacyDocument(51L));
        var submitted = service.submit(9L, 31L, "legacy", upload(0L, 51L));
        assertEquals("ACCEPTED", submitted.status());
        verify(platform).registerTemplateFrozenFile(31L, 51L, null,
                PlatformDeliveryRequirementApi.MATERIAL_SOURCE_UPLOAD);
    }

    @Test
    void minimumQuantityFloorRejectsInsufficientMaterials() {
        view = view("OPEN", 0, 2);
        when(platform.findById(31L)).thenReturn(Optional.of(view));
        when(platform.lockById(31L)).thenReturn(Optional.of(view));
        when(fileEvidence.inspectDocument(anyLong(), anyLong())).thenAnswer(
                i -> unifiedDocument(i.getArgument(1), true));
        assertTrue(assertThrows(RuntimeException.class, () -> service.submit(9L, 31L, "one", upload(0L, 50L)))
                .getMessage().contains("有效材料数量不足，至少需要 2 项"));
        var seq = new java.util.concurrent.atomic.AtomicInteger(9000);
        when(platform.registerTemplateFrozenFile(anyLong(), anyLong(), any(), anyString()))
                .thenAnswer(i -> (long) seq.incrementAndGet());
        service.submit(9L, 31L, "two", upload(0L, 50L, 51L));
        verify(platform).submitTemplateFrozen(argThat(command ->
                command.materialIds().size() == 2 && List.of(9001L, 9002L).equals(command.materialIds())));
    }

    // ———— 业务成果提交 ————

    private BusinessResultSource.Type resultType() {
        return new BusinessResultSource.Type("SOL", "REQUIREMENT_ANALYSIS", "REQUIREMENT_ANALYSIS_COMPLETED");
    }

    private BusinessResultSource.Query resultQuery() {
        return new BusinessResultSource.Query(7L, 9L, resultType(), "55", "56");
    }

    private BusinessResultSource.Result formedResult(BusinessResultSource.Validity validity) {
        return new BusinessResultSource.Result(7L, 9L, resultType(), "55", "56", "1", "1", validity,
                LocalDateTime.parse("2026-09-20T12:00:00"));
    }

    @Test
    void businessResultSubmissionRechecksExactIdentity() {
        when(results.types()).thenReturn(List.of(new BusinessResultSource.Descriptor(resultType(), true, true, true)));
        when(results.lockAndInspect(any())).thenReturn(
                BusinessResultSource.Observation.available(formedResult(BusinessResultSource.Validity.CURRENT)));
        var query = resultQuery();
        var request = new ProjectDeliverableSubmissionService.Submission(15L, 0L, "BUSINESS_RESULT", List.of(), query);
        var submitted = service.submit(9L, 31L, "business", request);
        assertEquals("ACCEPTED", submitted.status());
        verify(platform).registerTemplateFrozenBusinessResult(eq(31L), eq("project_business_result"),
                eq(compositeObjectId(resultType(), "55", "56", formedResult(BusinessResultSource.Validity.CURRENT).formedAt())),
                eq(1L), isNull());
        var commandCaptor = ArgumentCaptor.forClass(PlatformDeliveryRequirementApi.TemplateFrozenSubmitCommand.class);
        verify(platform).submitTemplateFrozen(commandCaptor.capture());
        assertEquals("BUSINESS_RESULT", commandCaptor.getValue().sourceType());
        assertEquals(List.of(9002L), commandCaptor.getValue().materialIds());

        when(results.types()).thenReturn(List.of(new BusinessResultSource.Descriptor(
                new BusinessResultSource.Type("SOL", "OTHER", "OTHER_DONE"), true, true, true)));
        assertTrue(assertThrows(RuntimeException.class, () -> service.submit(9L, 31L, "wrong-type", request))
                .getMessage().contains("不支持的业务成果类型"));

        when(results.types()).thenReturn(List.of(new BusinessResultSource.Descriptor(resultType(), true, true, true)));
        when(results.lockAndInspect(any())).thenReturn(
                BusinessResultSource.Observation.available(formedResult(BusinessResultSource.Validity.REVOKED)));
        assertTrue(assertThrows(RuntimeException.class, () -> service.submit(9L, 31L, "revoked", request))
                .getMessage().contains("业务成果尚未形成、已撤销或已被替换"));

        assertTrue(assertThrows(RuntimeException.class, () -> service.submit(9L, 31L, "with-file",
                new ProjectDeliverableSubmissionService.Submission(15L, 0L, "BUSINESS_RESULT",
                        List.of(new ProjectDeliverableSubmissionService.FileSelection(50L)), query)))
                .getMessage().contains("请选择当前项目的完整业务成果"));
        assertTrue(assertThrows(RuntimeException.class, () -> service.submit(9L, 31L, "no-query",
                new ProjectDeliverableSubmissionService.Submission(15L, 0L, "BUSINESS_RESULT", List.of(), null)))
                .getMessage().contains("请选择当前项目的完整业务成果"));
    }

    // ———— 文档归集 ————

    @Test
    void collectDocumentRegistersDocKeepsManualMaterialsAndReplaysByEventId() {
        when(platform.findCurrentSubmission(31L)).thenReturn(Optional.of(
                submission(1001L, "UPLOAD", "{}", null, List.of(9001L))));
        when(platform.listMaterials(31L)).thenReturn(List.of(fileMaterial(9001L, 50L, null, "ACTIVE")));
        service.collectDocument(view, scope(9L), collectedDocument(71L, true), "evt-1");
        verify(platform).registerTemplateFrozenDocument(31L, "SOL.REQUIREMENT_DOCUMENT", 71L, null);
        var commandCaptor = ArgumentCaptor.forClass(PlatformDeliveryRequirementApi.TemplateFrozenSubmitCommand.class);
        verify(platform).submitTemplateFrozen(commandCaptor.capture());
        assertEquals(PlatformDeliveryRequirementApi.SOURCE_BUSINESS_DOCUMENT, commandCaptor.getValue().sourceType());
        assertEquals(List.of(9001L, 9003L), commandCaptor.getValue().materialIds());
        // 状态由 OPEN 翻转：appendEvaluated + 常规唤醒 = 2 次事件；重放不再追加。
        verify(outbox, times(2)).append(anyString(), anyString(), any(BusinessEvent.class));

        when(platform.findSubmissionByRequestKey(31L, "file-event:evt-1")).thenReturn(Optional.of(
                submission(1003L, PlatformDeliveryRequirementApi.SOURCE_BUSINESS_DOCUMENT,
                        commandCaptor.getValue().requestPayloadJson(), null, List.of(9001L, 9003L))));
        service.collectDocument(view, scope(9L), collectedDocument(71L, true), "evt-1");
        verify(platform, times(1)).submitTemplateFrozen(any());
    }

    @Test
    void collectDocumentGuardsLifecycleScopeProjectionAndResultSlots() {
        var file = collectedDocument(71L, true);
        when(rules.read(9L, "D1")).thenReturn(new ProjectDeliverableRuleApi.Context(9L, 15L, "NORMAL_CLOSED",
                11L, "S1", "T1", JsonUtils.parseTree(CONFIG)));
        service.collectDocument(view, scope(9L), file, "closed");
        when(rules.read(9L, "D1")).thenReturn(context);
        service.collectDocument(view, new FileDocumentSourceProvider.Scope(9L, "SOL.OTHER_SOURCE"), file, "unconfigured");
        service.collectDocument(view, scope(10L), file, "other-project");
        when(platform.findCurrentSubmission(31L)).thenReturn(Optional.of(
                submission(1002L, PlatformDeliveryRequirementApi.SOURCE_AUTO_PROJECTION, "{}", null, List.of(9002L))));
        service.collectDocument(view, scope(9L), file, "projection-current");
        when(platform.findCurrentSubmission(31L)).thenReturn(Optional.of(
                submission(1004L, PlatformDeliveryRequirementApi.SOURCE_BUSINESS_RESULT, "{}", null, List.of(9002L))));
        service.collectDocument(view, scope(9L), file, "result-current");
        verify(platform, never()).submitTemplateFrozen(any());
        verifyNoInteractions(outbox);
    }

    @Test
    void collectDocumentDetachLeavesValidityToConvergence() {
        when(platform.findCurrentSubmission(31L)).thenReturn(Optional.of(
                submission(1001L, PlatformDeliveryRequirementApi.SOURCE_BUSINESS_DOCUMENT, "{}", null,
                        List.of(9001L, 9003L))));
        when(platform.listMaterials(31L)).thenReturn(List.of(
                fileMaterial(9001L, 50L, null, "ACTIVE"),
                fileMaterial(9003L, 71L, "SOL.REQUIREMENT_DOCUMENT", "ACTIVE")));
        // 解除挂接事件（同引用、不可用）不落新提交：材料有效性由收敛重验撤回。
        service.collectDocument(view, scope(9L), collectedDocument(71L, false), "detached-1");
        verify(platform, never()).registerTemplateFrozenDocument(anyLong(), anyString(), anyLong(), any());
        verify(platform, never()).submitTemplateFrozen(any());
    }

    // ———— 业务成果归集 ————

    private BusinessResultChange resultChange(UUID eventId, BusinessResultSource.Validity validity) {
        var result = new BusinessResultSource.Result(7L, 9L, resultType(), "55", "56", "1", "1", validity,
                LocalDateTime.parse("2026-09-20T12:00:00"));
        var source = new cn.iocoder.yudao.module.pms.project.api.workbinding.operation.BusinessOperationResultEvent(
                UUID.randomUUID().toString(), 1, 7L, 9L, "SOL", "REQUIREMENT_ANALYSIS", "55", null, 1L, "1",
                "REQUIREMENT_ANALYSIS_COMPLETED", "REQUIREMENT_ANALYSIS_COMPLETE", "owner-command", 11L,
                LocalDateTime.parse("2026-09-20T12:00:00"), "test");
        return new BusinessResultChange(eventId.toString(), 1,
                new BusinessResultChange.Channel(1L, 7L, 9L, result.type()), 8L, source,
                validity == BusinessResultSource.Validity.CURRENT
                        ? BusinessResultSource.Observation.available(result)
                        : BusinessResultSource.Observation.absent(BusinessResultSource.Status.UNAVAILABLE,
                        "OWNER_REVOKED"),
                validity == BusinessResultSource.Validity.CURRENT);
    }

    @Test
    void collectBusinessResultSwapsSlotReplaysByEventIdAndSkipsProjection() {
        var change = resultChange(UUID.randomUUID(), BusinessResultSource.Validity.CURRENT);
        service.collectBusinessResult(view, "SOL.REQUIREMENT_ANALYSIS.REQUIREMENT_ANALYSIS_COMPLETED", change);
        var commandCaptor = ArgumentCaptor.forClass(PlatformDeliveryRequirementApi.TemplateFrozenSubmitCommand.class);
        verify(platform).submitTemplateFrozen(commandCaptor.capture());
        assertEquals(PlatformDeliveryRequirementApi.SOURCE_BUSINESS_RESULT, commandCaptor.getValue().sourceType());
        assertEquals(List.of(9002L), commandCaptor.getValue().materialIds());
        verify(platform).registerTemplateFrozenBusinessResult(eq(31L), eq("project_business_result"), anyString(),
                eq(1L), isNull());

        when(platform.findSubmissionByRequestKey(31L, "result-change:" + change.eventId())).thenReturn(Optional.of(
                submission(1005L, PlatformDeliveryRequirementApi.SOURCE_BUSINESS_RESULT,
                        commandCaptor.getValue().requestPayloadJson(), null, List.of(9002L))));
        service.collectBusinessResult(view, "SOL.REQUIREMENT_ANALYSIS.REQUIREMENT_ANALYSIS_COMPLETED", change);
        verify(platform, times(1)).submitTemplateFrozen(any());

        when(platform.findSubmissionByRequestKey(eq(31L), anyString())).thenReturn(Optional.empty());
        when(platform.findCurrentSubmission(31L)).thenReturn(Optional.of(
                submission(1002L, PlatformDeliveryRequirementApi.SOURCE_AUTO_PROJECTION, "{}", null, List.of(9002L))));
        service.collectBusinessResult(view, "SOL.REQUIREMENT_ANALYSIS.REQUIREMENT_ANALYSIS_COMPLETED",
                resultChange(UUID.randomUUID(), BusinessResultSource.Validity.CURRENT));
        verify(platform, times(1)).submitTemplateFrozen(any());
    }

    @Test
    void collectBusinessResultGuardsLifecycleScopeAndNonCurrentResults() {
        service.collectBusinessResult(view, "SOL.OTHER_SOURCE",
                resultChange(UUID.randomUUID(), BusinessResultSource.Validity.CURRENT));
        when(rules.read(9L, "D1")).thenReturn(new ProjectDeliverableRuleApi.Context(9L, 15L, "NORMAL_CLOSED",
                11L, "S1", "T1", JsonUtils.parseTree(CONFIG)));
        service.collectBusinessResult(view, "SOL.REQUIREMENT_ANALYSIS.REQUIREMENT_ANALYSIS_COMPLETED",
                resultChange(UUID.randomUUID(), BusinessResultSource.Validity.CURRENT));
        when(rules.read(9L, "D1")).thenReturn(context);
        service.collectBusinessResult(view, "SOL.REQUIREMENT_ANALYSIS.REQUIREMENT_ANALYSIS_COMPLETED",
                resultChange(UUID.randomUUID(), BusinessResultSource.Validity.REVOKED));
        verify(platform, never()).registerTemplateFrozenBusinessResult(anyLong(), anyString(), anyString(), any(), any());
        verify(platform, never()).submitTemplateFrozen(any());
        verifyNoInteractions(outbox);
    }

    // ———— 收敛重验阶梯（真实 Resolver）———

    @Test
    void revalidateLadderCoversMissingSourceQuantityAndConfirmation() {
        when(platform.revalidateConvergence(31L)).thenReturn(new TemplateFrozenConvergence(
                view("SATISFIED", 1, 1), List.of()));
        when(platform.findCurrentSubmission(31L)).thenReturn(Optional.of(
                submission(1001L, "UPLOAD", "{}", null, List.of(9001L))));
        when(platform.listMaterials(31L)).thenReturn(List.of(fileMaterial(9001L, 50L, null, "ACTIVE")));
        var satisfied = service.revalidate(view);
        assertTrue(satisfied.satisfied());
        assertEquals("DELIVERABLE_RULE_SATISFIED", satisfied.reason());
        verify(platform).updateSubmissionDecision(eq(1001L), contains("DELIVERABLE_RULE_SATISFIED"));

        when(platform.revalidateConvergence(31L)).thenReturn(new TemplateFrozenConvergence(
                view("OPEN", 1, 1), List.of()));
        when(platform.findCurrentSubmission(31L)).thenReturn(Optional.empty());
        assertFalse(service.revalidate(view).satisfied());
        assertEquals("DELIVERABLE_SOURCE_MISSING", service.revalidate(view).reason());

        when(platform.findCurrentSubmission(31L)).thenReturn(Optional.of(
                submission(1001L, "UPLOAD", "{}", null, List.of(9001L))));
        when(platform.revalidateConvergence(31L)).thenReturn(new TemplateFrozenConvergence(
                view("OPEN", 1, 2), List.of()));
        assertEquals("DELIVERABLE_QUANTITY_NOT_MET", service.revalidate(view).reason());
        verify(platform, times(1)).updateSubmissionDecision(anyLong(), anyString());
    }

    @Test
    void revalidateCollectedDocumentScopeChangeIsRejected() {
        when(platform.revalidateConvergence(31L)).thenReturn(new TemplateFrozenConvergence(
                view("OPEN", 1, 1), List.of()));
        when(platform.findCurrentSubmission(31L)).thenReturn(Optional.of(
                submission(1001L, PlatformDeliveryRequirementApi.SOURCE_BUSINESS_DOCUMENT, "{}", null, List.of(9003L))));
        when(platform.listMaterials(31L)).thenReturn(List.of(
                fileMaterial(9003L, 71L, "SOL.REQUIREMENT_DOCUMENT", "ACTIVE")));
        when(fileEvidence.inspectDocument(7L, 71L)).thenReturn(collectedDocument(71L, true));
        when(documentSources.resolve(7L, collectedDocument(71L, true))).thenReturn(scope(10L));
        var evaluation = service.revalidate(view);
        assertEquals("DELIVERABLE_DOCUMENT_SCOPE_CHANGED", evaluation.reason());
        assertFalse(evaluation.satisfied());
    }

    @Test
    void revalidateProjectionDelegatesToOwnerEvidence() {
        when(platform.revalidateConvergence(31L)).thenReturn(new TemplateFrozenConvergence(
                view("SATISFIED", 1, 1), List.of()));
        var projection = submission(1002L, PlatformDeliveryRequirementApi.SOURCE_AUTO_PROJECTION, "{}", null, List.of(9002L));
        when(platform.findCurrentSubmission(31L)).thenReturn(Optional.of(projection));
        when(ownerSources.revalidate(view, projection)).thenReturn(
                new ProjectDeliverableOwnerSources.Evidence(true, "VALID", 1, List.of()));
        assertTrue(service.revalidate(view).satisfied());

        when(ownerSources.revalidate(view, projection)).thenReturn(
                new ProjectDeliverableOwnerSources.Evidence(false, "FILE_EVIDENCE_UNAVAILABLE", 0, List.of()));
        var evaluation = service.revalidate(view);
        assertEquals("FILE_EVIDENCE_UNAVAILABLE", evaluation.reason());
        // 有效证据路径已评估一次；证据失效路径不得再触发规则判定。
        verify(rules, times(1)).evaluate(9L, "D1");
    }

    // ———— 门禁刷新与评估事件 ————

    @Test
    void refreshAppendsEvaluatedEventOnlyOnStatusChange() {
        when(platform.revalidateConvergence(31L)).thenReturn(new TemplateFrozenConvergence(
                view("OPEN", 3, 1), List.of()));
        assertFalse(service.refresh(9L, 31L).satisfied());
        verify(outbox, times(1)).append(anyString(), anyString(), any(BusinessEvent.class));

        when(platform.revalidateConvergence(31L)).thenReturn(new TemplateFrozenConvergence(
                view("SATISFIED", 4, 1), List.of()));
        when(platform.findCurrentSubmission(31L)).thenReturn(Optional.of(
                submission(1001L, "UPLOAD", "{}", null, List.of(9001L))));
        assertTrue(service.refresh(9L, 31L).satisfied());
        var eventCaptor = ArgumentCaptor.forClass(BusinessEvent.class);
        verify(outbox, times(3)).append(anyString(), anyString(), eventCaptor.capture());
        var evaluated = eventCaptor.getAllValues().stream()
                .filter(event -> "ProjectDeliverableEvaluated.v1".equals(event.eventType())).toList();
        assertEquals(1, evaluated.size());
        assertEquals("deliverable-evaluated:31:4", evaluated.getFirst().eventId());
        var payload = JsonUtils.parseTree(evaluated.getFirst().eventPayload());
        assertEquals(evaluated.getFirst().eventId(), payload.path("eventId").asText());
        assertEquals("SATISFIED", payload.path("status").asText());
    }
}
