package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo.RequirementAnalysisAttachmentReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.RequirementAnalysisSectionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.PreparationMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.RequirementAnalysisRootMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.RequirementAnalysisSectionMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.RequirementAnalysisSectionPatchUpdate;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.AttachExistingFileVersionsCommand;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetCollectionQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetCollectionRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetFact;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectWorkBindingFactApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingFact;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingTarget;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.REQUIREMENT_ANALYSIS_COMMAND_INVALID;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.REQUIREMENT_ANALYSIS_CONTENT_INVALID;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.REQUIREMENT_VERSION_NOT_MATCH;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.PLATFORM_COMMAND_IN_PROGRESS;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.PLATFORM_COMMAND_KEY_CONFLICT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequirementAnalysisCommandServiceTest {

    private static final String SLOT = "2fce3d44-109d-47be-b15a-5ea09fda1a0f";
    private static final FileFactVersion FILE_VERSION = new FileFactVersion(2, 3, 4);

    @Mock PreparationMapper preparationMapper;
    @Mock RequirementAnalysisRootMapper rootMapper;
    @Mock RequirementAnalysisSectionMapper sectionMapper;
    @Mock ProjectScopeApi projectScopeApi;
    @Mock ProjectParticipantFactApi participantFactApi;
    @Mock ProjectWorkBindingFactApi workBindingFactApi;
    @Mock FileArtifactApi fileArtifactApi;
    @Mock PermissionApi permissionApi;
    @Mock PlatformCommandExecutionApi commandExecutionApi;
    @Mock OperationAuditApi operationAuditApi;
    @Mock TransactionTemplate transactionTemplate;

    private RequirementAnalysisCommandService service;
    private final AtomicLong sectionIds = new AtomicLong(700L);
    private final List<PlatformCommandExecutionApi.SuccessFacts> recordedSuccessFacts = new ArrayList<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new RequirementAnalysisCommandService(preparationMapper, rootMapper, sectionMapper,
                projectScopeApi, participantFactApi, workBindingFactApi, fileArtifactApi, permissionApi,
                commandExecutionApi, operationAuditApi, transactionTemplate);
        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<Object>) invocation.getArgument(0))
                        .doInTransaction(mock(TransactionStatus.class)));
    }

    @Test
    void createsInitialDraftFromFrozenBindingWithElevenCoreSections() {
        stubManager(true);
        when(preparationMapper.insert(any())).thenAnswer(invocation -> {
            PreparationDO row = invocation.getArgument(0);
            row.setId(501L);
            return 1;
        });
        when(sectionMapper.insert(any())).thenAnswer(invocation -> {
            RequirementAnalysisSectionDO row = invocation.getArgument(0);
            row.setId(sectionIds.incrementAndGet());
            return 1;
        });
        executeNewCommands();

        var result = service.createInitial(new RequirementAnalysisCommandService.CreateCommand(
                100L, 3, "initial-key"), actor());

        assertEquals(501L, result.preparationId());
        assertEquals("DRAFT", result.status());
        ArgumentCaptor<PreparationDO> root = ArgumentCaptor.forClass(PreparationDO.class);
        verify(preparationMapper).insert(root.capture());
        assertEquals(1, root.getValue().getBusinessVersion());
        assertEquals(1, root.getValue().getDraftMarker());
        assertEquals(17L, root.getValue().getTemplateRevisionId());
        verify(sectionMapper, times(11)).insert(any());
        Map<?, ?> audit = latestSuccessAudit();
        assertEquals("initial-key", audit.get("operationId"));
        assertNull(audit.get("draftPreparationIdBefore"));
        assertEquals(501L, longValue(audit, "draftPreparationIdAfter"));
        assertNull(audit.get("effectivePreparationIdBefore"));
        assertNull(audit.get("effectivePreparationIdAfter"));
        assertNull(audit.get("statusBefore"));
        assertEquals("DRAFT", audit.get("statusAfter"));
        assertNull(audit.get("businessVersionBefore"));
        assertEquals(1, intValue(audit, "businessVersionAfter"));
        assertNull(audit.get("contentVersionBefore"));
        assertEquals(0, intValue(audit, "contentVersionAfter"));
        assertTrue(recordedSuccessFacts.getLast().detailSnapshot().contains("\"statusBefore\":null"));
        assertTrue(recordedSuccessFacts.getLast().detailSnapshot()
                .contains("\"effectivePreparationIdAfter\":null"));
    }

    @Test
    void patchHonorsFieldPresenceAndAdvancesBothSectionAndRootCas() {
        stubManager(false);
        PreparationDO root = draft();
        RequirementAnalysisSectionDO section = section(701L, "PROJECT_BACKGROUND", true, "\"old\"");
        when(rootMapper.selectById(any())).thenReturn(root);
        when(rootMapper.selectForUpdate(any())).thenReturn(root);
        when(sectionMapper.selectForUpdate(any())).thenReturn(section);
        when(sectionMapper.patchIfMatch(any())).thenReturn(1);
        when(rootMapper.incrementContentIfMatch(any())).thenReturn(1);

        var result = service.patch(new RequirementAnalysisCommandService.PatchCommand(501L, 701L,
                2, 5, 3, Set.of("value"), JsonUtils.parseTree("\"new\""),
                null), actor());

        assertEquals(6, result.contentVersion());
        assertEquals(3, result.version());
        ArgumentCaptor<RequirementAnalysisSectionPatchUpdate> update =
                ArgumentCaptor.forClass(RequirementAnalysisSectionPatchUpdate.class);
        verify(sectionMapper).patchIfMatch(update.capture());
        assertTrue(update.getValue().updateValue());
        assertEquals(false, update.getValue().updateAttachments());
        assertEquals("\"new\"", update.getValue().valueSnapshot());
        verify(operationAuditApi).record(eq(0L), eq(9L), eq("corr-1"),
                eq("REQUIREMENT_ANALYSIS_PATCH"), eq("RequirementAnalysis"), eq("501"),
                eq("SUCCESS"), any(Map.class));

        ServiceException invalid = assertThrows(ServiceException.class, () -> service.patch(
                new RequirementAnalysisCommandService.PatchCommand(501L, 701L, 3, 6, 3,
                        Set.of("unknown"), null, null), actor()));
        assertEquals(REQUIREMENT_ANALYSIS_COMMAND_INVALID.getCode(), invalid.getCode());

        when(sectionMapper.patchIfMatch(any())).thenReturn(0);
        ServiceException cas = assertThrows(ServiceException.class, () -> service.patch(
                new RequirementAnalysisCommandService.PatchCommand(501L, 701L, 3, 6, 3,
                        Set.of("value"), JsonUtils.parseTree("\"new\""), null), actor()));
        assertEquals(REQUIREMENT_VERSION_NOT_MATCH.getCode(), cas.getCode());
        verify(rootMapper, times(1)).incrementContentIfMatch(any());
    }

    @Test
    void richTextRemovesEmbeddedMediaAndInvalidPatchWritesStableRejectedAudit() {
        stubManager(false);
        PreparationDO root = draft();
        RequirementAnalysisSectionDO section = section(701L, "PROJECT_BACKGROUND", true, "\"old\"");
        when(rootMapper.selectById(any())).thenReturn(root);
        when(rootMapper.selectForUpdate(any())).thenReturn(root);
        when(sectionMapper.selectForUpdate(any())).thenReturn(section);
        String input = "<p>说明</p><img src=\"https://files.example/a.png\"><video src=\"a.mp4\"></video>";
        when(sectionMapper.patchIfMatch(any())).thenReturn(1);
        when(rootMapper.incrementContentIfMatch(any())).thenReturn(1);

        service.patch(new RequirementAnalysisCommandService.PatchCommand(501L, 701L,
                2, 5, 3, Set.of("value"), JsonUtils.parseTree(JsonUtils.toJsonString(input)), null), actor());

        ArgumentCaptor<RequirementAnalysisSectionPatchUpdate> update =
                ArgumentCaptor.forClass(RequirementAnalysisSectionPatchUpdate.class);
        verify(sectionMapper).patchIfMatch(update.capture());
        assertEquals("\"<p>说明</p>\"", update.getValue().valueSnapshot());

        ServiceException invalid = assertThrows(ServiceException.class, () -> service.patch(null, actor()));
        assertEquals(REQUIREMENT_ANALYSIS_COMMAND_INVALID.getCode(), invalid.getCode());
        verify(operationAuditApi).record(eq(0L), eq(9L), eq("corr-1"),
                eq("REQUIREMENT_ANALYSIS_PATCH"), eq("RequirementAnalysis"), eq("UNKNOWN"),
                eq("REJECTED"), any(Map.class));
    }

    @Test
    void attachmentPatchLocksCompleteActiveSetBeforeFreezingCommonScopeVersion() {
        stubManager(false);
        PreparationDO root = draft();
        RequirementAnalysisSectionDO section = section(701L, "PROJECT_BACKGROUND", true, "\"old\"");
        when(rootMapper.selectById(any())).thenReturn(root);
        when(rootMapper.selectForUpdate(any())).thenReturn(root);
        when(sectionMapper.selectForUpdate(any())).thenReturn(section);
        when(sectionMapper.patchIfMatch(any())).thenReturn(1);
        when(rootMapper.incrementContentIfMatch(any())).thenReturn(1);
        FileArtifactVersionFact active = fileFact(SLOT);
        when(fileArtifactApi.inspectReferenceSets(any())).thenAnswer(invocation -> {
            FileReferenceSetCollectionQuery query = invocation.getArgument(0);
            return List.of(new FileReferenceSetFact(query.collectionKeys().getFirst(), 7L, List.of(active)));
        });
        when(fileArtifactApi.lockAndRevalidateReferenceSets(any())).thenAnswer(invocation -> {
            FileReferenceSetCollectionRevalidationQuery query = invocation.getArgument(0);
            return List.of(new FileReferenceSetFact(query.collections().getFirst().key(), 7L, List.of(active)));
        });
        var attachment = new RequirementAnalysisAttachmentReqVO();
        attachment.setArtifactId(801L);
        attachment.setVersionNo(1);
        attachment.setReferenceKey(SLOT);
        attachment.setFileFactVersion(FILE_VERSION);
        attachment.setScopeVersion(7L);

        service.patch(new RequirementAnalysisCommandService.PatchCommand(501L, 701L,
                2, 5, 3, Set.of("attachments"), null, List.of(attachment)), actor());

        ArgumentCaptor<RequirementAnalysisSectionPatchUpdate> update =
                ArgumentCaptor.forClass(RequirementAnalysisSectionPatchUpdate.class);
        InOrder order = inOrder(fileArtifactApi, sectionMapper, rootMapper);
        order.verify(fileArtifactApi).inspectReferenceSets(any());
        order.verify(fileArtifactApi).lockAndRevalidateReferenceSets(any());
        order.verify(sectionMapper).patchIfMatch(update.capture());
        order.verify(rootMapper).incrementContentIfMatch(any());
        assertTrue(update.getValue().attachmentReferenceSnapshot().contains("\"scopeVersion\":7"));
    }

    @Test
    void completionRejectsMissingMandatoryContentThenRevalidatesEveryFrozenFileBeforeCas() {
        stubManager(true);
        executeNewCommands();
        PreparationDO root = draft();
        when(rootMapper.selectById(any())).thenReturn(root);
        when(rootMapper.selectForUpdate(any())).thenReturn(root);
        List<RequirementAnalysisSectionDO> invalid = elevenSections();
        invalid.getFirst().setValueSnapshot("null");
        when(sectionMapper.selectListForUpdate(any())).thenReturn(invalid);

        ServiceException missing = assertThrows(ServiceException.class, () -> service.complete(
                new RequirementAnalysisCommandService.CompleteCommand(501L, 2, 5, 3, "complete-missing"), actor()));
        assertEquals(REQUIREMENT_ANALYSIS_CONTENT_INVALID.getCode(), missing.getCode());
        verify(fileArtifactApi, never()).lockAndRevalidateReferenceSets(any());
        verify(rootMapper, never()).completeDraftIfMatch(any());

        List<RequirementAnalysisSectionDO> invisible = elevenSections();
        invisible.getFirst().setValueSnapshot("\"<p>&nbsp;</p><p>​</p>\"");
        when(sectionMapper.selectListForUpdate(any())).thenReturn(invisible);
        ServiceException invisibleText = assertThrows(ServiceException.class, () -> service.complete(
                new RequirementAnalysisCommandService.CompleteCommand(
                        501L, 2, 5, 3, "complete-invisible-rich-text"), actor()));
        assertEquals(REQUIREMENT_ANALYSIS_CONTENT_INVALID.getCode(), invisibleText.getCode());

        List<RequirementAnalysisSectionDO> valid = elevenSections();
        RequirementAnalysisSectionDO requiredFalse = section(750L, "BOOLEAN_EXTENSION", true, "false");
        requiredFalse.setFieldTypeCode("BOOLEAN");
        RequirementAnalysisSectionDO requiredZero = section(751L, "NUMBER_EXTENSION", true, "0");
        requiredZero.setFieldTypeCode("NUMBER");
        valid.add(requiredFalse);
        valid.add(requiredZero);
        valid.getFirst().setAttachmentReferenceSnapshot(attachmentSnapshot());
        when(sectionMapper.selectListForUpdate(any())).thenReturn(valid);
        stubReferenceSets(valid);
        when(rootMapper.completeDraftIfMatch(any())).thenReturn(1);

        var completed = service.complete(new RequirementAnalysisCommandService.CompleteCommand(
                501L, 2, 5, 3, "complete-ok"), actor());

        assertEquals("COMPLETED", completed.status());
        verify(fileArtifactApi).lockAndRevalidateReferenceSets(any());
        verify(rootMapper).completeDraftIfMatch(any());
        Map<?, ?> audit = latestSuccessAudit();
        assertEquals("complete-ok", audit.get("operationId"));
        assertEquals(501L, longValue(audit, "draftPreparationIdBefore"));
        assertNull(audit.get("draftPreparationIdAfter"));
        assertNull(audit.get("effectivePreparationIdBefore"));
        assertEquals(501L, longValue(audit, "effectivePreparationIdAfter"));
        assertEquals("DRAFT", audit.get("statusBefore"));
        assertEquals("COMPLETED", audit.get("statusAfter"));
        assertEquals(1, intValue(audit, "businessVersionBefore"));
        assertEquals(1, intValue(audit, "businessVersionAfter"));
        assertEquals(5, intValue(audit, "contentVersionBefore"));
        assertEquals(5, intValue(audit, "contentVersionAfter"));
        assertEquals(2, intValue(audit, "aggregateVersionBefore"));
        assertEquals(3, intValue(audit, "aggregateVersionAfter"));
    }

    @Test
    void completionLocksAllSectionReferenceSetsOnceBeforeCas() {
        String secondSlot = "8d900f5b-1f6b-47cd-ab51-74be8b3c6c44";
        stubManager(true);
        executeNewCommands();
        PreparationDO root = draft();
        when(rootMapper.selectById(any())).thenReturn(root);
        when(rootMapper.selectForUpdate(any())).thenReturn(root);
        List<RequirementAnalysisSectionDO> sections = elevenSections();
        sections.get(0).setAttachmentReferenceSnapshot(attachmentSnapshot(900L, SLOT));
        sections.get(1).setAttachmentReferenceSnapshot(attachmentSnapshot(800L, secondSlot));
        when(sectionMapper.selectListForUpdate(any())).thenReturn(sections);
        stubReferenceSets(sections);
        when(rootMapper.completeDraftIfMatch(any())).thenReturn(1);

        service.complete(new RequirementAnalysisCommandService.CompleteCommand(
                501L, 2, 5, 3, "complete-ordered-files"), actor());

        ArgumentCaptor<FileReferenceSetCollectionRevalidationQuery> locks =
                ArgumentCaptor.forClass(FileReferenceSetCollectionRevalidationQuery.class);
        verify(fileArtifactApi).lockAndRevalidateReferenceSets(locks.capture());
        assertEquals(11, locks.getValue().collections().size());
        InOrder order = inOrder(rootMapper, fileArtifactApi);
        order.verify(rootMapper).selectById(any());
        order.verify(rootMapper).selectEffective(any());
        order.verify(rootMapper).selectForUpdate(any());
        order.verify(rootMapper).selectEffectiveForUpdate(any());
        order.verify(fileArtifactApi).inspectReferenceSets(any());
        order.verify(fileArtifactApi).lockAndRevalidateReferenceSets(any());
        order.verify(rootMapper).completeDraftIfMatch(any());
    }

    @Test
    void revisionCopiesFrozenSectionsAndAttachesExistingVersionsToIndependentSlots() {
        stubManager(false);
        executeNewCommands();
        PreparationDO source = completed();
        RequirementAnalysisSectionDO sourceSection = section(701L, "PROJECT_BACKGROUND", true, "\"frozen\"");
        sourceSection.setAttachmentReferenceSnapshot(attachmentSnapshot());
        when(rootMapper.selectById(any())).thenReturn(source);
        when(rootMapper.selectEffectiveForUpdate(any())).thenReturn(source);
        when(sectionMapper.selectListForUpdate(any())).thenReturn(List.of(sourceSection));
        when(preparationMapper.insert(any())).thenAnswer(invocation -> {
            PreparationDO row = invocation.getArgument(0);
            row.setId(502L);
            return 1;
        });
        when(sectionMapper.insert(any())).thenAnswer(invocation -> {
            RequirementAnalysisSectionDO row = invocation.getArgument(0);
            row.setId(702L);
            return 1;
        });
        when(fileArtifactApi.attachExistingVersions(any())).thenAnswer(invocation -> {
            AttachExistingFileVersionsCommand command = invocation.getArgument(0);
            String targetKey = command.items().getFirst().target().referenceKey();
            return List.of(fileFact(targetKey));
        });
        when(sectionMapper.patchIfMatch(any())).thenReturn(1);

        var draft = service.createRevision(new RequirementAnalysisCommandService.CreateRevisionCommand(
                501L, 4, 5, 3, "revision-key"), actor());

        assertEquals(502L, draft.preparationId());
        assertEquals(2, draft.businessVersion());
        ArgumentCaptor<AttachExistingFileVersionsCommand> attach =
                ArgumentCaptor.forClass(AttachExistingFileVersionsCommand.class);
        verify(fileArtifactApi).attachExistingVersions(attach.capture());
        var item = attach.getValue().items().getFirst();
        assertEquals("701", item.source().objectId());
        assertEquals("702", item.target().objectId());
        assertNotEquals(SLOT, item.target().referenceKey());
        verify(sectionMapper).patchIfMatch(any());
        Map<?, ?> audit = latestSuccessAudit();
        assertEquals("revision-key", audit.get("operationId"));
        assertNull(audit.get("draftPreparationIdBefore"));
        assertEquals(502L, longValue(audit, "draftPreparationIdAfter"));
        assertEquals(501L, longValue(audit, "effectivePreparationIdBefore"));
        assertEquals(501L, longValue(audit, "effectivePreparationIdAfter"));
        assertEquals("COMPLETED", audit.get("statusBefore"));
        assertEquals("DRAFT", audit.get("statusAfter"));
        assertEquals(1, intValue(audit, "businessVersionBefore"));
        assertEquals(2, intValue(audit, "businessVersionAfter"));
        assertEquals(5, intValue(audit, "contentVersionBefore"));
        assertEquals(0, intValue(audit, "contentVersionAfter"));
        assertFalse(recordedSuccessFacts.getLast().detailSnapshot().contains("frozen"));
    }

    @Test
    void idempotencyReplayConflictAndInProgressHaveStableClosedSemantics() {
        var replay = new RequirementAnalysisCommandService.CommandResult(501L, 100L, 1, "DRAFT", 0, 0);
        when(commandExecutionApi.execute(any(), any(), any(), any(), any()))
                .thenReturn(new PlatformCommandExecutionApi.ExecutionResult<>(
                        PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED, replay));
        assertEquals(replay, service.createInitial(new RequirementAnalysisCommandService.CreateCommand(
                100L, 3, "same-intent"), actor()));
        verifyNoInteractions(preparationMapper, sectionMapper);

        when(commandExecutionApi.execute(any(), any(), any(), any(), any()))
                .thenReturn(new PlatformCommandExecutionApi.ExecutionResult<>(
                        PlatformCommandExecutionApi.Decision.CONFLICT, null));
        ServiceException conflict = assertThrows(ServiceException.class, () -> service.createInitial(
                new RequirementAnalysisCommandService.CreateCommand(100L, 3, "same-intent"), actor()));
        assertEquals(PLATFORM_COMMAND_KEY_CONFLICT.getCode(), conflict.getCode());

        when(commandExecutionApi.execute(any(), any(), any(), any(), any()))
                .thenReturn(new PlatformCommandExecutionApi.ExecutionResult<>(
                        PlatformCommandExecutionApi.Decision.IN_PROGRESS, null));
        ServiceException inProgress = assertThrows(ServiceException.class, () -> service.createInitial(
                new RequirementAnalysisCommandService.CreateCommand(100L, 3, "running-intent"), actor()));
        assertEquals(PLATFORM_COMMAND_IN_PROGRESS.getCode(), inProgress.getCode());
    }

    @Test
    void missingManagePermissionFailsBeforeProjectOrSolLocks() {
        when(permissionApi.hasAnyPermissions(9L, RequirementAnalysisQueryService.PERMISSION_MANAGE)).thenReturn(false);
        executeNewCommands();

        ServiceException error = assertThrows(ServiceException.class, () -> service.createInitial(
                new RequirementAnalysisCommandService.CreateCommand(100L, 3, "forbidden"), actor()));

        assertEquals(FORBIDDEN.getCode(), error.getCode());
        verifyNoInteractions(projectScopeApi, participantFactApi, workBindingFactApi, preparationMapper, sectionMapper);
    }

    @SuppressWarnings("unchecked")
    private void executeNewCommands() {
        when(commandExecutionApi.execute(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            Supplier<Object> operation = invocation.getArgument(3);
            Object response = operation.get();
            Function<Object, PlatformCommandExecutionApi.SuccessFacts> facts = invocation.getArgument(4);
            recordedSuccessFacts.add(facts.apply(response));
            return new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW, response);
        });
    }

    private Map<?, ?> latestSuccessAudit() {
        PlatformCommandExecutionApi.SuccessFacts facts = recordedSuccessFacts.getLast();
        return JsonUtils.parseObject(facts.detailSnapshot(), Map.class);
    }

    private long longValue(Map<?, ?> audit, String key) {
        return ((Number) audit.get(key)).longValue();
    }

    private int intValue(Map<?, ?> audit, String key) {
        return ((Number) audit.get(key)).intValue();
    }

    private void stubManager(boolean withBinding) {
        when(permissionApi.hasAnyPermissions(9L, RequirementAnalysisQueryService.PERMISSION_MANAGE)).thenReturn(true);
        when(projectScopeApi.resolveCurrent(any())).thenReturn(scope());
        when(projectScopeApi.lockAndRevalidate(any())).thenReturn(scope());
        when(participantFactApi.inspect(any())).thenReturn(manager());
        when(participantFactApi.lockAndRevalidate(any())).thenReturn(manager());
        if (withBinding) {
            when(workBindingFactApi.inspect(any())).thenReturn(binding());
            when(workBindingFactApi.lockAndRevalidate(any())).thenReturn(binding());
        }
    }

    private RequirementAnalysisCommandService.Actor actor() {
        return new RequirementAnalysisCommandService.Actor(0L, 9L, "corr-1");
    }

    private ProjectScopeResult scope() {
        return new ProjectScopeResult(100L, 7L, Set.of(100L), Set.of());
    }

    private ProjectParticipantFact manager() {
        return new ProjectParticipantFact(100L, 9L, Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER),
                "PRIMARY", "ACTIVE", "S1", 3, 11L);
    }

    private ProjectWorkBindingFact binding() {
        return new ProjectWorkBindingFact(100L, 3, 201L, 1, 301L, 1, 401L, 1,
                ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS.workBindingTypeCode(),
                ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS.targetContextCode(),
                ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS.targetObjectType(),
                ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS.targetObjectKey(),
                null, null, null, null, 17L, 1,
                "{\"schemaVersion\":1,\"catalogCode\":\"PRE_04_REQUIREMENT_ANALYSIS\","
                        + "\"catalogVersion\":1,\"extensionItems\":[]}");
    }

    private PreparationDO draft() {
        PreparationDO row = baseRoot(501L, 1, "DRAFT");
        row.setDraftMarker(1);
        row.setVersion(2);
        row.setContentVersion(5);
        return row;
    }

    private PreparationDO completed() {
        PreparationDO row = baseRoot(501L, 1, "COMPLETED");
        row.setEffectiveMarker(1);
        row.setVersion(4);
        row.setContentVersion(5);
        return row;
    }

    private PreparationDO baseRoot(Long id, int businessVersion, String status) {
        PreparationDO row = new PreparationDO();
        row.setId(id);
        row.setTenantId(0L);
        row.setProjectId(100L);
        row.setBusinessVersion(businessVersion);
        row.setPreparationTypeCode(RequirementAnalysisQueryService.TYPE);
        row.setTemplateId(401L);
        row.setTemplateRevisionId(17L);
        row.setTemplateSnapshot(binding().bindingParameterSnapshot());
        row.setFixedFormCatalogVersion(1);
        row.setStatusCode(status);
        return row;
    }

    private List<RequirementAnalysisSectionDO> elevenSections() {
        List<RequirementAnalysisSectionDO> rows = new ArrayList<>();
        String[] codes = {"PROJECT_BACKGROUND", "PROJECT_OBJECTIVE", "NETWORK_TOPOLOGY",
                "TRANSMISSION_REQUIREMENT", "TRAFFIC_REQUIREMENT", "BUSINESS_REQUIREMENT", "IP_PLANNING",
                "REDUNDANCY_REQUIREMENT", "SECURITY_PROTECTION", "OPERATIONS_REQUIREMENT", "LOGGING_REQUIREMENT"};
        for (int i = 0; i < codes.length; i++) rows.add(section(701L + i, codes[i], i < 3, "\"filled\""));
        return rows;
    }

    private RequirementAnalysisSectionDO section(Long id, String code, boolean required, String value) {
        RequirementAnalysisSectionDO row = new RequirementAnalysisSectionDO();
        row.setId(id);
        row.setTenantId(0L);
        row.setPreparationId(501L);
        row.setSectionCode(code);
        row.setSectionName(code);
        row.setSectionKindCode("CORE");
        row.setFieldTypeCode("RICH_TEXT");
        row.setRequiredFlag(required);
        row.setSortOrder(id.intValue());
        row.setSchemaSnapshot("{}");
        row.setValueSnapshot(value);
        row.setAttachmentReferenceSnapshot("[]");
        row.setVersion(1);
        return row;
    }

    private String attachmentSnapshot() {
        return attachmentSnapshot(801L, SLOT);
    }

    private String attachmentSnapshot(Long artifactId, String referenceKey) {
        return "[{\"artifactId\":" + artifactId + ",\"versionNo\":1,\"referenceKey\":\"" + referenceKey
                + "\",\"fileFactVersion\":{\"artifactVersion\":2,\"referenceVersion\":3,"
                + "\"availabilityVersion\":4},\"scopeVersion\":7}]";
    }

    private void stubReferenceSets(List<RequirementAnalysisSectionDO> sections) {
        Map<Long, RequirementAnalysisSectionDO> byId = sections.stream()
                .collect(java.util.stream.Collectors.toMap(RequirementAnalysisSectionDO::getId,
                        Function.identity()));
        when(fileArtifactApi.inspectReferenceSets(any())).thenAnswer(invocation -> {
            FileReferenceSetCollectionQuery query = invocation.getArgument(0);
            return query.collectionKeys().stream().map(key -> referenceSet(
                    key, byId.get(Long.valueOf(key.objectId())))).toList();
        });
        when(fileArtifactApi.lockAndRevalidateReferenceSets(any())).thenAnswer(invocation -> {
            FileReferenceSetCollectionRevalidationQuery query = invocation.getArgument(0);
            return query.collections().stream().map(expected -> new FileReferenceSetFact(
                    expected.key(), expected.expectedScopeVersion(), expected.expectedActiveFacts())).toList();
        });
    }

    private FileReferenceSetFact referenceSet(
            cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetKey key,
            RequirementAnalysisSectionDO section) {
        List<RequirementAnalysisQueryService.AttachmentFact> saved = section == null
                ? List.of() : JsonUtils.parseArray(section.getAttachmentReferenceSnapshot(),
                RequirementAnalysisQueryService.AttachmentFact.class);
        List<FileArtifactVersionFact> active = (saved == null ? List
                .<RequirementAnalysisQueryService.AttachmentFact>of() : saved).stream()
                .map(fact -> new FileArtifactVersionFact(fact.artifactId(), fact.versionNo(), fact.referenceKey(),
                        "REQUIREMENT_ANALYSIS_ATTACHMENT", "evidence.pdf", 10L, "application/pdf", "sha",
                        "AVAILABLE", "ACTIVE", fact.fileFactVersion(), fact.scopeVersion()))
                .sorted(java.util.Comparator.comparing(FileArtifactVersionFact::referenceKey)).toList();
        return new FileReferenceSetFact(key, 7L, active);
    }

    private FileArtifactVersionFact fileFact(String referenceKey) {
        return new FileArtifactVersionFact(801L, 1, referenceKey, "SECTION_ATTACHMENT", "evidence.pdf", 10L,
                "application/pdf", "sha", "AVAILABLE", "ACTIVE", FILE_VERSION, 7L);
    }
}
