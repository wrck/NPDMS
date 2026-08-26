package cn.iocoder.yudao.module.pms.engineering.service.constructionplan;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanChangeDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanRevisionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.ConstructionPlanChangeMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.ConstructionPlanMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.ConstructionPlanRevisionMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanChangeVersionUpdate;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanVersionUpdate;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DurationChangeBpmResultServiceTest {

    @Mock ConstructionPlanMapper planMapper;
    @Mock ConstructionPlanRevisionMapper revisionMapper;
    @Mock ConstructionPlanChangeMapper changeMapper;
    @Mock DurationChangeBpmAuthorizationGuard authorizationGuard;
    @Mock OperationAuditApi operationAuditApi;

    private DurationChangeBpmResultService service;

    @BeforeEach
    void setUp() {
        service = new DurationChangeBpmResultService(planMapper, revisionMapper, changeMapper,
                authorizationGuard, operationAuditApi);
    }

    @Test
    void shouldApproveAndSwitchCurrentRevision() {
        stubPending(false);

        service.handle("P-1", BpmProcessInstanceStatusEnum.APPROVE.getStatus(), "同意");

        ArgumentCaptor<ConstructionPlanChangeVersionUpdate> changeUpdate =
                ArgumentCaptor.forClass(ConstructionPlanChangeVersionUpdate.class);
        verify(changeMapper).updateVersionIfMatch(changeUpdate.capture());
        assertEquals(ConstructionPlanChangeDO.STATUS_APPROVED,
                changeUpdate.getValue().statusCode());
        ArgumentCaptor<ConstructionPlanVersionUpdate> planUpdate =
                ArgumentCaptor.forClass(ConstructionPlanVersionUpdate.class);
        verify(planMapper).updateVersionIfMatch(planUpdate.capture());
        assertEquals(702L, planUpdate.getValue().currentDurationRevisionId());
        assertEquals(null, planUpdate.getValue().pendingChangeId());
        assertEquals(ConstructionPlanDO.RECALCULATION_PENDING,
                planUpdate.getValue().planRecalculationStatusCode());
        assertEquals(702L, planUpdate.getValue().planRecalculationSourceRevisionId());
        verify(operationAuditApi).record(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldRejectAndKeepCurrentRevision() {
        stubPending(false);

        service.handle("P-1", BpmProcessInstanceStatusEnum.REJECT.getStatus(), "资料不完整");

        ArgumentCaptor<ConstructionPlanChangeVersionUpdate> changeUpdate =
                ArgumentCaptor.forClass(ConstructionPlanChangeVersionUpdate.class);
        verify(changeMapper).updateVersionIfMatch(changeUpdate.capture());
        assertEquals(ConstructionPlanChangeDO.STATUS_REJECTED,
                changeUpdate.getValue().statusCode());
        assertEquals("资料不完整", changeUpdate.getValue().approvalOpinion());
        ArgumentCaptor<ConstructionPlanVersionUpdate> planUpdate =
                ArgumentCaptor.forClass(ConstructionPlanVersionUpdate.class);
        verify(planMapper).updateVersionIfMatch(planUpdate.capture());
        assertEquals(701L, planUpdate.getValue().currentDurationRevisionId());
        assertEquals("RECALCULATED", planUpdate.getValue().planRecalculationStatusCode());
        assertEquals(701L, planUpdate.getValue().planRecalculationSourceRevisionId());
    }

    @Test
    void shouldWithdrawAndKeepCurrentRevision() {
        stubPending(false);

        service.handle("P-1", BpmProcessInstanceStatusEnum.CANCEL.getStatus(), "申请人撤回");

        ArgumentCaptor<ConstructionPlanChangeVersionUpdate> update =
                ArgumentCaptor.forClass(ConstructionPlanChangeVersionUpdate.class);
        verify(changeMapper).updateVersionIfMatch(update.capture());
        assertEquals(ConstructionPlanChangeDO.STATUS_WITHDRAWN, update.getValue().statusCode());
    }

    @Test
    void shouldIgnoreRepeatedTerminalResult() {
        stubAuthorization();
        ConstructionPlanDO plan = plan();
        ConstructionPlanChangeDO change = change();
        change.setStatusCode(ConstructionPlanChangeDO.STATUS_APPROVED);
        when(planMapper.selectForUpdate(any())).thenReturn(plan);
        when(changeMapper.selectForUpdate(any())).thenReturn(change);

        service.handle("P-1", BpmProcessInstanceStatusEnum.APPROVE.getStatus(), "重放");

        verify(changeMapper, never()).updateVersionIfMatch(any());
        verify(planMapper, never()).updateVersionIfMatch(any());
        verify(operationAuditApi, never()).record(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldIgnoreNonTerminalStatus() {
        service.handle("P-1", BpmProcessInstanceStatusEnum.RUNNING.getStatus(), null);
        verify(authorizationGuard, never()).authorize(any(), any());
    }

    @Test
    void shouldFailClosedWhenFrozenFileFactCannotBeRevalidated() {
        stubPending(true);

        assertThrows(ServiceException.class, () -> service.handle(
                "P-1", BpmProcessInstanceStatusEnum.APPROVE.getStatus(), "同意"));
        verify(changeMapper, never()).updateVersionIfMatch(any());
        verify(planMapper, never()).updateVersionIfMatch(any());
    }

    private void stubPending(boolean evidenceRequired) {
        stubAuthorization();
        when(planMapper.selectForUpdate(any())).thenReturn(plan());
        ConstructionPlanChangeDO change = change();
        change.setCustomerEvidenceRequired(evidenceRequired);
        when(changeMapper.selectForUpdate(any())).thenReturn(change);
        when(revisionMapper.selectForUpdate(any())).thenReturn(candidate());
        if (!evidenceRequired) {
            when(changeMapper.updateVersionIfMatch(any())).thenReturn(1);
            when(planMapper.updateVersionIfMatch(any())).thenReturn(1);
        }
    }

    private void stubAuthorization() {
        when(authorizationGuard.authorize(any(), any())).thenReturn(
                new DurationChangeBpmAuthorizationGuard.AuthorizationContext(
                        0L, 10L, 501L, 301L, 801L, 3));
    }

    private ConstructionPlanDO plan() {
        ConstructionPlanDO plan = new ConstructionPlanDO();
        plan.setId(501L);
        plan.setTenantId(0L);
        plan.setProjectId(301L);
        plan.setCurrentDurationRevisionId(701L);
        plan.setPendingChangeId(801L);
        plan.setPlanRecalculationStatusCode("RECALCULATED");
        plan.setPlanRecalculationSourceRevisionId(701L);
        plan.setVersion(4);
        return plan;
    }

    private ConstructionPlanChangeDO change() {
        ConstructionPlanChangeDO change = new ConstructionPlanChangeDO();
        change.setId(801L);
        change.setTenantId(0L);
        change.setPlanId(501L);
        change.setBaseRevisionId(701L);
        change.setCandidateRevisionId(702L);
        change.setStatusCode(ConstructionPlanChangeDO.STATUS_PENDING_APPROVAL);
        change.setReasonTypeCode("INTERNAL_ADJUSTMENT");
        change.setReasonDetail("reason");
        change.setCustomerEvidenceRequired(false);
        change.setProcessDefinitionKey("pms-sol-duration-change");
        change.setProcessInstanceId("P-1");
        change.setSubmittedAt(LocalDateTime.now());
        change.setApplicantUserId(9L);
        change.setApproverUserId(10L);
        change.setVersion(2);
        return change;
    }

    private ConstructionPlanRevisionDO candidate() {
        ConstructionPlanRevisionDO revision = new ConstructionPlanRevisionDO();
        revision.setId(702L);
        revision.setTenantId(0L);
        revision.setPlanId(501L);
        revision.setSourceChangeId(801L);
        revision.setFrozenAt(LocalDateTime.now());
        return revision;
    }

}
