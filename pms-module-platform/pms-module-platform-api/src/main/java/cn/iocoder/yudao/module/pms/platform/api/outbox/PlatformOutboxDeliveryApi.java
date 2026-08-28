package cn.iocoder.yudao.module.pms.platform.api.outbox;

import cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxClaimQuery;
import cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxMessageDTO;

import java.time.LocalDateTime;
import java.util.List;

/** 平台Outbox投递公开契约。 */
public interface PlatformOutboxDeliveryApi {

    List<PlatformOutboxMessageDTO> claimDue(PlatformOutboxClaimQuery query);

    void markDelivered(String eventId, int expectedRetryCount);

    void scheduleRetry(String eventId, int expectedRetryCount, LocalDateTime nextRetryTime);
}
