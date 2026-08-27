package cn.iocoder.yudao.module.pms.engineering.service.preparation;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.DynamicFormInstanceDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationItemDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationSourceReferenceDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.DynamicFormInstanceMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.PreparationItemMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.PreparationMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.PreparationSourceReferenceMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.PreparationItemReviewUpdate;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.command.PreparationReviewCommand;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PreparationReviewServiceTest {

    @Mock private PreparationMapper preparationMapper;
    @Mock private PreparationItemMapper itemMapper;
    @Mock private DynamicFormInstanceMapper formMapper;
    @Mock private PreparationSourceReferenceMapper sourceMapper;
    @Mock private PermissionApi permissionApi;
    @Mock private ProjectScopeApi projectScopeApi;
    @Mock private ProjectParticipantFactApi participantFactApi;
    @Mock private FileArtifactApi fileArtifactApi;
    @Mock private PreparationSourceProviderRegistry sourceProviderRegistry;
    @Mock private PlatformCommandExecutionApi commandExecutionApi;
    @Mock private OperationAuditApi operationAuditApi;
    @Mock private TransactionTemplate transactionTemplate;
    @InjectMocks private PreparationReviewService service;
    private final AtomicReference<PlatformCommandExecutionApi.SuccessFacts> successFacts = new AtomicReference<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<Object>) invocation.getArgument(0))
                        .doInTransaction(mock(TransactionStatus.class)));
        when(permissionApi.hasAnyPermissions(7L, PreparationInitializationService.PERMISSION_MANAGE)).thenReturn(true);
        ProjectScopeResult scope = new ProjectScopeResult(10L, 3L, Set.of(10L), Set.of());
        when(projectScopeApi.resolveCurrent(any())).thenReturn(scope);
        when(projectScopeApi.lockAndRevalidate(any())).thenReturn(scope);
        when(commandExecutionApi.execute(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            Supplier<Object> operation = invocation.getArgument(3);
            Function<Object, PlatformCommandExecutionApi.SuccessFacts> facts = invocation.getArgument(4);
            Object response = operation.get();
            successFacts.set(facts.apply(response));
            return new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW, response);
        });
    }

    @Test
    void submitFreezesRequiredAndNotApplicableItems() {
        PreparationDO preparation = preparation("DRAFT", 1, 1);
        PreparationItemDO required = item(101L, "REQUIRED", "PENDING", 1);
        required.setAssigneeUserId(9L);
        required.setSiteResultCode("READY");
        required.setEvidencePolicySnapshot("{\"required\":true}");
        required.setEvidenceReferenceSnapshot(evidence());
        PreparationItemDO notApplicable = item(102L, "NOT_APPLICABLE_PENDING", "PENDING", 1);
        notApplicable.setNotApplicableReason("现场不涉及");
        stubRows(preparation, List.of(required, notApplicable), List.of(form(201L, 101L), form(202L, 102L)));
        when(formMapper.freezeIfMatch(any())).thenReturn(1);
        when(preparationMapper.updateLifecycleIfMatch(any())).thenReturn(1);
        when(fileArtifactApi.lockAndRevalidate(any())).thenReturn(fileFact());

        var result = service.execute(command(PreparationReviewCommand.SUBMIT, null, 1, null, null), actor());

        assertEquals("PENDING_CONFIRMATION", result.statusCode());
        assertEquals(2, result.preparationVersion());
        verify(formMapper, org.mockito.Mockito.times(2)).freezeIfMatch(any());
        verify(preparationMapper).updateLifecycleIfMatch(any());
        ArgumentCaptor<FileArtifactVersionRevalidationQuery> fileQuery =
                ArgumentCaptor.forClass(FileArtifactVersionRevalidationQuery.class);
        verify(fileArtifactApi).lockAndRevalidate(fileQuery.capture());
        assertEquals("READ", fileQuery.getValue().requiredAction());
        verify(sourceMapper).selectListForUpdate(any());
        String audit = successFacts.get().detailSnapshot();
        assertTrue(audit.contains("\"preparationBefore\""));
        assertTrue(audit.contains("\"preparationAfter\""));
        assertTrue(audit.contains("\"formsAfter\""));
        assertTrue(audit.contains("\"sourcesLocked\""));
    }

    @Test
    void submitReturnedDraftKeepsRetainedConfirmedFormFrozen() {
        PreparationDO preparation = preparation("DRAFT", 0, 2);
        PreparationItemDO pending = item(101L, "REQUIRED", "PENDING", 0);
        pending.setAssigneeUserId(9L);
        pending.setSiteResultCode("READY");
        PreparationItemDO retained = item(102L, "REQUIRED", "CONFIRMED", 0);
        retained.setAssigneeUserId(9L);
        retained.setSiteResultCode("READY");
        DynamicFormInstanceDO pendingForm = form(201L, 101L);
        DynamicFormInstanceDO retainedForm = form(202L, 102L);
        retainedForm.setStatusCode("FROZEN");
        retainedForm.setFrozenAt(LocalDateTime.now().minusMinutes(1));
        retainedForm.setFrozenBy(7L);
        stubRows(preparation, List.of(pending, retained), List.of(pendingForm, retainedForm));
        when(formMapper.freezeIfMatch(any())).thenReturn(1);
        when(preparationMapper.updateLifecycleIfMatch(any())).thenReturn(1);

        var result = service.execute(command(PreparationReviewCommand.SUBMIT, null, 0, null, null), actor());

        assertEquals("PENDING_CONFIRMATION", result.statusCode());
        verify(formMapper).freezeIfMatch(any());
        verify(preparationMapper).updateLifecycleIfMatch(any());
        String audit = successFacts.get().detailSnapshot();
        assertTrue(audit.contains("\"formsAfter\""));
        assertTrue(audit.contains("\"status\":\"FROZEN\""));
    }

    @Test
    void lastItemConfirmationAggregatesPreparation() {
        PreparationDO preparation = preparation("PENDING_CONFIRMATION", 4, 2);
        PreparationItemDO selected = item(101L, "REQUIRED", "PENDING", 3);
        selected.setEvidenceReferenceSnapshot(evidence());
        PreparationItemDO confirmed = item(102L, "NOT_APPLICABLE_CONFIRMED", "CONFIRMED", 2);
        stubRows(preparation, List.of(selected, confirmed), List.of(form(201L, 101L), form(202L, 102L)));
        when(itemMapper.updateReviewIfMatch(any())).thenReturn(1);
        when(preparationMapper.invalidateReadinessIfMatch(any())).thenReturn(1);
        when(preparationMapper.updateLifecycleIfMatch(any())).thenReturn(1);
        when(fileArtifactApi.lockAndRevalidate(any())).thenReturn(fileFact());

        var result = service.execute(command(PreparationReviewCommand.CONFIRM, 101L, 4, 3, null), actor());

        assertEquals("CONFIRMED", result.statusCode());
        assertEquals(6, result.preparationVersion());
        verify(preparationMapper).invalidateReadinessIfMatch(any());
        verify(preparationMapper).updateLifecycleIfMatch(any());
        ArgumentCaptor<FileArtifactVersionRevalidationQuery> fileQuery =
                ArgumentCaptor.forClass(FileArtifactVersionRevalidationQuery.class);
        verify(fileArtifactApi).lockAndRevalidate(fileQuery.capture());
        assertEquals("READ", fileQuery.getValue().requiredAction());
        verify(sourceMapper).selectListForUpdate(any());
        String audit = successFacts.get().detailSnapshot();
        assertTrue(audit.contains("\"itemBefore\""));
        assertTrue(audit.contains("\"itemAfter\""));
        assertTrue(audit.contains("\"readinessBefore\""));
    }

    @Test
    void notApplicableConfirmationDoesNotRequireEvidence() {
        PreparationDO preparation = preparation("PENDING_CONFIRMATION", 4, 2);
        PreparationItemDO selected = item(101L, "NOT_APPLICABLE_PENDING", "PENDING", 3);
        selected.setNotApplicableReason("现场不涉及");
        selected.setEvidencePolicySnapshot("{\"required\":true}");
        stubRows(preparation, List.of(selected), List.of(form(201L, 101L)));
        when(itemMapper.updateReviewIfMatch(any())).thenReturn(1);
        when(preparationMapper.invalidateReadinessIfMatch(any())).thenReturn(1);
        when(preparationMapper.updateLifecycleIfMatch(any())).thenReturn(1);

        var result = service.execute(command(PreparationReviewCommand.CONFIRM_NOT_APPLICABLE,
                101L, 4, 3, "确认现场不涉及"), actor());

        assertEquals("CONFIRMED", result.statusCode());
        verify(fileArtifactApi, org.mockito.Mockito.never()).lockAndRevalidate(any());
        verify(itemMapper).updateReviewIfMatch(any());
    }

    @Test
    void staleSyncedSourceRejectsSubmitBeforeFreezingForms() {
        PreparationDO preparation = preparation("DRAFT", 1, 1);
        PreparationItemDO required = item(101L, "REQUIRED", "PENDING", 1);
        required.setAssigneeUserId(9L);
        required.setSiteResultCode("READY");
        required.setEvidencePolicySnapshot("{\"required\":true}");
        required.setEvidenceReferenceSnapshot(evidence());
        stubRows(preparation, List.of(required), List.of(form(201L, 101L)));
        when(sourceMapper.selectListForUpdate(any())).thenReturn(List.of(source(101L)));
        when(fileArtifactApi.lockAndRevalidate(any())).thenReturn(fileFact());
        when(sourceProviderRegistry.lockAndRevalidate(any())).thenThrow(new IllegalStateException("stale"));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> service.execute(command(PreparationReviewCommand.SUBMIT, null, 1, null, null), actor()));

        verify(formMapper, org.mockito.Mockito.never()).freezeIfMatch(any());
        verify(preparationMapper, org.mockito.Mockito.never()).updateLifecycleIfMatch(any());
        org.mockito.InOrder lockOrder = org.mockito.Mockito.inOrder(fileArtifactApi, sourceProviderRegistry);
        lockOrder.verify(fileArtifactApi).lockAndRevalidate(any());
        lockOrder.verify(sourceProviderRegistry).lockAndRevalidate(any());
        org.junit.jupiter.api.Assertions.assertNull(successFacts.get());
        ArgumentCaptor<cn.iocoder.yudao.module.pms.engineering.api.source.dto.PreparationSourceFactRevalidationQuery>
                query = ArgumentCaptor.forClass(
                cn.iocoder.yudao.module.pms.engineering.api.source.dto.PreparationSourceFactRevalidationQuery.class);
        verify(sourceProviderRegistry).lockAndRevalidate(query.capture());
        assertEquals("APPROVED", query.getValue().expectedNormalizedResultCode());
        assertEquals("F1", query.getValue().expectedSourceFactVersion());
        assertEquals("W1", query.getValue().expectedSourceWatermark());
    }

    @Test
    void staleSyncedSourceRejectsConfirmBeforeReviewCas() {
        PreparationDO preparation = preparation("PENDING_CONFIRMATION", 4, 2);
        PreparationItemDO selected = item(101L, "REQUIRED", "PENDING", 3);
        stubRows(preparation, List.of(selected), List.of(form(201L, 101L)));
        when(sourceMapper.selectListForUpdate(any())).thenReturn(List.of(source(101L)));
        when(sourceProviderRegistry.lockAndRevalidate(any())).thenThrow(new IllegalStateException("stale"));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> service.execute(command(PreparationReviewCommand.CONFIRM, 101L, 4, 3, null), actor()));

        verify(itemMapper, org.mockito.Mockito.never()).updateReviewIfMatch(any());
        verify(preparationMapper, org.mockito.Mockito.never()).invalidateReadinessIfMatch(any());
    }

    @Test
    void returnCreatesNewCurrentDraftAndResetsOnlyReturnedItem() {
        PreparationDO preparation = preparation("CONFIRMED", 5, 2);
        preparation.setBusinessVersion(1);
        PreparationItemDO returned = item(101L, "REQUIRED", "CONFIRMED", 2);
        PreparationItemDO retained = item(102L, "NOT_APPLICABLE_CONFIRMED", "CONFIRMED", 2);
        stubRows(preparation, List.of(returned, retained), List.of(form(201L, 101L), form(202L, 102L)));
        PreparationSourceReferenceDO source = new PreparationSourceReferenceDO();
        source.setItemId(101L); source.setSourceTypeCode("OA"); source.setSourceReferenceKey("OA-1");
        source.setSyncStatusCode("SUCCESS"); source.setVersion(1);
        when(sourceMapper.selectListForUpdate(any())).thenReturn(List.of(source));
        when(itemMapper.updateReviewIfMatch(any())).thenReturn(1);
        when(preparationMapper.updateLifecycleIfMatch(any())).thenReturn(1);
        when(preparationMapper.clearCurrentMarkerIfMatch(any())).thenReturn(1);
        when(preparationMapper.insert(any())).thenAnswer(invocation -> {
            PreparationDO row = invocation.getArgument(0); row.setId(2L); return 1;
        });
        when(itemMapper.insert(any())).thenAnswer(invocation -> {
            PreparationItemDO row = invocation.getArgument(0); row.setId(1000L + row.getSourceItemId()); return 1;
        });
        when(formMapper.insert(any())).thenReturn(1);
        when(sourceMapper.insert(any())).thenReturn(1);

        var result = service.execute(command(PreparationReviewCommand.RETURN, 101L, 5, 2, "资料需补充"), actor());

        assertEquals("DRAFT", result.statusCode());
        assertEquals(2, result.businessVersion());
        assertEquals(2L, result.currentPreparationId());
        ArgumentCaptor<PreparationItemDO> copied = ArgumentCaptor.forClass(PreparationItemDO.class);
        verify(itemMapper, org.mockito.Mockito.times(2)).insert(copied.capture());
        assertEquals("PENDING", copied.getAllValues().get(0).getConfirmationStatusCode());
        assertEquals("CONFIRMED", copied.getAllValues().get(1).getConfirmationStatusCode());
        ArgumentCaptor<PreparationSourceReferenceDO> copiedSource = ArgumentCaptor.forClass(PreparationSourceReferenceDO.class);
        verify(sourceMapper).insert(copiedSource.capture());
        assertEquals("UNKNOWN", copiedSource.getValue().getSyncStatusCode());
        String audit = successFacts.get().detailSnapshot();
        assertTrue(audit.contains("\"copyFacts\""));
        assertTrue(audit.contains("\"RESET_RETURNED\""));
        assertTrue(audit.contains("\"COPY_UNCHANGED\""));
    }

    private void stubRows(PreparationDO preparation, List<PreparationItemDO> items,
                          List<DynamicFormInstanceDO> forms) {
        when(preparationMapper.selectById(any())).thenReturn(preparation);
        when(preparationMapper.selectForUpdate(any())).thenReturn(preparation);
        when(itemMapper.selectListForUpdate(any())).thenReturn(items);
        when(formMapper.selectListForUpdate(any())).thenReturn(forms);
    }

    private PreparationReviewCommand command(String action, Long itemId, Integer preparationVersion,
                                             Integer itemVersion, String reason) {
        return new PreparationReviewCommand(action, 1L, itemId, preparationVersion, itemVersion,
                3, reason, "review-key-" + action);
    }

    private PreparationItemApplicationService.Actor actor() {
        return new PreparationItemApplicationService.Actor(1L, 7L, "review-op");
    }

    private PreparationDO preparation(String status, int version, int businessVersion) {
        PreparationDO row = new PreparationDO(); row.setId(1L); row.setTenantId(1L); row.setProjectId(10L);
        row.setBusinessVersion(businessVersion); row.setCurrentMarker(1); row.setStatusCode(status);
        row.setReadinessStatusCode("NOT_READY"); row.setInputVersion(1); row.setReadinessVersion(0);
        row.setSnapshotCurrent(false); row.setVersion(version); row.setSubmittedAt(LocalDateTime.now());
        return row;
    }

    private PreparationItemDO item(Long id, String applicability, String confirmation, int version) {
        PreparationItemDO row = new PreparationItemDO(); row.setId(id); row.setTenantId(1L); row.setPreparationId(1L);
        row.setItemCode("ITEM-" + id); row.setItemName("工勘项" + id); row.setSortOrder(id.intValue());
        row.setApplicabilityCode(applicability); row.setConfirmationStatusCode(confirmation);
        row.setFormCode("SITE_SURVEY_COMMON"); row.setFormVersion(1);
        row.setFormSchemaSnapshot(schema()); row.setEvidencePolicySnapshot("{\"required\":false}");
        row.setSourcePolicySnapshot("{}"); row.setWaiverPolicySnapshot("{}"); row.setVersion(version);
        return row;
    }

    private DynamicFormInstanceDO form(Long id, Long itemId) {
        DynamicFormInstanceDO row = new DynamicFormInstanceDO(); row.setId(id); row.setTenantId(1L);
        row.setPreparationId(1L); row.setItemId(itemId); row.setFormCode("SITE_SURVEY_COMMON");
        row.setFormVersion(1); row.setSchemaSnapshot(schema()); row.setValueSnapshot("{\"siteCondition\":\"正常\"}");
        row.setStatusCode("DRAFT"); row.setVersion(1); return row;
    }

    private PreparationSourceReferenceDO source(Long itemId) {
        PreparationSourceReferenceDO row = new PreparationSourceReferenceDO();
        row.setId(301L); row.setItemId(itemId); row.setSourceTypeCode("OA");
        row.setSourceObjectType("REQUEST"); row.setSourceObjectId("OA-1");
        row.setSourceReferenceKey("REF-1"); row.setNormalizedResultCode("APPROVED");
        row.setSourceFactVersion("F1"); row.setSourceWatermark("W1");
        row.setSyncStatusCode("SYNCED"); row.setVersion(1); return row;
    }

    private String schema() {
        return "{\"schemaVersion\":1,\"formCode\":\"SITE_SURVEY_COMMON\",\"formVersion\":1,"
                + "\"fields\":[{\"fieldCode\":\"siteCondition\",\"fieldType\":\"TEXT\","
                + "\"required\":true,\"maxLength\":200,\"options\":[],\"sortOrder\":1}]}";
    }

    private String evidence() {
        return "[{\"artifactId\":301,\"versionNo\":2,\"referenceKey\":\"SITE\","
                + "\"fileFactVersion\":{\"artifactVersion\":1,\"referenceVersion\":2,"
                + "\"availabilityVersion\":3},\"scopeVersion\":3}]";
    }

    private FileArtifactVersionFact fileFact() {
        return new FileArtifactVersionFact(301L, 2, "SITE", "SURVEY", "site.pdf", 100L,
                "application/pdf", "sha", "AVAILABLE", "ACTIVE", new FileFactVersion(1, 2, 3), 3L);
    }
}
