package cn.iocoder.yudao.module.pms.engineering.service.preparation;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.engineering.api.readiness.dto.ReadinessFactVector;
import cn.iocoder.yudao.module.pms.engineering.api.readiness.dto.SiteSurveyReadinessQuery;
import cn.iocoder.yudao.module.pms.engineering.api.readiness.dto.SiteSurveyReadinessRevalidationQuery;
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

    private ReadinessFactVector vector(PreparationReadinessSnapshotDO snapshot) {
        return new ReadinessFactVector(snapshot.getInputVersion(), snapshot.getProjectScopeVersion(),
                JsonUtils.parseArray(snapshot.getItemFactsSnapshot(),
                        cn.iocoder.yudao.module.pms.engineering.api.readiness.dto.ReadinessItemFact.class),
                JsonUtils.parseArray(snapshot.getFileFactsSnapshot(),
                        cn.iocoder.yudao.module.pms.engineering.api.readiness.dto.ReadinessFileFact.class),
                List.of(), List.of());
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
