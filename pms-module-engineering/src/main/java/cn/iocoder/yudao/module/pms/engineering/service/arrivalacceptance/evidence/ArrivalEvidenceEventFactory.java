package cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.evidence;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.engineering.api.arrival.event.ImplementationEvidencePublishedMessage;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;

import java.util.UUID;

public final class ArrivalEvidenceEventFactory {

    public static final String IMPLEMENTATION_EVIDENCE_PUBLISHED = "ImplementationEvidencePublished";

    public String nextEventId() {
        return UUID.randomUUID().toString();
    }

    public PlatformCommandExecutionApi.BusinessEvent published(
            ImplementationEvidencePublishedMessage message) {
        return new PlatformCommandExecutionApi.BusinessEvent(
                message.eventId(), IMPLEMENTATION_EVIDENCE_PUBLISHED,
                JsonUtils.toJsonString(message));
    }
}
