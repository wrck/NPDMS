package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultChange;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/** 通道通知的有界接收者游标，不是业务结果版本或形成边界。 */
public record ResultSubscriptionFanoutEvent(String eventId, int eventVersion, BusinessResultChange source, long afterSubscriptionId) {
    public static final String EVENT_TYPE = "PMS.ResultSubscriptionFanout.v1";
    public ResultSubscriptionFanoutEvent {
        UUID.fromString(eventId); Objects.requireNonNull(source, "source change");
        if (eventVersion != 1 || afterSubscriptionId <= 0) throw new IllegalArgumentException("SUBSCRIPTION_FANOUT_INVALID");
    }
    public static ResultSubscriptionFanoutEvent create(BusinessResultChange source, long after) {
        String id = UUID.nameUUIDFromBytes(("subscription-fanout:" + source.eventId() + ":" + after).getBytes(StandardCharsets.UTF_8)).toString();
        return new ResultSubscriptionFanoutEvent(id, 1, source, after);
    }
}
