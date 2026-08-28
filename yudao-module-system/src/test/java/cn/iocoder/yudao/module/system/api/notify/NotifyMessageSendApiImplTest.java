package cn.iocoder.yudao.module.system.api.notify;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySendSingleToUserReqDTO;
import cn.iocoder.yudao.module.system.service.notify.NotifySendService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotifyMessageSendApiImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private NotifyMessageSendApiImpl api;
    @Mock
    private NotifySendService notifySendService;

    @Test
    void sendSingleMessageToAdmin_forwardsDeliveryKey() {
        NotifySendSingleToUserReqDTO request = new NotifySendSingleToUserReqDTO()
                .setUserId(100L).setTemplateCode("project-assigned")
                .setTemplateParams(Map.of("projectName", "项目A"))
                .setDeliveryKey("event-001");
        when(notifySendService.sendSingleNotifyToAdmin(
                100L, "project-assigned", request.getTemplateParams(), "event-001")).thenReturn(9001L);

        Long result = api.sendSingleMessageToAdmin(request);

        assertEquals(9001L, result);
        verify(notifySendService).sendSingleNotifyToAdmin(
                100L, "project-assigned", request.getTemplateParams(), "event-001");
    }
}
