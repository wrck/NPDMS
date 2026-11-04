package cn.iocoder.yudao.module.system.api.sms;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.system.service.sms.SmsChannelService;
import cn.iocoder.yudao.module.system.service.sms.SmsTemplateService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class SmsNotificationAvailabilityApiImpl implements SmsNotificationAvailabilityApi {

    @Resource
    private SmsTemplateService smsTemplateService;
    @Resource
    private SmsChannelService smsChannelService;

    @Override
    public boolean isEnabled(String templateCode) {
        var template = smsTemplateService.getSmsTemplateByCodeFromCache(templateCode);
        if (template == null || !CommonStatusEnum.ENABLE.getStatus().equals(template.getStatus())) {
            return false;
        }
        var channel = smsChannelService.getSmsChannel(template.getChannelId());
        return channel != null && CommonStatusEnum.ENABLE.getStatus().equals(channel.getStatus());
    }
}
