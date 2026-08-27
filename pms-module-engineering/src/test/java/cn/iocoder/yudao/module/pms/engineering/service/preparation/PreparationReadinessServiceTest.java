package cn.iocoder.yudao.module.pms.engineering.service.preparation;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.engineering.api.readiness.dto.ReadinessFactVector;
import cn.iocoder.yudao.module.pms.engineering.api.readiness.dto.SiteSurveyReadinessQuery;
import cn.iocoder.yudao.module.pms.engineering.api.readiness.dto.SiteSurveyReadinessRevalidationQuery;
import cn.iocoder.yudao.module.pms.engineering.api.source.dto.PreparationSourceFact;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.*;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.*;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.command.PreparationReadinessCommand;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.PREPARATION_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.PREPARATION_READINESS_VERSION_CONFLICT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PreparationReadinessServiceTest {

    @Mock PreparationMapper preparationMapper;
    @Mock PreparationItemMapper itemMapper;
    @Mock DynamicFormInstanceMapper formMapper;
    @Mock PreparationSourceReferenceMapper sourceMapper;
    @Mock PreparationItemWaiverMapper waiverMapper;
    @Mock PreparationReadinessSnapshotMapper snapshotMapper;
    @Mock ProjectScopeApi projectScopeApi;
    @Mock ProjectParticipantFactApi participantFactApi;
    @Mock PermissionApi permissionApi;
    @Mock FileArtifactApi fileArtifactApi;
    @Mock PreparationSourceProviderRegistry sourceProviderRegistry;
    @Mock PlatformCommandExecutionApi commandExecutionApi;
    @Mock OperationAuditApi operationAuditApi;
    @Mock TransactionTemplate transactionTemplate;
    @InjectMocks PreparationReadinessService service;
    AtomicReference<PlatformCommandExecutionApi.SuccessFacts> success = new AtomicReference<>();

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
            success.set(facts.apply(response));
            return new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW, response);
        });
    }

    @Test
    void evaluateAppendsReadySnapshotForConfirmedNoSourcePreparation() {
        PreparationDO preparation = preparation();
        stubLocked(preparation, item(1), form(1));
        when(snapshotMapper.insert(any())).thenAnswer(invocation -> {
            PreparationReadinessSnapshotDO row = invocation.getArgument(0); row.setId(91L); return 1;
        });
        when(preparationMapper.updateReadinessIfMatch(any())).thenReturn(1);

        var result = service.evaluate(new PreparationReadinessCommand(1L, 4, 2, "ready-key"), actor());

        assertEquals("READY", result.readiness().readinessStatus());
        assertTrue(result.readiness().snapshotCurrent());
        assertFalse(result.replayed());
        assertEquals(91L, result.readiness().latestSnapshotId());
        assertEquals(List.of(), result.readiness().blockerCodes());
        ArgumentCaptor<PreparationReadinessSnapshotDO> snapshot =
                ArgumentCaptor.forClass(PreparationReadinessSnapshotDO.class);
        verify(snapshotMapper).insert(snapshot.capture());
        assertEquals("READY", snapshot.getValue().getResultCode());
        assertEquals(5, snapshot.getValue().getPreparationVersion());
        assertEquals(2, snapshot.getValue().getReadinessVersion());
        assertTrue(success.get().detailSnapshot().contains("\"snapshotId\":91"));
    }

    @Test
    void inspectDetectsChangedScopeWithoutWriting() {
        PreparationDO preparation = preparation();
        preparation.setLatestReadinessSnapshotId(91L);
        preparation.setSnapshotCurrent(true);
        when(preparationMapper.selectById(any())).thenReturn(preparation);
        when(itemMapper.selectList(any())).thenReturn(List.of(item(1)));
        when(formMapper.selectList(any())).thenReturn(List.of(form(1)));
        when(sourceMapper.selectList(any())).thenReturn(List.of());
        when(waiverMapper.selectList(any())).thenReturn(List.of());
        PreparationReadinessSnapshotDO old = snapshot(preparation, 2L);
        when(snapshotMapper.selectById(any())).thenReturn(old);

        var fact = service.inspect(new SiteSurveyReadinessQuery(10L, 1L), 1L, 7L);

        assertEquals("NOT_READY", fact.readinessStatus());
        assertFalse(fact.snapshotCurrent());
        assertEquals(3L, fact.projectScopeVersion());
        verify(snapshotMapper, never()).insert(any());
        verify(preparationMapper, never()).updateReadinessIfMatch(any());
    }

    @Test
    void lockAndRevalidateAcceptsCurrentReadyVector() {
        PreparationDO preparation = preparation();
        preparation.setLatestReadinessSnapshotId(91L);
        preparation.setSnapshotCurrent(true);
        preparation.setReadinessStatusCode("READY");
        stubLocked(preparation, item(1), form(1));
        PreparationReadinessSnapshotDO snapshot = snapshot(preparation, 3L);
        when(snapshotMapper.selectById(any())).thenReturn(snapshot);
        ReadinessFactVector vector = new ReadinessFactVector(1, 3L,
                JsonUtils.parseArray(snapshot.getItemFactsSnapshot(),
                        cn.iocoder.yudao.module.pms.engineering.api.readiness.dto.ReadinessItemFact.class),
                List.of(), List.of(), List.of());

        var fact = service.lockAndRevalidate(new SiteSurveyReadinessRevalidationQuery(
                10L, 1L, 1, 1, 4, 1, 91L, 3L, vector), 1L, 7L);

        assertEquals("READY", fact.readinessStatus());
        assertTrue(fact.snapshotCurrent());
        verify(itemMapper).selectListForUpdate(any());
        verify(snapshotMapper, never()).insert(any());
    }

    @Test
    void evaluateFailsClosedWhenOaSourceIsRequired() {
        PreparationDO preparation = preparation();
        PreparationItemDO item = item(1);
        item.setSourcePolicySnapshot("{\"requirementCode\":\"OA_REQUIRED\"}");
        stubLocked(preparation, item, form(1));
        when(snapshotMapper.insert(any())).thenAnswer(invocation -> {
            PreparationReadinessSnapshotDO row = invocation.getArgument(0); row.setId(92L); return 1;
        });
        when(preparationMapper.updateReadinessIfMatch(any())).thenReturn(1);

        var result = service.evaluate(new PreparationReadinessCommand(1L, 4, 2, "oa-key"), actor());

        assertEquals("NOT_READY", result.readiness().readinessStatus());
        assertEquals(List.of("SOURCE_PROVIDER_UNAVAILABLE"), result.readiness().blockerCodes());
        verify(snapshotMapper).insert(argThat(row -> "NOT_READY".equals(row.getResultCode())
                && row.getBlockersSnapshot().contains("SOURCE_PROVIDER_UNAVAILABLE")));
    }

    @Test
    void approvedCrossVersionWaiverCanReplaceUnavailableRequiredSource() {
        PreparationDO preparation = preparation();
        PreparationItemDO item = item(1);
        item.setItemCode("FIBER");
        item.setSourcePolicySnapshot("{\"requirementCode\":\"OA_REQUIRED\"}");
        stubLocked(preparation, item, form(1));
        PreparationItemWaiverDO waiver = new PreparationItemWaiverDO();
        waiver.setId(61L); waiver.setPreparationId(99L); waiver.setItemId(98L); waiver.setItemCode("FIBER");
        waiver.setWaiverNo(1); waiver.setStatusCode("APPROVED"); waiver.setVersion(2);
        waiver.setBlockerCodesSnapshot("[\"SOURCE_PROVIDER_UNAVAILABLE\"]");
        waiver.setValidFrom(LocalDateTime.now().minusHours(1)); waiver.setValidUntil(LocalDateTime.now().plusHours(1));
        when(waiverMapper.selectBusinessListForUpdate(any())).thenReturn(List.of(waiver));
        when(snapshotMapper.insert(any())).thenAnswer(invocation -> {
            PreparationReadinessSnapshotDO row = invocation.getArgument(0); row.setId(93L); return 1;
        });
        when(preparationMapper.updateReadinessIfMatch(any())).thenReturn(1);

        var result = service.evaluate(new PreparationReadinessCommand(1L, 4, 2, "waiver-key"), actor());

        assertEquals("READY", result.readiness().readinessStatus());
        assertTrue(result.readiness().blockerCodes().isEmpty());
        assertEquals(61L, result.readiness().factVector().waiverFacts().getFirst().waiverId());
    }

    @Test
    void syncedSourceProviderFactParticipatesInReadyVector() {
        PreparationDO preparation = preparation();
        PreparationItemDO item = item(1);
        item.setSourcePolicySnapshot("{\"requirementCode\":\"OA_REQUIRED\"}");
        PreparationSourceReferenceDO source = new PreparationSourceReferenceDO();
        source.setId(51L); source.setItemId(11L); source.setSourceTypeCode("OA");
        source.setSourceObjectType("REQUEST"); source.setSourceObjectId("OA-1");
        source.setSourceReferenceKey("REF-1"); source.setNormalizedResultCode("APPROVED");
        source.setSourceFactVersion("F1"); source.setSourceWatermark("W1");
        source.setSyncStatusCode("SYNCED"); source.setVersion(1);
        stubLocked(preparation, item, form(1));
        when(sourceMapper.selectListForUpdate(any())).thenReturn(List.of(source));
        when(sourceProviderRegistry.lockAndRevalidate(any())).thenReturn(new PreparationSourceFact(
                10L, 11L, "OA", "REQUEST", "OA-1", "REF-1", "APPROVED", "F1", "W1", true));
        when(snapshotMapper.insert(any())).thenAnswer(invocation -> {
            PreparationReadinessSnapshotDO row = invocation.getArgument(0); row.setId(94L); return 1;
        });
        when(preparationMapper.updateReadinessIfMatch(any())).thenReturn(1);

        var result = service.evaluate(new PreparationReadinessCommand(1L, 4, 2, "source-key"), actor());

        assertEquals("READY", result.readiness().readinessStatus());
        assertEquals("F1", result.readiness().factVector().sourceFacts().getFirst().sourceFactVersion());
    }

    @Test
    void providerUnavailableExpiresCurrentSnapshotAndEvaluateAppendsNotReady() {
        PreparationDO preparation = currentReadyPreparation();
        PreparationItemDO item = item(1);
        item.setSourcePolicySnapshot("{\"requirementCode\":\"OA_REQUIRED\"}");
        PreparationSourceReferenceDO source = syncedSource();
        stubCurrentFacts(preparation, item, form(1), List.of(source), List.of());
        when(snapshotMapper.selectById(any())).thenReturn(snapshotWithSource(preparation, 3L));
        when(sourceProviderRegistry.inspect(any())).thenThrow(new IllegalStateException("unavailable"));
        when(sourceProviderRegistry.lockAndRevalidate(any())).thenThrow(new IllegalStateException("unavailable"));
        when(snapshotMapper.insert(any())).thenAnswer(invocation -> {
            PreparationReadinessSnapshotDO row = invocation.getArgument(0); row.setId(95L); return 1;
        });
        when(preparationMapper.updateReadinessIfMatch(any())).thenReturn(1);

        var inspected = service.inspect(new SiteSurveyReadinessQuery(10L, 1L), 1L, 7L);
        var evaluated = service.evaluate(new PreparationReadinessCommand(1L, 4, 2, "provider-down"), actor());

        assertEquals(vector(snapshotWithSource(preparation, 3L)), inspected.factVector());
        assertFalse(inspected.snapshotCurrent());
        assertEquals(List.of("SOURCE_PROVIDER_UNAVAILABLE"), inspected.blockerCodes());
        assertFalse(evaluated.replayed());
        assertEquals("NOT_READY", evaluated.readiness().readinessStatus());
        verify(snapshotMapper).insert(argThat(row -> "NOT_READY".equals(row.getResultCode())));
    }

    @Test
    void expiredWaiverExpiresCurrentSnapshotAndEvaluateAppendsNotReady() {
        PreparationDO preparation = currentReadyPreparation();
        PreparationItemDO item = item(1);
        item.setItemCode("FIBER");
        item.setSourcePolicySnapshot("{\"requirementCode\":\"OA_REQUIRED\"}");
        LocalDateTime now = LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        PreparationItemWaiverDO waiver = waiver(now.minusHours(2), now.minusHours(1));
        stubCurrentFacts(preparation, item, form(1), List.of(), List.of(waiver));
        when(snapshotMapper.selectById(any())).thenReturn(snapshotWithWaiver(preparation, 3L, waiver));
        when(snapshotMapper.insert(any())).thenAnswer(invocation -> {
            PreparationReadinessSnapshotDO row = invocation.getArgument(0); row.setId(96L); return 1;
        });
        when(preparationMapper.updateReadinessIfMatch(any())).thenReturn(1);

        var inspected = service.inspect(new SiteSurveyReadinessQuery(10L, 1L), 1L, 7L);
        var evaluated = service.evaluate(new PreparationReadinessCommand(1L, 4, 2, "waiver-expired"), actor());

        assertEquals(vector(snapshotWithWaiver(preparation, 3L, waiver)), inspected.factVector());
        assertFalse(inspected.snapshotCurrent());
        assertEquals(List.of("SOURCE_PROVIDER_UNAVAILABLE"), inspected.blockerCodes());
        assertFalse(evaluated.replayed());
        assertEquals("NOT_READY", evaluated.readiness().readinessStatus());
        verify(snapshotMapper).insert(argThat(row -> "NOT_READY".equals(row.getResultCode())));
    }

    @Test
    void changedFileFactMakesInspectNotReadyAndLockedRevalidationRejectsWithoutWriting() {
        PreparationDO preparation = preparation();
        preparation.setLatestReadinessSnapshotId(91L);
        preparation.setSnapshotCurrent(true);
        preparation.setReadinessStatusCode("READY");
        PreparationItemDO item = itemWithEvidence();
        DynamicFormInstanceDO form = form(1);
        when(preparationMapper.selectById(any())).thenReturn(preparation);
        when(itemMapper.selectList(any())).thenReturn(List.of(item));
        when(formMapper.selectList(any())).thenReturn(List.of(form));
        when(sourceMapper.selectList(any())).thenReturn(List.of());
        when(waiverMapper.selectList(any())).thenReturn(List.of());
        stubLocked(preparation, item, form);
        PreparationReadinessSnapshotDO snapshot = snapshotWithFile(preparation, 3L);
        when(snapshotMapper.selectById(any())).thenReturn(snapshot);
        FileArtifactVersionFact changed = new FileArtifactVersionFact(301L, 2, "SITE", "SURVEY", "site.pdf",
                100L, "application/pdf", "sha", "UNAVAILABLE", "ACTIVE",
                new FileFactVersion(1, 2, 4), 1L);
        when(fileArtifactApi.inspect(any())).thenReturn(changed);
        when(fileArtifactApi.lockAndRevalidate(any())).thenReturn(changed);

        var inspected = service.inspect(new SiteSurveyReadinessQuery(10L, 1L), 1L, 7L);

        assertEquals("NOT_READY", inspected.readinessStatus());
        assertFalse(inspected.snapshotCurrent());
        assertEquals(List.of("FILE_FACT_CHANGED"), inspected.blockerCodes());
        ReadinessFactVector frozenVector = vector(snapshot);
        var error = assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.lockAndRevalidate(new SiteSurveyReadinessRevalidationQuery(
                        10L, 1L, 1, 1, 4, 1, 91L, 3L, frozenVector), 1L, 7L));
        assertEquals(PREPARATION_READINESS_VERSION_CONFLICT.getCode(), error.getCode());
        verify(snapshotMapper, never()).insert(any());
        verify(preparationMapper, never()).updateReadinessIfMatch(any());
    }

    @Test
    void crossTenantAndExpectedVersionChangesFailClosed() {
        when(preparationMapper.selectById(any())).thenReturn(null);
        var crossTenant = assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.inspect(new SiteSurveyReadinessQuery(10L, 1L), 2L, 7L));
        assertEquals(PREPARATION_NOT_EXISTS.getCode(), crossTenant.getCode());

        PreparationDO preparation = preparation();
        preparation.setLatestReadinessSnapshotId(91L);
        preparation.setSnapshotCurrent(true);
        preparation.setReadinessStatusCode("READY");
        stubLocked(preparation, item(1), form(1));
        PreparationReadinessSnapshotDO snapshot = snapshot(preparation, 3L);
        when(snapshotMapper.selectById(any())).thenReturn(snapshot);
        var versionConflict = assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.lockAndRevalidate(new SiteSurveyReadinessRevalidationQuery(
                        10L, 1L, 1, 1, 3, 1, 91L, 3L, vector(snapshot)), 1L, 7L));
        assertEquals(PREPARATION_READINESS_VERSION_CONFLICT.getCode(), versionConflict.getCode());
        var changedVector = new ReadinessFactVector(2, 3L, vector(snapshot).itemFacts(), List.of(), List.of(), List.of());
        var vectorConflict = assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.lockAndRevalidate(new SiteSurveyReadinessRevalidationQuery(
                        10L, 1L, 1, 1, 4, 1, 91L, 3L, changedVector), 1L, 7L));
        assertEquals(PREPARATION_READINESS_VERSION_CONFLICT.getCode(), vectorConflict.getCode());
    }

    private void stubLocked(PreparationDO preparation, PreparationItemDO item, DynamicFormInstanceDO form) {
        when(preparationMapper.selectById(any())).thenReturn(preparation);
        when(preparationMapper.selectForUpdate(any())).thenReturn(preparation);
        when(itemMapper.selectListForUpdate(any())).thenReturn(List.of(item));
        when(formMapper.selectListForUpdate(any())).thenReturn(List.of(form));
        when(sourceMapper.selectListForUpdate(any())).thenReturn(List.of());
        when(waiverMapper.selectListForUpdate(any())).thenReturn(List.of());
    }

    private void stubCurrentFacts(PreparationDO preparation, PreparationItemDO item, DynamicFormInstanceDO form,
            List<PreparationSourceReferenceDO> sources, List<PreparationItemWaiverDO> waivers) {
        when(preparationMapper.selectById(any())).thenReturn(preparation);
        when(preparationMapper.selectForUpdate(any())).thenReturn(preparation);
        when(itemMapper.selectList(any())).thenReturn(List.of(item));
        when(itemMapper.selectListForUpdate(any())).thenReturn(List.of(item));
        when(formMapper.selectList(any())).thenReturn(List.of(form));
        when(formMapper.selectListForUpdate(any())).thenReturn(List.of(form));
        when(sourceMapper.selectList(any())).thenReturn(sources);
        when(sourceMapper.selectListForUpdate(any())).thenReturn(sources);
        when(waiverMapper.selectBusinessList(any())).thenReturn(waivers);
        when(waiverMapper.selectBusinessListForUpdate(any())).thenReturn(waivers);
    }

    private PreparationDO currentReadyPreparation() {
        PreparationDO row = preparation();
        row.setLatestReadinessSnapshotId(91L); row.setSnapshotCurrent(true); row.setReadinessStatusCode("READY");
        return row;
    }

    private PreparationSourceReferenceDO syncedSource() {
        PreparationSourceReferenceDO row = new PreparationSourceReferenceDO();
        row.setId(51L); row.setItemId(11L); row.setSourceTypeCode("OA");
        row.setSourceObjectType("REQUEST"); row.setSourceObjectId("OA-1"); row.setSourceReferenceKey("REF-1");
        row.setNormalizedResultCode("APPROVED"); row.setSourceFactVersion("F1"); row.setSourceWatermark("W1");
        row.setSyncStatusCode("SYNCED"); row.setVersion(1); return row;
    }

    private PreparationItemWaiverDO waiver(LocalDateTime validFrom, LocalDateTime validUntil) {
        PreparationItemWaiverDO row = new PreparationItemWaiverDO();
        row.setId(61L); row.setPreparationId(99L); row.setItemId(98L); row.setItemCode("FIBER");
        row.setWaiverNo(1); row.setStatusCode("APPROVED"); row.setVersion(2);
        row.setBlockerCodesSnapshot("[\"SOURCE_PROVIDER_UNAVAILABLE\"]");
        row.setValidFrom(validFrom); row.setValidUntil(validUntil); return row;
    }

    private PreparationDO preparation() {
        PreparationDO row = new PreparationDO(); row.setId(1L); row.setTenantId(1L); row.setProjectId(10L);
        row.setBusinessVersion(1); row.setCurrentMarker(1); row.setStatusCode("CONFIRMED");
        row.setReadinessStatusCode("NOT_READY"); row.setInputVersion(1); row.setReadinessVersion(1);
        row.setSnapshotCurrent(false); row.setVersion(4); return row;
    }

    private PreparationItemDO item(int version) {
        PreparationItemDO row = new PreparationItemDO(); row.setId(11L); row.setPreparationId(1L);
        row.setItemCode("POWER"); row.setApplicabilityCode("REQUIRED"); row.setConfirmationStatusCode("CONFIRMED");
        row.setOutsourced(false); row.setAssigneeUserId(8L); row.setVersion(version);
        row.setEvidencePolicySnapshot("{\"required\":false}");
        row.setSourcePolicySnapshot("{\"requirementCode\":\"NONE\"}"); return row;
    }

    private PreparationItemDO itemWithEvidence() {
        PreparationItemDO row = item(1);
        row.setEvidencePolicySnapshot("{\"required\":true}");
        row.setEvidenceReferenceSnapshot("[{\"artifactId\":301,\"versionNo\":2,\"referenceKey\":\"SITE\","
                + "\"fileFactVersion\":{\"artifactVersion\":1,\"referenceVersion\":2,"
                + "\"availabilityVersion\":3},\"scopeVersion\":1}]");
        return row;
    }

    private DynamicFormInstanceDO form(int version) {
        DynamicFormInstanceDO row = new DynamicFormInstanceDO(); row.setId(21L); row.setItemId(11L);
        row.setFormCode("POWER"); row.setFormVersion(1); row.setVersion(version); row.setStatusCode("FROZEN");
        row.setFrozenAt(LocalDateTime.now()); row.setSchemaSnapshot(schema());
        row.setValueSnapshot("{\"siteCondition\":\"正常\"}"); return row;
    }

    private PreparationReadinessSnapshotDO snapshot(PreparationDO preparation, Long scopeVersion) {
        PreparationReadinessSnapshotDO row = new PreparationReadinessSnapshotDO(); row.setId(91L);
        row.setPreparationId(1L); row.setSnapshotNo(1); row.setResultCode("READY"); row.setRuleVersion(1);
        row.setProjectScopeVersion(scopeVersion); row.setInputVersion(1); row.setPreparationVersion(4);
        row.setReadinessVersion(1);
        row.setItemFactsSnapshot("[{\"itemId\":11,\"itemCode\":\"POWER\",\"itemVersion\":1,"
                + "\"applicabilityCode\":\"REQUIRED\",\"confirmationStatusCode\":\"CONFIRMED\","
                + "\"outsourced\":false,\"assigneeUserId\":8,\"formInstanceId\":21,"
                + "\"formCode\":\"POWER\",\"formDefinitionVersion\":1,\"formInstanceVersion\":1,"
                + "\"formStatusCode\":\"FROZEN\"}]");
        row.setFileFactsSnapshot("[]"); row.setSourceFactsSnapshot("[]"); row.setWaiverFactsSnapshot("[]");
        row.setBlockersSnapshot("[]"); return row;
    }

    private PreparationReadinessSnapshotDO snapshotWithFile(PreparationDO preparation, Long scopeVersion) {
        PreparationReadinessSnapshotDO row = snapshot(preparation, scopeVersion);
        row.setFileFactsSnapshot("[{\"itemId\":11,\"artifactId\":301,\"versionNo\":2,"
                + "\"referenceKey\":\"SITE\",\"artifactVersion\":1,\"referenceVersion\":2,"
                + "\"availabilityVersion\":3,\"scopeVersion\":1,\"availabilityStatus\":\"AVAILABLE\","
                + "\"referenceStatus\":\"ACTIVE\"}]");
        return row;
    }

    private PreparationReadinessSnapshotDO snapshotWithSource(PreparationDO preparation, Long scopeVersion) {
        PreparationReadinessSnapshotDO row = snapshot(preparation, scopeVersion);
        row.setSourceFactsSnapshot("[{\"sourceReferenceId\":51,\"itemId\":11,\"sourceTypeCode\":\"OA\"," +
                "\"sourceReferenceKey\":\"REF-1\",\"normalizedResultCode\":\"APPROVED\"," +
                "\"sourceFactVersion\":\"F1\",\"sourceWatermark\":\"W1\",\"syncStatusCode\":\"SYNCED\"," +
                "\"version\":1}]");
        return row;
    }

    private PreparationReadinessSnapshotDO snapshotWithWaiver(PreparationDO preparation, Long scopeVersion,
            PreparationItemWaiverDO waiver) {
        PreparationReadinessSnapshotDO row = snapshot(preparation, scopeVersion);
        row.setItemFactsSnapshot(row.getItemFactsSnapshot().replace("\"itemCode\":\"POWER\"",
                "\"itemCode\":\"FIBER\""));
        row.setWaiverFactsSnapshot(JsonUtils.toJsonString(List.of(new cn.iocoder.yudao.module.pms.engineering.api.readiness.dto.ReadinessWaiverFact(
                waiver.getId(), waiver.getItemId(), waiver.getItemCode(), waiver.getWaiverNo(),
                waiver.getStatusCode(), waiver.getBlockerCodesSnapshot(), waiver.getValidFrom(),
                waiver.getValidUntil(), waiver.getVersion()))));
        return row;
    }

    private ReadinessFactVector vector(PreparationReadinessSnapshotDO snapshot) {
        return new ReadinessFactVector(snapshot.getInputVersion(), snapshot.getProjectScopeVersion(),
                JsonUtils.parseArray(snapshot.getItemFactsSnapshot(),
                        cn.iocoder.yudao.module.pms.engineering.api.readiness.dto.ReadinessItemFact.class),
                JsonUtils.parseArray(snapshot.getFileFactsSnapshot(),
                        cn.iocoder.yudao.module.pms.engineering.api.readiness.dto.ReadinessFileFact.class),
                JsonUtils.parseArray(snapshot.getSourceFactsSnapshot(),
                        cn.iocoder.yudao.module.pms.engineering.api.readiness.dto.ReadinessSourceFact.class),
                JsonUtils.parseArray(snapshot.getWaiverFactsSnapshot(),
                        cn.iocoder.yudao.module.pms.engineering.api.readiness.dto.ReadinessWaiverFact.class));
    }

    private String schema() {
        return "{\"schemaVersion\":1,\"formCode\":\"POWER\",\"formVersion\":1,"
                + "\"fields\":[{\"fieldCode\":\"siteCondition\",\"fieldType\":\"TEXT\","
                + "\"required\":true,\"maxLength\":200,\"options\":[],\"sortOrder\":1}]}";
    }

    private PreparationItemApplicationService.Actor actor() {
        return new PreparationItemApplicationService.Actor(1L, 7L, "ready-op");
    }
}
