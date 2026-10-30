package cn.iocoder.yudao.module.pms.bindings.backend;

import cn.iocoder.yudao.module.pms.bindings.dal.dataobject.BindApprovalInstanceDO;
import cn.iocoder.yudao.module.pms.bindings.dal.mysql.BindApprovalInstanceMapper;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.approval.ApprovalAssociationApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.execution.ApprovalSubmissionRequest;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.execution.ApprovalTrustedResult;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LocalApprovalBackendTest {

    private static final EntityRef SUBJECT = new EntityRef(1L, "demo", "ticket", 2L);

    @Mock
    private BindApprovalInstanceMapper instanceMapper;
    @Mock
    private ApprovalAssociationApi associationApi;

    private LocalApprovalBackend backend;
    private BindApprovalInstanceDO instance;

    @BeforeEach
    void setUp() {
        backend = new LocalApprovalBackend(instanceMapper, associationApi);
        instance = new BindApprovalInstanceDO();
        instance.setId(1L);
        instance.setInstanceRef("al-abc");
        instance.setAttemptHint("att-1");
        instance.setPurpose("TICKET_CLOSE");
        instance.setStatus(BindApprovalInstanceDO.STATUS_PENDING);
        when(instanceMapper.selectByInstanceRef("al-abc")).thenReturn(Optional.of(instance));
        when(instanceMapper.selectByAttemptHint("att-1")).thenReturn(Optional.empty());
    }

    @Test
    void submitCreatesInstanceAndReplaysByAttemptKey() {
        String instanceRef = backend.submit(new ApprovalSubmissionRequest(SUBJECT, "TICKET_CLOSE",
                "att-1", "basis-1", "proc/ticket-close"));

        org.junit.jupiter.api.Assertions.assertTrue(instanceRef.startsWith("al-"));
        verify(instanceMapper).insert(any(BindApprovalInstanceDO.class));
        instance.setInstanceRef(instanceRef);

        // 同一尝试键重放：不再插入，返回同一实例。
        when(instanceMapper.selectByAttemptHint("att-1")).thenReturn(Optional.of(instance));
        String replay = backend.submit(new ApprovalSubmissionRequest(SUBJECT, "TICKET_CLOSE",
                "att-1", "basis-1", "proc/ticket-close"));
        assertEquals(instanceRef, replay);
        verify(instanceMapper).insert(any(BindApprovalInstanceDO.class)); // 仍只插过一次
    }

    @Test
    void submitRejectsReusedAttemptKeyAcrossPurposes() {
        when(instanceMapper.selectByAttemptHint("att-1")).thenReturn(Optional.of(instance));

        BusinessContractException ex = assertThrows(BusinessContractException.class,
                () -> backend.submit(new ApprovalSubmissionRequest(SUBJECT, "OTHER_PURPOSE",
                        "att-1", "basis-1", "proc")));
        assertEquals("APPROVAL_ATTEMPT_KEY_REUSED", ex.getErrorCode());
    }

    @Test
    void decideRunsStateMachineAndCallsBackTrustedResult() {
        backend.decide("al-abc", "att-1", true, "同意");

        assertEquals(BindApprovalInstanceDO.STATUS_APPROVED, instance.getStatus());
        assertEquals("同意", instance.getDecisionComment());
        ArgumentCaptor<ApprovalTrustedResult> result =
                ArgumentCaptor.forClass(ApprovalTrustedResult.class);
        verify(associationApi).recordTrustedResult(result.capture());
        assertEquals(LocalApprovalBackend.BACKEND_ID, result.getValue().backendId());
        assertEquals("al-abc", result.getValue().instanceRef());
        assertEquals("att-1", result.getValue().attemptId());
        assertTrue(result.getValue().approved());
    }

    @Test
    void decideRejectedWhenInstanceNotPending() {
        instance.setStatus(BindApprovalInstanceDO.STATUS_APPROVED);

        BusinessContractException ex = assertThrows(BusinessContractException.class,
                () -> backend.decide("al-abc", "att-1", false, "迟到回调"));
        assertEquals("APPROVAL_INSTANCE_NOT_PENDING", ex.getErrorCode());
        verify(associationApi, org.mockito.Mockito.never()).recordTrustedResult(any());
    }

    @Test
    void withdrawMarksInstanceAndNotifiesAssociation() {
        backend.withdraw(LocalApprovalBackend.BACKEND_ID, "al-abc", "att-1", "发起人撤回");

        assertEquals(BindApprovalInstanceDO.STATUS_WITHDRAWN, instance.getStatus());
        verify(associationApi).recordWithdrawal(LocalApprovalBackend.BACKEND_ID, "al-abc", "att-1",
                "发起人撤回");
    }
}
