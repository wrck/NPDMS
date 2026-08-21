package cn.iocoder.yudao.module.system.api.outbox;

import cn.iocoder.yudao.module.system.api.outbox.dto.OutboxAppendCommand;

/** 与正式业务事实同事务追加事件的边界。 */
public interface TransactionalOutboxApi {

    void append(OutboxAppendCommand command);
}
