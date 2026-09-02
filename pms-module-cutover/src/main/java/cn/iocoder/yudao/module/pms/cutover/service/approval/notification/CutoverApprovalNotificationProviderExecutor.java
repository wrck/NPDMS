package cn.iocoder.yudao.module.pms.cutover.service.approval.notification;

import cn.iocoder.yudao.module.system.api.notify.NotifyMessageSendApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySendSingleToUserReqDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CutoverApprovalNotificationProviderExecutor {

    private final NotifyMessageSendApi notifyMessageSendApi;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long send(NotifySendSingleToUserReqDTO request) {
        return notifyMessageSendApi.sendSingleMessageToAdmin(request);
    }
}
