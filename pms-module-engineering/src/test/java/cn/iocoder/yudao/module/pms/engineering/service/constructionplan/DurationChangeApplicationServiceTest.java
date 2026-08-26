package cn.iocoder.yudao.module.pms.engineering.service.constructionplan;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.constructionplan.vo.ConstructionPlanChangeRespVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanChangeDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanRevisionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.ConstructionPlanChangeMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.ConstructionPlanMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.ConstructionPlanRevisionMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanChangeDraftUpdate;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanRevisionDraftUpdate;
import cn.iocoder.yudao.module.pms.engineering.service.constructionplan.command.CreateDurationChangeCommand;
import cn.iocoder.yudao.module.pms.engineering.service.constructionplan.command.PatchDurationChangeCommand;
import cn.iocoder.yudao.module.pms.engineering.service.constructionplan.patch.DurationChangePatch;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DurationChangeApplicationServiceTest {

    @Mock ConstructionPlanMapper planMapper;
    @Mock ConstructionPlanRevisionMapper revisionMapper;
    @Mock ConstructionPlanChangeMapper changeMapper;
    @Mock PlatformCommandExecutionApi commandExecutionApi;
    @Mock OperationAuditApi operationAuditApi;
    @Mock PermissionApi permissionApi;
    @Mock ProjectScopeApi projectScopeApi;
    @Mock ProjectParticipantFactApi participantFactApi;
    @Mock TransactionTemplate transactionTemplate;
    private DurationChangeApplicationService service;

    @BeforeEach
    void setUp() {
        service = new DurationChangeApplicationService(planMapper, revisionMapper, changeMapper,
                commandExecutionApi, operationAuditApi, permissionApi, projectScopeApi,
                participantFactApi, transactionTemplate);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(org.mockito.Mockito.mock(TransactionStatus.class));
        });
    }

    @Test
    void createsDraftAgainstCurrentRevisionWithoutPendingPointerOrBpm() {
        stubAuthorizedPlan();
        when(revisionMapper.selectLatestForUpdate(any())).thenReturn(revision(701L, 1));
        when(revisionMapper.insert(any())).thenReturn(1);
        when(changeMapper.insert(any())).thenReturn(1);
        when(commandExecutionApi.execute(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            Supplier<?> operation = invocation.getArgument(3);
            Object response = operation.get();
            Function<Object, PlatformCommandExecutionApi.SuccessFacts> facts = invocation.getArgument(4);
            assertNotNull(facts.apply(response).detailSnapshot());
            return new PlatformCommandExecutionApi.ExecutionResult<>(
                    PlatformCommandExecutionApi.Decision.NEW, response);
        });

        ConstructionPlanChangeRespVO response = service.createDraft(createCommand(), actor());

        assertEquals("DRAFT", response.getStatus());
        assertEquals(701L, response.getBaseRevisionId());
        assertEquals("DURATION_FROM_START", response.getCandidateRevision().getCalculationBasis());
        assertEquals(LocalDate.of(2026, 9, 12), response.getCandidateRevision().getEndDate());
        ArgumentCaptor<ConstructionPlanRevisionDO> revision =
                ArgumentCaptor.forClass(ConstructionPlanRevisionDO.class);
        ArgumentCaptor<ConstructionPlanChangeDO> change =
                ArgumentCaptor.forClass(ConstructionPlanChangeDO.class);
        verify(revisionMapper).insert(revision.capture());
        verify(changeMapper).insert(change.capture());
        assertEquals(change.getValue().getId(), revision.getValue().getSourceChangeId());
        assertEquals(revision.getValue().getId(), change.getValue().getCandidateRevisionId());
        verify(planMapper, never()).updateVersionIfMatch(any());
    }

    @Test
    void patchesOnlyDurationAndAdvancesChangeVersion() {
        stubPatchRows();
        when(revisionMapper.updateDraftIfMatch(any())).thenReturn(1);
        when(changeMapper.updateDraftIfMatch(any())).thenReturn(1);
        DurationChangePatch patch = new DurationChangePatch(null, null, null, 7,
                null, null, null, null, Set.of("durationDays"));

        ConstructionPlanChangeRespVO response = service.patchDraft(
                patchCommand(patch), actor());

        ArgumentCaptor<ConstructionPlanRevisionDraftUpdate> revisionUpdate =
                ArgumentCaptor.forClass(ConstructionPlanRevisionDraftUpdate.class);
        ArgumentCaptor<ConstructionPlanChangeDraftUpdate> changeUpdate =
                ArgumentCaptor.forClass(ConstructionPlanChangeDraftUpdate.class);
        verify(revisionMapper).updateDraftIfMatch(revisionUpdate.capture());
        verify(changeMapper).updateDraftIfMatch(changeUpdate.capture());
        assertEquals(LocalDate.of(2026, 9, 16), revisionUpdate.getValue().endDate());
        assertEquals(Set.of(), changeUpdate.getValue().submittedFields());
        assertEquals(1, response.getVersion());
        assertEquals(1, response.getCandidateRevision().getVersion());
    }

    @Test
    void patchesOnlyReasonWithoutUpdatingCandidateRevision() {
        stubPatchRows();
        when(changeMapper.updateDraftIfMatch(any())).thenReturn(1);
        DurationChangePatch patch = new DurationChangePatch(null, null, null, null,
                null, "  调整说明  ", null, null, Set.of("reasonDetail"));

        ConstructionPlanChangeRespVO response = service.patchDraft(patchCommand(patch), actor());

        verify(revisionMapper, never()).updateDraftIfMatch(any());
        assertEquals("调整说明", response.getReasonDetail());
    }

    @Test
    void nullEvidenceFieldClearsTheWholeReferencePair() {
        stubPatchRows();
        when(changeMapper.updateDraftIfMatch(any())).thenReturn(1);
        DurationChangePatch patch = new DurationChangePatch(null, null, null, null,
                null, null, null, null, Set.of("customerEvidenceFileId"));

        service.patchDraft(patchCommand(patch), actor());

        ArgumentCaptor<ConstructionPlanChangeDraftUpdate> update =
                ArgumentCaptor.forClass(ConstructionPlanChangeDraftUpdate.class);
        verify(changeMapper).updateDraftIfMatch(update.capture());
        assertNull(update.getValue().customerEvidenceFileId());
        assertNull(update.getValue().customerEvidenceFileVersion());
        assertEquals(Set.of("customerEvidence"), update.getValue().submittedFields());
    }

    @Test
    void rejectsEmptyPatchBeforeAnyBusinessRead() {
        DurationChangePatch patch = new DurationChangePatch(null, null, null, null,
                null, null, null, null, Set.of());

        assertThrows(ServiceException.class,
                () -> service.patchDraft(patchCommand(patch), actor()));

        verify(planMapper, never()).selectById(any());
        verify(operationAuditApi).record(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsStaleBaseWithoutUpdatingDraft() {
        stubAuthorizedPlan();
        ConstructionPlanChangeDO change = change();
        change.setBaseRevisionId(700L);
        when(changeMapper.selectForUpdate(any())).thenReturn(change);

        assertThrows(ServiceException.class, () -> service.patchDraft(patchCommand(
                new DurationChangePatch(null, null, null, null, null, "x", null, null,
                        Set.of("reasonDetail"))), actor()));

        verify(changeMapper, never()).updateDraftIfMatch(any());
    }

    private void stubAuthorizedPlan() {
        when(permissionApi.hasAnyPermissions(9L,
                ConstructionPlanApplicationService.PERMISSION_MANAGE)).thenReturn(true);
        when(planMapper.selectById(any())).thenReturn(plan());
        when(projectScopeApi.resolveCurrent(any())).thenReturn(
                new ProjectScopeResult(100L, 7L, Set.of(100L), Set.of()));
        when(participantFactApi.lockAndRevalidate(any())).thenReturn(null);
        when(planMapper.selectForUpdate(any())).thenReturn(plan());
    }

    private void stubPatchRows() {
        stubAuthorizedPlan();
        when(changeMapper.selectForUpdate(any())).thenReturn(change());
        when(revisionMapper.selectForUpdate(any())).thenReturn(candidate());
    }

    private ConstructionPlanDO plan() {
        ConstructionPlanDO plan = new ConstructionPlanDO();
        plan.setId(501L);
        plan.setProjectId(100L);
        plan.setCurrentDurationRevisionId(701L);
        plan.setPlanRecalculationStatusCode(ConstructionPlanDO.RECALCULATION_PENDING);
        plan.setPlanRecalculationSourceRevisionId(701L);
        plan.setVersion(1);
        plan.setTenantId(0L);
        return plan;
    }

    private ConstructionPlanRevisionDO revision(Long id, Integer no) {
        ConstructionPlanRevisionDO row = new ConstructionPlanRevisionDO();
        row.setId(id);
        row.setPlanId(501L);
        row.setRevisionNo(no);
        row.setCalculationBasisCode("DATE_RANGE");
        row.setStartDate(LocalDate.of(2026, 9, 1));
        row.setEndDate(LocalDate.of(2026, 9, 5));
        row.setDurationDays(5);
        row.setCreatedBy(9L);
        row.setCreatedAt(LocalDateTime.of(2026, 8, 26, 15, 0));
        row.setVersion(0);
        row.setTenantId(0L);
        return row;
    }

    private ConstructionPlanRevisionDO candidate() {
        ConstructionPlanRevisionDO row = revision(702L, 2);
        row.setCalculationBasisCode("DURATION_FROM_START");
        row.setStartDate(LocalDate.of(2026, 9, 10));
        row.setEndDate(LocalDate.of(2026, 9, 14));
        row.setDurationDays(5);
        row.setSourceChangeId(801L);
        return row;
    }

    private ConstructionPlanChangeDO change() {
        ConstructionPlanChangeDO row = new ConstructionPlanChangeDO();
        row.setId(801L);
        row.setPlanId(501L);
        row.setBaseRevisionId(701L);
        row.setCandidateRevisionId(702L);
        row.setStatusCode(ConstructionPlanChangeDO.STATUS_DRAFT);
        row.setReasonTypeCode("CUSTOMER_DELAY");
        row.setReasonDetail("原说明");
        row.setCustomerEvidenceRequired(false);
        row.setCustomerEvidenceFileId(901L);
        row.setCustomerEvidenceFileVersion(2);
        row.setApplicantUserId(9L);
        row.setCreatedAt(LocalDateTime.of(2026, 8, 26, 15, 1));
        row.setVersion(0);
        row.setTenantId(0L);
        return row;
    }

    private CreateDurationChangeCommand createCommand() {
        return new CreateDurationChangeCommand(501L, 1, 3, "DURATION_FROM_START",
                LocalDate.of(2026, 9, 10), null, 3, "CUSTOMER_DELAY", "原因",
                null, null, "idem-1", "a".repeat(64));
    }

    private PatchDurationChangeCommand patchCommand(DurationChangePatch patch) {
        return new PatchDurationChangeCommand(501L, 801L, 0, 3, patch);
    }

    private ConstructionPlanApplicationService.Actor actor() {
        return new ConstructionPlanApplicationService.Actor(0L, 9L, "corr-task5");
    }
}
