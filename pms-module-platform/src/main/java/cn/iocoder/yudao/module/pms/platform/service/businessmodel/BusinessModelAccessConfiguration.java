package cn.iocoder.yudao.module.pms.platform.service.businessmodel;

import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessAccessGuard;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessEntityAccessPort;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.collection.BusinessCollectionPort;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.event.BusinessEventPort;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelCatalog;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityExtensionApi;
import cn.iocoder.yudao.module.pms.platform.support.access.DefaultBusinessEntityAccess;
import cn.iocoder.yudao.module.pms.platform.support.persistence.BusinessEntityPersistenceRegistry;
import cn.iocoder.yudao.module.pms.platform.support.service.BusinessCallerContext;
import cn.iocoder.yudao.module.pms.platform.support.service.BusinessOperationDispatcher;
import cn.iocoder.yudao.module.pms.platform.support.service.DefaultBusinessApplicationService;
import cn.iocoder.yudao.module.pms.platform.support.service.OperationExecutionStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionOperations;

/** 公共访问与默认业务执行装配：不加载任何项目运行时、流程引擎或规则引擎实现。 */
@Configuration
public class BusinessModelAccessConfiguration {

    @Bean
    public BusinessEntityAccessPort businessEntityAccessPort(BusinessModelCatalog catalog,
                                                             BusinessEntityPersistenceRegistry persistence,
                                                             BusinessAccessGuard guard,
                                                             ObjectProvider<EntityExtensionApi> extensionApi) {
        DefaultBusinessEntityAccess access =
                new DefaultBusinessEntityAccess(catalog, persistence, guard, extensionApi.getIfAvailable());
        return access;
    }

    @Bean
    public BusinessCollectionPort businessCollectionPort(BusinessEntityAccessPort accessPort) {
        return (BusinessCollectionPort) accessPort;
    }

    @Bean
    public DefaultBusinessApplicationService defaultBusinessApplicationService(
            BusinessCallerContext callerContext, BusinessModelCatalog catalog,
            BusinessEntityPersistenceRegistry persistence, BusinessAccessGuard guard,
            OperationExecutionStore executionStore, BusinessEventPort eventPort,
            OperationAuditApi auditApi, ObjectProvider<TransactionOperations> transactionOperations) {
        return new DefaultBusinessApplicationService(callerContext, catalog, persistence, guard,
                executionStore, eventPort, auditApi, transactionOperations.getIfAvailable());
    }

    @Bean
    public BusinessOperationDispatcher businessOperationDispatcher(
            BusinessEntityPersistenceRegistry persistence,
            DefaultBusinessApplicationService defaultService) {
        return new BusinessOperationDispatcher(persistence, defaultService);
    }
}
