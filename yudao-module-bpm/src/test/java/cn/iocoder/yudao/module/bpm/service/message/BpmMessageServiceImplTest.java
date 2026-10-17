package cn.iocoder.yudao.module.bpm.service.message;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.web.config.WebProperties;
import cn.iocoder.yudao.module.bpm.service.message.dto.*;
import cn.iocoder.yudao.module.system.api.sms.SmsSendApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.SMS_SEND_MOBILE_NOT_EXISTS;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.SMS_SEND_TEMPLATE_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BpmMessageServiceImplTest {

    private final SmsSendApi smsSendApi = mock(SmsSendApi.class);
    private final BpmMessageServiceImpl service = new BpmMessageServiceImpl();

    enum Notice {
        APPROVE("bpm_process_instance_approve", 7L),
        REJECT("bpm_process_instance_reject", 7L),
        ASSIGNED("bpm_task_assigned", 8L),
        TIMEOUT("bpm_task_timeout", 8L);

        final String template;
        final Long recipient;
        Notice(String template, Long recipient) { this.template = template; this.recipient = recipient; }
    }

    @BeforeEach
    void setUp() {
        var properties = new WebProperties();
        var ui = new WebProperties.Ui();
        ui.setUrl("http://localhost:19191");
        properties.setAdminUi(ui);
        ReflectionTestUtils.setField(service, "smsSendApi", smsSendApi);
        ReflectionTestUtils.setField(service, "webProperties", properties);
    }

    @ParameterizedTest
    @EnumSource(Notice.class)
    void missingRecipientMobileDoesNotAbortBpmProcessing(Notice notice) {
        when(smsSendApi.sendSingleSmsToAdmin(any())).thenThrow(exception(SMS_SEND_MOBILE_NOT_EXISTS));

        assertDoesNotThrow(() -> send(notice));
        verify(smsSendApi).sendSingleSmsToAdmin(argThat(request ->
                notice.recipient.equals(request.getUserId()) && notice.template.equals(request.getTemplateCode())));
    }

    @ParameterizedTest
    @EnumSource(Notice.class)
    void configuredSmsStillUsesTheOriginalRecipientAndProcessDetail(Notice notice) {
        when(smsSendApi.sendSingleSmsToAdmin(any())).thenReturn(91L);

        send(notice);

        verify(smsSendApi).sendSingleSmsToAdmin(argThat(request ->
                notice.recipient.equals(request.getUserId()) && notice.template.equals(request.getTemplateCode())
                        && "http://localhost:19191/bpm/process-instance/detail?id=process-1"
                        .equals(request.getTemplateParams().get("detailUrl"))));
    }

    @ParameterizedTest
    @EnumSource(Notice.class)
    void unrelatedSmsFailuresAreStillReported(Notice notice) {
        ServiceException failure = exception(SMS_SEND_TEMPLATE_NOT_EXISTS);
        when(smsSendApi.sendSingleSmsToAdmin(any())).thenThrow(failure);

        assertSame(failure, assertThrows(ServiceException.class, () -> send(notice)));
    }

    private void send(Notice notice) {
        switch (notice) {
            case APPROVE -> service.sendMessageWhenProcessInstanceApprove(new BpmMessageSendWhenProcessInstanceApproveReqDTO()
                    .setProcessInstanceId("process-1").setProcessInstanceName("工期变更").setStartUserId(7L));
            case REJECT -> service.sendMessageWhenProcessInstanceReject(new BpmMessageSendWhenProcessInstanceRejectReqDTO()
                    .setProcessInstanceId("process-1").setProcessInstanceName("工期变更").setStartUserId(7L).setReason("测试驳回"));
            case ASSIGNED -> service.sendMessageWhenTaskAssigned(new BpmMessageSendWhenTaskCreatedReqDTO()
                    .setProcessInstanceId("process-1").setProcessInstanceName("工期变更").setStartUserId(7L)
                    .setStartUserNickname("申请人").setTaskId("task-1").setTaskName("服务经理审批").setAssigneeUserId(8L));
            case TIMEOUT -> service.sendMessageWhenTaskTimeout(new BpmMessageSendWhenTaskTimeoutReqDTO()
                    .setProcessInstanceId("process-1").setProcessInstanceName("工期变更")
                    .setTaskId("task-1").setTaskName("服务经理审批").setAssigneeUserId(8L));
        }
    }
}
