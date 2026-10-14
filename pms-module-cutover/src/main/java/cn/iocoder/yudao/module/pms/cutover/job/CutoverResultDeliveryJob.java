package cn.iocoder.yudao.module.pms.cutover.job;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.quartz.core.handler.JobHandler;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.job.TenantJob;
import cn.iocoder.yudao.module.pms.cutover.api.result.*;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformOutboxDeliveryApi;
import cn.iocoder.yudao.module.pms.platform.api.outbox.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.*;

@Component @RequiredArgsConstructor @Slf4j
public class CutoverResultDeliveryJob implements JobHandler {
    private final PlatformOutboxDeliveryApi outbox;
    private final List<CutoverCompletedConsumer> consumers;
    @Override @TenantJob
    public String execute(String param) {
        var now = LocalDateTime.now(); int complete = 0;
        var messages = outbox.claimDue(new PlatformOutboxClaimQuery(now, 50, Set.of(CutoverCompleted.EVENT_TYPE)));
        for (var message : messages) if (deliver(message)) complete++;
        return "割接结果已投递 " + complete + "，待重试 " + (messages.size() - complete);
    }
    public boolean deliver(PlatformOutboxMessageDTO message) {
        boolean complete = false;
        try {
            var event = JsonUtils.parseObject(message.payload(), CutoverCompleted.class);
            if (!CutoverCompleted.EVENT_TYPE.equals(message.eventType()) || event == null
                    || !Objects.equals(message.tenantId(), TenantContextHolder.getTenantId())
                    || !Objects.equals(message.eventId(), event.eventId()) || !Objects.equals(message.tenantId(), event.tenantId())
                    || !"SUCCESS".equals(event.finalResult()) || event.projectId() == null)
                throw new IllegalArgumentException("割接结果消息身份无效");
            var asset = consumers.stream().filter(consumer -> "AST".equals(consumer.ownerContext())).toList();
            if (asset.size() != 1) throw new IllegalStateException("资产结果消费者不可用");
            for (var consumer : consumers) consumer.accept(event);
            complete = true;
        } catch (RuntimeException failed) {
            log.warn("Cutover result delivery deferred: tenant={}, event={}", message.tenantId(), message.eventId());
        }
        try {
            if (complete) outbox.markDelivered(message.eventId(), message.retryCount());
            else outbox.scheduleRetry(message.eventId(), message.retryCount(), LocalDateTime.now().plusMinutes(
                    Math.min(60, 1L << Math.min(Math.max(message.retryCount(), 0), 6))));
        } catch (IllegalStateException collision) {
            if (!Set.of("OUTBOX_DELIVERY_CAS_CONFLICT", "OUTBOX_RETRY_CAS_CONFLICT").contains(collision.getMessage())) throw collision;
        }
        return complete;
    }
}
