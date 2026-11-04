package cn.iocoder.yudao.module.system.api.sms;

import cn.iocoder.yudao.module.system.dal.dataobject.sms.SmsChannelDO;
import cn.iocoder.yudao.module.system.dal.dataobject.sms.SmsTemplateDO;
import cn.iocoder.yudao.module.system.service.sms.SmsChannelService;
import cn.iocoder.yudao.module.system.service.sms.SmsTemplateService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SmsNotificationAvailabilityApiImplTest {
    @Test
    void requiresEnabledTemplateAndEnabledChannel() {
        var templates = mock(SmsTemplateService.class);
        var channels = mock(SmsChannelService.class);
        var api = new SmsNotificationAvailabilityApiImpl();
        ReflectionTestUtils.setField(api, "smsTemplateService", templates);
        ReflectionTestUtils.setField(api, "smsChannelService", channels);

        assertFalse(api.isEnabled("bpm"));
        var template = new SmsTemplateDO().setChannelId(42L).setStatus(1);
        when(templates.getSmsTemplateByCodeFromCache("bpm")).thenReturn(template);
        assertFalse(api.isEnabled("bpm"));
        verifyNoInteractions(channels);

        template.setStatus(0);
        assertFalse(api.isEnabled("bpm"));
        var channel = new SmsChannelDO().setStatus(1);
        when(channels.getSmsChannel(42L)).thenReturn(channel);
        assertFalse(api.isEnabled("bpm"));
        channel.setStatus(0);
        assertTrue(api.isEnabled("bpm"));
    }
}
