package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformOutboxDeliveryApi;
import cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxMessageDTO;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.command.ProjectServiceManagerAssignedPayload;
import cn.iocoder.yudao.module.system.api.notify.NotifyMessageSendApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySendSingleToUserReqDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceManagerNotificationJobTest {

    @Mock
    private PlatformOutboxDeliveryApi outboxDeliveryApi;
    @Mock
    private NotifyMessageSendApi notifyMessageSendApi;
    @InjectMocks
    private ProjectServiceManagerNotificationJob job;

    @BeforeEach
    void setTenant() {
        TenantContextHolder.setTenantId(9L);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void sendsFrozenPayloadAndMarksDelivered() {
        PlatformOutboxMessageDTO message = message("evt-1", 0, 66L, "PRIMARY");
        when(outboxDeliveryApi.claimDue(any())).thenReturn(List.of(message));
        when(notifyMessageSendApi.sendSingleMessageToAdmin(any())).thenReturn(100L);

        String result = job.execute(null);

        ArgumentCaptor<NotifySendSingleToUserReqDTO> requestCaptor =
                ArgumentCaptor.forClass(NotifySendSingleToUserReqDTO.class);
        verify(notifyMessageSendApi).sendSingleMessageToAdmin(requestCaptor.capture());
        NotifySendSingleToUserReqDTO request = requestCaptor.getValue();
        assertEquals(66L, request.getUserId());
        assertEquals("evt-1", request.getDeliveryKey());
        assertEquals("PRIMARY", request.getTemplateParams().get("assignmentType"));
        verify(outboxDeliveryApi).markDelivered("evt-1", 0);
        assertEquals("服务经理通知投递成功 1 条，待重试 0 条", result);
    }

    @Test
    void replaysSameDeliveryKeyAfterSystemSuccessCompletionCrash() {
        PlatformOutboxMessageDTO message = message("evt-crash", 0, 66L, "PRIMARY");
        when(outboxDeliveryApi.claimDue(any())).thenReturn(List.of(message));
        when(notifyMessageSendApi.sendSingleMessageToAdmin(any())).thenReturn(101L);
        doThrow(new IllegalStateException("completion unavailable"))
                .doNothing().when(outboxDeliveryApi).markDelivered("evt-crash", 0);

        job.execute(null);
        job.execute(null);

        ArgumentCaptor<NotifySendSingleToUserReqDTO> captor =
                ArgumentCaptor.forClass(NotifySendSingleToUserReqDTO.class);
        verify(notifyMessageSendApi, times(2)).sendSingleMessageToAdmin(captor.capture());
        assertEquals(List.of("evt-crash", "evt-crash"),
                captor.getAllValues().stream().map(NotifySendSingleToUserReqDTO::getDeliveryKey).toList());
        verify(outboxDeliveryApi).scheduleRetry(org.mockito.ArgumentMatchers.eq("evt-crash"),
                org.mockito.ArgumentMatchers.eq(0), any(LocalDateTime.class));
    }

    @Test
    void failureSchedulesBoundedRetryWithoutRebuildingOldEvent() {
        PlatformOutboxMessageDTO oldMessage = message("evt-old", 20, 65L, "COLLABORATOR");
        when(outboxDeliveryApi.claimDue(any())).thenReturn(List.of(oldMessage));
        doThrow(new IllegalStateException("system unavailable"))
                .when(notifyMessageSendApi).sendSingleMessageToAdmin(any());

        job.execute(null);

        ArgumentCaptor<NotifySendSingleToUserReqDTO> requestCaptor =
                ArgumentCaptor.forClass(NotifySendSingleToUserReqDTO.class);
        verify(notifyMessageSendApi).sendSingleMessageToAdmin(requestCaptor.capture());
        assertEquals(65L, requestCaptor.getValue().getUserId());
        assertEquals("COLLABORATOR", requestCaptor.getValue().getTemplateParams().get("assignmentType"));
        ArgumentCaptor<LocalDateTime> retryCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(outboxDeliveryApi).scheduleRetry(
                org.mockito.ArgumentMatchers.eq("evt-old"), org.mockito.ArgumentMatchers.eq(20),
                retryCaptor.capture());
        long minutes = java.time.Duration.between(LocalDateTime.now(), retryCaptor.getValue()).toMinutes();
        assertFalse(minutes > ProjectServiceManagerNotificationJob.MAX_RETRY_DELAY_MINUTES);
    }

    private static PlatformOutboxMessageDTO message(
            String eventId, int retryCount, Long recipientUserId, String assignmentType) {
        LocalDateTime effectiveFrom = LocalDateTime.of(2026, 8, 25, 8, 30);
        Map<String, Object> snapshot = Map.of(
                "projectId", 1L,
                "assignmentId", 8L,
                "assignmentType", assignmentType,
                "levelCode", "L1",
                "effectiveFrom", effectiveFrom);
        ProjectServiceManagerAssignedPayload payload = new ProjectServiceManagerAssignedPayload(
                8L, 1L, recipientUserId, "pms_project_service_manager_assigned",
                snapshot, assignmentType, "L1", effectiveFrom);
        return new PlatformOutboxMessageDTO(eventId, "ProjectServiceManagerAssigned",
                JsonUtils.toJsonString(payload), retryCount, 9L, effectiveFrom);
    }
}
