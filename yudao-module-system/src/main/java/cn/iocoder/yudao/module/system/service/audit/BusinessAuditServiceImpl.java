package cn.iocoder.yudao.module.system.service.audit;

import cn.iocoder.yudao.module.system.api.audit.dto.BusinessAuditCommand;
import cn.iocoder.yudao.module.system.dal.dataobject.audit.OperationAuditDO;
import cn.iocoder.yudao.module.system.dal.mysql.audit.OperationAuditMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class BusinessAuditServiceImpl implements BusinessAuditService {

    @Resource
    private OperationAuditMapper operationAuditMapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void appendSuccess(BusinessAuditCommand command) {
        append(command);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void appendFailureAfterRollback(BusinessAuditCommand command) {
        append(command);
    }

    private void append(BusinessAuditCommand command) {
        operationAuditMapper.insert(OperationAuditDO.builder()
                .tenantId(command.tenantId()).actorId(command.actorId())
                .operationCode(command.operationCode()).resourceType(command.resourceType())
                .resourceId(command.resourceId() == null ? null : Long.toString(command.resourceId()))
                .decisionCode(command.decisionCode()).detailJson(command.redactedDetailJson())
                .correlationId(command.correlationId()).operationTime(LocalDateTime.now()).build());
    }
}
