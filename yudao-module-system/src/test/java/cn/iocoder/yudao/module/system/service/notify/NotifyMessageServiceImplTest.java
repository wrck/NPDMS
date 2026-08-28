package cn.iocoder.yudao.module.system.service.notify;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.system.dal.dataobject.notify.NotifyMessageDO;
import cn.iocoder.yudao.module.system.dal.dataobject.notify.NotifyTemplateDO;
import cn.iocoder.yudao.module.system.dal.mysql.notify.NotifyMessageMapper;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.dao.DuplicateKeyException;

import java.util.Map;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.NOTIFY_DELIVERY_KEY_CONFLICT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

class NotifyMessageServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private NotifyMessageServiceImpl service;
    @Mock
    private NotifyMessageMapper mapper;

    @Test
    void createNotifyMessage_returnsExistingIdForConsistentReplay() {
        NotifyTemplateDO template = template();
        Map<String, Object> params = Map.of("projectName", "项目A");
        doThrow(new DuplicateKeyException("duplicate")).when(mapper).insert(any(NotifyMessageDO.class));
        when(mapper.selectByUserTypeAndDeliveryKey(1, "event-001")).thenReturn(
                new NotifyMessageDO().setId(9001L).setUserId(100L).setUserType(1)
                        .setTemplateCode("project-assigned").setTemplateParams(params)
                        .setDeliveryKey("event-001"));

        Long result = service.createNotifyMessage(
                100L, 1, template, "项目A已指派", params, "event-001");

        assertEquals(9001L, result);
    }

    @Test
    void createNotifyMessage_rejectsDifferentReplayPayload() {
        NotifyTemplateDO template = template();
        doThrow(new DuplicateKeyException("duplicate")).when(mapper).insert(any(NotifyMessageDO.class));
        when(mapper.selectByUserTypeAndDeliveryKey(1, "event-001")).thenReturn(
                new NotifyMessageDO().setId(9001L).setUserId(101L).setUserType(1)
                        .setTemplateCode("project-assigned")
                        .setTemplateParams(Map.of("projectName", "项目B"))
                        .setDeliveryKey("event-001"));

        assertServiceException(() -> service.createNotifyMessage(
                        100L, 1, template, "项目A已指派",
                        Map.of("projectName", "项目A"), "event-001"),
                NOTIFY_DELIVERY_KEY_CONFLICT);
    }

    private static NotifyTemplateDO template() {
        return new NotifyTemplateDO().setId(8001L).setCode("project-assigned")
                .setType(1).setNickname("项目平台");
    }
}
