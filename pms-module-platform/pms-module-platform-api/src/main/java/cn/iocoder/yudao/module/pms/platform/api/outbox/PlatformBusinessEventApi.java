package cn.iocoder.yudao.module.pms.platform.api.outbox;

import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.BusinessEvent;

/** Append an explicit business event in the caller's existing transaction; delivery remains owned by Outbox. */
public interface PlatformBusinessEventApi {
    void append(String aggregateType, String aggregateKey, BusinessEvent event);
}
