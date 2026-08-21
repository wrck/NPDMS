package cn.iocoder.yudao.module.system.service.outbox;

import cn.iocoder.yudao.module.system.api.outbox.dto.OutboxAppendCommand;
import cn.iocoder.yudao.module.system.dal.dataobject.outbox.OutboxEventDO;
import cn.iocoder.yudao.module.system.dal.mysql.outbox.OutboxEventMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionalOutboxServiceImpl implements TransactionalOutboxService {

    @Resource
    private OutboxEventMapper outboxEventMapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void append(OutboxAppendCommand command) {
        outboxEventMapper.insert(OutboxEventDO.builder()
                .tenantId(command.tenantId()).eventId(command.eventId())
                .aggregateType(command.aggregateType()).aggregateId(Long.toString(command.aggregateId()))
                .eventType(command.eventType()).eventVersion(command.eventVersion())
                .payloadJson(command.payloadJson()).publishStatus("PENDING").retryCount(0).build());
    }
}
