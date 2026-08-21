package cn.iocoder.yudao.module.system.service.idempotency;

import cn.iocoder.yudao.module.system.api.idempotency.dto.IdempotencyDecision;
import cn.iocoder.yudao.module.system.dal.dataobject.idempotency.IdempotencyRecordDO;
import cn.iocoder.yudao.module.system.dal.mysql.idempotency.IdempotencyRecordMapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.IDEMPOTENCY_RECORD_INCOMPLETE;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.IDEMPOTENCY_REQUEST_CONFLICT;

@Service
public class TransactionalIdempotencyServiceImpl implements TransactionalIdempotencyService {

    @Resource
    private IdempotencyRecordMapper idempotencyRecordMapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public IdempotencyDecision begin(long tenantId, long actorId, String scopeCode,
                                     String idempotencyKey, String requestSha256) {
        IdempotencyRecordDO reservation = IdempotencyRecordDO.builder()
                .id(IdWorker.getId()).tenantId(tenantId).actorId(actorId).scopeCode(scopeCode)
                .idempotencyKey(idempotencyKey).requestSha256(requestSha256).status("RESERVED").build();
        boolean inserted = idempotencyRecordMapper.insertIgnore(reservation) == 1;
        IdempotencyRecordDO record = idempotencyRecordMapper.selectForUpdate(
                tenantId, actorId, scopeCode, idempotencyKey);
        if (record == null || !Objects.equals(record.getRequestSha256(), requestSha256)) {
            throw exception(IDEMPOTENCY_REQUEST_CONFLICT);
        }
        if (inserted) {
            return new IdempotencyDecision(IdempotencyDecision.Mode.OWNER, record.getId(), null, null);
        }
        if (!"COMPLETED".equals(record.getStatus())) {
            throw exception(IDEMPOTENCY_RECORD_INCOMPLETE);
        }
        return new IdempotencyDecision(IdempotencyDecision.Mode.REPLAY, record.getId(),
                record.getResourceId(), record.getResponseJson());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void complete(long recordId, long resourceId, String responseJson) {
        if (idempotencyRecordMapper.complete(recordId, resourceId, responseJson) != 1) {
            throw exception(IDEMPOTENCY_RECORD_INCOMPLETE);
        }
    }
}
