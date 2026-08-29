package cn.iocoder.yudao.module.pms.commerce.api.scope;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.scope.dto.DeliveryScopeAcceptanceLockCommand;
import cn.iocoder.yudao.module.pms.commerce.api.scope.dto.DeliveryScopeVersionFact;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.DeliveryScopeMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.DeliveryScopeAcceptanceLockQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.DeliveryScopeVersionLockRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class DeliveryScopeAcceptanceLockApiImpl implements DeliveryScopeAcceptanceLockApi {

    private final DeliveryScopeMapper deliveryScopeMapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public List<DeliveryScopeVersionFact> lockCurrentByProject(DeliveryScopeAcceptanceLockCommand command) {
        validate(command);
        List<DeliveryScopeVersionLockRow> rows = deliveryScopeMapper.selectCurrentVersionsForAcceptanceLock(
                new DeliveryScopeAcceptanceLockQuery(command.tenantId(), command.projectId()));
        if (rows == null) {
            throw new IllegalStateException("DELIVERY_SCOPE_ACCEPTANCE_LOCK_UNAVAILABLE");
        }
        List<DeliveryScopeVersionFact> facts = new ArrayList<>(rows.size());
        Long previousId = null;
        for (DeliveryScopeVersionLockRow row : rows) {
            if (row == null || !positive(row.getDeliveryScopeId()) || !positive(row.getAllocationVersion())
                    || previousId != null && row.getDeliveryScopeId() <= previousId) {
                throw new IllegalStateException("DELIVERY_SCOPE_ACCEPTANCE_LOCK_INCONSISTENT");
            }
            facts.add(new DeliveryScopeVersionFact(row.getDeliveryScopeId(), row.getAllocationVersion()));
            previousId = row.getDeliveryScopeId();
        }
        return List.copyOf(facts);
    }

    private void validate(DeliveryScopeAcceptanceLockCommand command) {
        if (command == null || command.tenantId() == null || command.tenantId() < 0
                || !Objects.equals(command.tenantId(), TenantContextHolder.getTenantId())
                || !positive(command.projectId()) || !positive(command.projectStageSnapshotId())
                || command.operationId() == null || command.operationId().isBlank()) {
            throw new IllegalArgumentException("DELIVERY_SCOPE_ACCEPTANCE_LOCK_REQUEST_INVALID");
        }
    }

    private boolean positive(Long value) {
        return value != null && value > 0;
    }
}
