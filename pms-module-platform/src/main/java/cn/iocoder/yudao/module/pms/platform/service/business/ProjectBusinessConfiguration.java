package cn.iocoder.yudao.module.pms.platform.service.business;

import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessDeletionGuard;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessScopeAccess;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.delivery.DefaultBusinessDeliveryApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.event.BusinessEventPort;
import cn.iocoder.yudao.module.pms.platform.support.business.BusinessDefaults;
import cn.iocoder.yudao.module.pms.platform.support.business.BusinessPermissions;
import cn.iocoder.yudao.module.pms.platform.support.service.BusinessCallerContext;
import cn.iocoder.yudao.module.pms.platform.support.service.OperationExecutionStore;
import jakarta.validation.Validator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Infrastructure for the direct inheritance path. Existing template/model runtime remains unchanged. */
@Configuration(proxyBeanMethods=false)
public class ProjectBusinessConfiguration {
    @Bean public BusinessDefaults projectBusinessDefaults(BusinessCallerContext callers,PermissionCommonApi permissions,
            ObjectProvider<BusinessScopeAccess> scopes,Validator validator,PlatformTransactionManager transactions,
            OperationExecutionStore journal,OperationAuditApi audit,BusinessEventPort events,
            ObjectProvider<BusinessDeletionGuard> deletionGuards,ObjectProvider<DefaultBusinessDeliveryApi> deliveries) {
        var projectScopes=scopes.orderedStream().filter(scope->"project".equals(scope.policyRef())).toList();
        if (projectScopes.size()!=1) throw new IllegalStateException("Exactly one shared project scope implementation is required");
        return new BusinessDefaults(callers,new BusinessPermissions(permissions,callers),projectScopes.getFirst(),validator,
                new TransactionTemplate(transactions),journal,audit,events,deletionGuards.orderedStream().toList(),deliveries::getObject);
    }
}
