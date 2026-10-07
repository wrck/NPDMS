package cn.iocoder.yudao.module.pms.platform.support.business;

import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.delivery.DefaultBusinessDeliveryApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.event.BusinessEventPort;
import cn.iocoder.yudao.module.pms.platform.support.service.BusinessCallerContext;
import cn.iocoder.yudao.module.pms.platform.support.service.OperationExecutionStore;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionOperations;

/** Shared technical ports wired once, not an entity catalog or command dispatcher. */
public record BusinessDefaults(BusinessCallerContext callers, BusinessAccessGuard permissions,
        BusinessScopeAccess projects, Validator validator, TransactionOperations transactions,
        OperationExecutionStore journal, OperationAuditApi audit, BusinessEventPort events,
        List<BusinessDeletionGuard> deletionGuards, Supplier<DefaultBusinessDeliveryApi> deliveries) {
    public BusinessDefaults {
        Objects.requireNonNull(callers); Objects.requireNonNull(permissions); Objects.requireNonNull(projects);
        Objects.requireNonNull(validator); Objects.requireNonNull(transactions); Objects.requireNonNull(journal);
        Objects.requireNonNull(audit); Objects.requireNonNull(events); Objects.requireNonNull(deliveries);
        if (!"project".equals(projects.policyRef())) throw new IllegalArgumentException("Project defaults require project scope");
        deletionGuards = List.copyOf(deletionGuards);
    }
}
