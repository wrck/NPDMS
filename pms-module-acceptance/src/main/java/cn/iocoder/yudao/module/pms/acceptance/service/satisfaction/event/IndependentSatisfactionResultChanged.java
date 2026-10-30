package cn.iocoder.yudao.module.pms.acceptance.service.satisfaction.event;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.BusinessEvent;
import java.util.UUID;

public record IndependentSatisfactionResultChanged(String eventId, Long tenantId, Long projectId,
        Long taskId, Long resultId, Long actorId, String changeType) {
    public static final String TYPE = "ACC.IndependentSatisfactionResultChanged.v1";
    public static BusinessEvent event(Long tenant, Long project, Long task, Long result, Long actor, String change) {
        var event = new IndependentSatisfactionResultChanged(UUID.randomUUID().toString(), tenant, project, task, result, actor, change);
        return new BusinessEvent(event.eventId(), TYPE, JsonUtils.toJsonString(event));
    }
}
