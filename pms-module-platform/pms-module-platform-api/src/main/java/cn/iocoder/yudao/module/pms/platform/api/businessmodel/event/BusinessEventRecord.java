package cn.iocoder.yudao.module.pms.platform.api.businessmodel.event;

import cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef;

import java.util.Map;

/**
 * 业务事件：事件身份、来源实体、变化/形成/失效类型、并发或顺序依据、因果关联。
 * 真实业务变更与事件登记同事务；后续消费失败不重新执行已成功的领域命令。
 * sequence 是 Outbox 行号：登记方不提供（0），仅受控读取用于推进消费游标，避免队头停滞。
 */
public record BusinessEventRecord(
        String eventId,
        EntityRef sourceRef,
        BusinessEventKind kind,
        String orderBasis,
        String causationId,
        Map<String, Object> payload,
        long sequence) {
}
