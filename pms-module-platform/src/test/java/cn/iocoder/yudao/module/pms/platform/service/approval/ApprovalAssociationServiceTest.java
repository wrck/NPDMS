package cn.iocoder.yudao.module.pms.platform.service.approval;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessEntityAccessPort;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessEntityData;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.execution.ApprovalExecutionPort;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.execution.ApprovalSubmissionRequest;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.execution.ApprovalTrustedResult;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelKind;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessOperationDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.BusinessOperationReceipt;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.BusinessOperationRequest;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.OperationEntryKind;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.ReceiptOutcome;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformBusinessEventApi;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.approval.ApprovalAttemptDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.approval.ApprovalEffectDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.approval.ApprovalOpinionDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.approval.ApprovalAttemptMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.approval.ApprovalEffectMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.approval.ApprovalOpinionMapper;
import cn.iocoder.yudao.module.pms.platform.support.service.BusinessOperationDispatcher;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApprovalAssociationServiceTest {

    private static final String OWNER = "demo";
    private static final String TYPE = "ticket";
    private static final Long ENTITY_ID = 2L;
    private static final EntityRef SUBJECT = new EntityRef(1L, OWNER, TYPE, ENTITY_ID);

    @Mock
    private ApprovalAttemptMapper attemptMapper;
    @Mock
    private ApprovalOpinionMapper opinionMapper;
    @Mock
    private ApprovalEffectMapper effectMapper;
    @Mock
    private ApprovalBackendRegistry backendRegistry;
    @Mock
    private ApprovalExecutionPort backend;
    @Mock
    private PlatformBusinessEventApi outbox;
    @Mock
    private cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelCatalog catalog;
    @Mock
    private BusinessEntityAccessPort accessPort;
    @Mock
    private BusinessOperationDispatcher dispatcher;

    private ApprovalAssociationService service;
    private ApprovalAttemptDO attempt;

    @BeforeEach
    void setUp() {
        service = new ApprovalAssociationService(attemptMapper, opinionMapper, effectMapper,
                backendRegistry, outbox, catalog, accessPort, dispatcher);
        lenient().when(backendRegistry.requireActive()).thenReturn(backend);
        lenient().when(backend.backendId()).thenReturn("approval-local");
        lenient().when(backend.submit(any(ApprovalSubmissionRequest.class)))
                .thenReturn("al-instance-1");
        attempt = attempt(10L, ApprovalAttemptDO.STATUS_PENDING);
        lenient().when(attemptMapper.selectById(10L)).thenReturn(attempt);
        lenient().when(attemptMapper.selectBySubjectAndAttempt(OWNER, TYPE, ENTITY_ID, "TICKET_CLOSE",
                "att-1")).thenReturn(Optional.empty());
        lenient().when(attemptMapper.selectByInstanceRef("al-instance-1"))
                .thenReturn(List.of(attempt));
        lenient().when(accessPort.read(any(), any(), anyString())).thenReturn(new BusinessEntityData(
                SUBJECT, null, Map.of(), 5L, true, null));
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private ApprovalAttemptDO attempt(Long id, String status) {
        ApprovalAttemptDO row = new ApprovalAttemptDO();
        row.setId(id);
        row.setOwnerModule(OWNER);
        row.setEntityType(TYPE);
        row.setEntityId(ENTITY_ID);
        row.setPurpose("TICKET_CLOSE");
        row.setAttemptId("att-1");
        row.setSubmissionBasis("basis-1");
        row.setNeutralProcessRef("proc/ticket-close");
        row.setBackendId("approval-local");
        row.setInstanceRef("al-instance-1");
        row.setStatus(status);
        row.setVersion(0);
        row.setTenantId(1L);
        return row;
    }

    private BusinessModelDescriptor descriptorWith(String operationCode) {
        return new BusinessModelDescriptor(OWNER, TYPE, "DEMO_TICKET", 1, BusinessModelKind.AGGREGATE_ROOT,
                "演示工单", null, List.of(), List.of(),
                List.of(new BusinessOperationDescriptor(operationCode, 1, "保存",
                        BusinessOperationDescriptor.StandardOperationKind.UPDATE)),
                List.of(), "demo_ticket");
    }

    @Test
    void submitDispatchesToConfiguredBackendAndRecordsOpinion() {
        ApprovalAttemptDO row = service.submit(SUBJECT, "TICKET_CLOSE", "att-1", "basis-1",
                "proc/ticket-close", 9L);

        assertEquals("al-instance-1", row.getInstanceRef());
        assertEquals(ApprovalAttemptDO.STATUS_PENDING, row.getStatus());
        verify(backend).submit(any(ApprovalSubmissionRequest.class));
        ArgumentCaptor<ApprovalOpinionDO> opinion = ArgumentCaptor.forClass(ApprovalOpinionDO.class);
        verify(opinionMapper).insert(opinion.capture());
        assertEquals(ApprovalOpinionDO.ACTION_SUBMIT, opinion.getValue().getAction());
        assertEquals(9L, opinion.getValue().getActorUserId());
    }

    @Test
    void submitWithoutBackendRejectedExplicitly() {
        when(backendRegistry.requireActive()).thenThrow(new BusinessContractException(
                "APPROVAL_BACKEND_UNAVAILABLE", "审批执行实现未装配"));

        BusinessContractException ex = assertThrows(BusinessContractException.class,
                () -> service.submit(SUBJECT, "TICKET_CLOSE", "att-1", "basis-1", "proc", 9L));
        assertEquals("APPROVAL_BACKEND_UNAVAILABLE", ex.getErrorCode());
        verify(attemptMapper, never()).insert(any(ApprovalAttemptDO.class));
    }

    @Test
    void submitReplaysSameAttemptKeyWithoutEngineCall() {
        ApprovalAttemptDO existing = attempt(10L, ApprovalAttemptDO.STATUS_APPROVED);
        when(attemptMapper.selectBySubjectAndAttempt(OWNER, TYPE, ENTITY_ID, "TICKET_CLOSE", "att-1"))
                .thenReturn(Optional.of(existing));

        ApprovalAttemptDO row = service.submit(SUBJECT, "TICKET_CLOSE", "att-1", "basis-1", "proc", 9L);

        assertEquals(10L, row.getId());
        verify(backend, never()).submit(any());
        verify(opinionMapper, never()).insert(any(ApprovalOpinionDO.class));
    }

    @Test
    void withdrawOnlyAllowedWhilePending() {
        service.withdraw(10L, "no longer needed", 9L);
        assertEquals(ApprovalAttemptDO.STATUS_WITHDRAWN, attempt.getStatus());
        verify(backend).withdraw("approval-local", "al-instance-1", "att-1", "no longer needed");

        attempt.setStatus(ApprovalAttemptDO.STATUS_APPROVED);
        BusinessContractException ex = assertThrows(BusinessContractException.class,
                () -> service.withdraw(10L, "again", 9L));
        assertEquals("APPROVAL_ATTEMPT_NOT_PENDING", ex.getErrorCode());
    }

    @Test
    void resubmitCreatesChainedAttemptAndKeepsOldRowUntouched() {
        attempt.setStatus(ApprovalAttemptDO.STATUS_REJECTED);
        doAnswer(inv -> {
            ((ApprovalAttemptDO) inv.getArgument(0)).setId(11L);
            return 1;
        }).when(attemptMapper).insert(any(ApprovalAttemptDO.class));
        when(attemptMapper.selectBySubjectAndAttempt(OWNER, TYPE, ENTITY_ID, "TICKET_CLOSE", "att-2"))
                .thenReturn(Optional.empty());

        ApprovalAttemptDO row = service.resubmit(10L, "att-2", "basis-2", 9L);

        assertEquals(11L, row.getId());
        assertEquals(10L, row.getPreviousAttemptId());
        assertEquals(ApprovalAttemptDO.STATUS_REJECTED, attempt.getStatus());
        verify(backend).submit(any(ApprovalSubmissionRequest.class));
    }

    @Test
    void resubmitRejectedWhilePreviousStillPending() {
        BusinessContractException ex = assertThrows(BusinessContractException.class,
                () -> service.resubmit(10L, "att-2", "basis-2", 9L));
        assertEquals("APPROVAL_ATTEMPT_STILL_PENDING", ex.getErrorCode());
    }

    @Test
    void trustedResultUpdatesPendingAttemptAndPublishesEvent() {
        ApprovalAssociationService.ApprovalRecordingResult result = service.recordTrustedResult(
                new ApprovalTrustedResult("approval-local", "approval-local:1", "al-instance-1",
                        "att-1", true, "一致同意"));

        assertEquals(1, result.updated());
        assertEquals(0, result.skipped());
        assertEquals(ApprovalAttemptDO.STATUS_APPROVED, attempt.getStatus());
        assertEquals("一致同意", attempt.getConclusionBasis());
        verify(outbox).append(eq("ApprovalAttempt"), eq("10"), any(
                PlatformCommandExecutionApi.BusinessEvent.class));
    }

    @Test
    void duplicateSameConclusionCallbackIsIdempotent() {
        attempt.setStatus(ApprovalAttemptDO.STATUS_APPROVED);

        ApprovalAssociationService.ApprovalRecordingResult result = service.recordTrustedResult(
                new ApprovalTrustedResult("approval-local", "approval-local:1", "al-instance-1",
                        "att-1", true, "一致同意"));

        assertEquals(0, result.updated());
        assertTrue(result.skippedReasons().get(0).contains("幂等跳过"));
        verify(outbox, never()).append(anyString(), anyString(), any());
    }

    @Test
    void oldAttemptCallbackDoesNotPolluteNewSubmission() {
        ApprovalAttemptDO newer = attempt(11L, ApprovalAttemptDO.STATUS_PENDING);
        newer.setAttemptId("att-2");
        when(attemptMapper.selectByInstanceRef("al-instance-1")).thenReturn(List.of(newer));

        ApprovalAssociationService.ApprovalRecordingResult result = service.recordTrustedResult(
                new ApprovalTrustedResult("approval-local", "approval-local:1", "al-instance-1",
                        "att-1", true, "旧结论"));

        assertEquals(0, result.updated());
        assertTrue(result.skippedReasons().get(0).contains("尝试不一致"));
        assertEquals(ApprovalAttemptDO.STATUS_PENDING, newer.getStatus());
    }

    @Test
    void withdrawnBatchMemberNotFoldedIntoBatchConclusion() {
        ApprovalAttemptDO withdrawnMember = attempt(11L, ApprovalAttemptDO.STATUS_WITHDRAWN);
        withdrawnMember.setAttemptId("att-1");
        when(attemptMapper.selectByInstanceRef("al-instance-1"))
                .thenReturn(List.of(attempt, withdrawnMember));

        ApprovalAssociationService.ApprovalRecordingResult result = service.recordTrustedResult(
                new ApprovalTrustedResult("approval-local", "approval-local:1", "al-instance-1",
                        "att-1", true, "批次批准"));

        assertEquals(1, result.updated());
        assertEquals(1, result.skipped());
        assertTrue(result.skippedReasons().get(0).contains("已撤回"));
        assertEquals(ApprovalAttemptDO.STATUS_WITHDRAWN, withdrawnMember.getStatus());
    }

    @Test
    void backendMismatchReportedAndRowUnchanged() {
        doThrow(new BusinessContractException("APPROVAL_BACKEND_MISMATCH",
                "审批执行实现不一致: other-engine")).when(backendRegistry)
                .requireBackendMatches(any(), eq("other-engine"));
        ApprovalAssociationService.ApprovalRecordingResult result = service.recordTrustedResult(
                new ApprovalTrustedResult("other-engine", "other:1", "al-instance-1",
                        "att-1", true, "伪造来源"));

        assertEquals(0, result.updated());
        assertTrue(result.skippedReasons().get(0).contains("执行实现不一致"));
        assertEquals(ApprovalAttemptDO.STATUS_PENDING, attempt.getStatus());
    }

    @Test
    void effectRequiresApprovedAttempt() {
        BusinessContractException ex = assertThrows(BusinessContractException.class,
                () -> service.executeEffect(10L, "save", "eff-1", Map.of(), null, 9L));
        assertEquals("APPROVAL_NOT_APPROVED", ex.getErrorCode());
        verify(dispatcher, never()).dispatch(any());
    }

    @Test
    void effectExecutesDeclaredCommandAndRecordsSuccess() {
        attempt.setStatus(ApprovalAttemptDO.STATUS_APPROVED);
        when(catalog.require(OWNER, TYPE)).thenReturn(descriptorWith("save"));
        when(dispatcher.dispatch(any(BusinessOperationRequest.class))).thenReturn(
                new BusinessOperationReceipt(ReceiptOutcome.SAVED, SUBJECT, 6L, List.of(), null, null));

        ApprovalEffectDO effect = service.executeEffect(10L, "save", "eff-1", Map.of("handled", true),
                null, 9L);

        assertEquals(ApprovalEffectDO.STATUS_SUCCESS, effect.getStatus());
        assertEquals("SAVED", effect.getReceiptOutcome());
        assertEquals(5L, effect.getConcurrencyBasis().longValue());
        ArgumentCaptor<BusinessOperationRequest> request =
                ArgumentCaptor.forClass(BusinessOperationRequest.class);
        verify(dispatcher).dispatch(request.capture());
        assertEquals("save", request.getValue().operationCode());
        assertEquals("approval:att-1", request.getValue().entryCorrelationId());
    }

    @Test
    void effectFailurePersistedThenRethrown() {
        attempt.setStatus(ApprovalAttemptDO.STATUS_APPROVED);
        when(catalog.require(OWNER, TYPE)).thenReturn(descriptorWith("save"));
        when(dispatcher.dispatch(any(BusinessOperationRequest.class))).thenThrow(
                new BusinessContractException("CONCURRENCY_CONFLICT", "并发依据过期"));

        BusinessContractException ex = assertThrows(BusinessContractException.class,
                () -> service.executeEffect(10L, "save", "eff-1", Map.of(), null, 9L));
        assertEquals("CONCURRENCY_CONFLICT", ex.getErrorCode());
        ArgumentCaptor<ApprovalEffectDO> persisted = ArgumentCaptor.forClass(ApprovalEffectDO.class);
        verify(effectMapper).insert(persisted.capture());
        assertEquals(ApprovalEffectDO.STATUS_FAILED, persisted.getValue().getStatus());
        assertTrue(persisted.getValue().getDetail().contains("CONCURRENCY_CONFLICT"));
    }

    @Test
    void effectReplaysByIdempotencyKey() {
        attempt.setStatus(ApprovalAttemptDO.STATUS_APPROVED);
        ApprovalEffectDO existing = new ApprovalEffectDO();
        existing.setId(50L);
        existing.setAttemptRowId(10L);
        existing.setOperationCode("save");
        existing.setIdempotencyKey("eff-1");
        existing.setStatus(ApprovalEffectDO.STATUS_SUCCESS);
        when(effectMapper.selectByKey(10L, "eff-1")).thenReturn(Optional.of(existing));

        ApprovalEffectDO effect = service.executeEffect(10L, "save", "eff-1", Map.of(), null, 9L);

        assertEquals(50L, effect.getId());
        verify(dispatcher, never()).dispatch(any());
    }

    @Test
    void effectOperationMustBeDeclared() {
        attempt.setStatus(ApprovalAttemptDO.STATUS_APPROVED);
        when(catalog.require(OWNER, TYPE)).thenReturn(descriptorWith("other-op"));

        BusinessContractException ex = assertThrows(BusinessContractException.class,
                () -> service.executeEffect(10L, "save", "eff-1", Map.of(), null, 9L));
        assertEquals("OPERATION_NOT_DECLARED", ex.getErrorCode());
    }

    @Test
    void decidedRowTimestampSetOnApproval() {
        service.recordTrustedResult(new ApprovalTrustedResult("approval-local", "approval-local:1",
                "al-instance-1", "att-1", true, "ok"));
        assertTrue(attempt.getDecidedTime() != null
                && !attempt.getDecidedTime().isAfter(LocalDateTime.now()));
    }
}
