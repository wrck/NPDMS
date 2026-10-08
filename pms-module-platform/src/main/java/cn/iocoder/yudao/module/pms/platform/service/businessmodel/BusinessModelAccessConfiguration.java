package cn.iocoder.yudao.module.pms.platform.service.businessmodel;

import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessAccessGuard;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessEntityAccessPort;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessEntityScopePolicy;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.collection.BusinessCollectionPort;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.event.BusinessEventPort;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelCatalog;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityExtensionApi;
import cn.iocoder.yudao.module.pms.platform.support.access.DefaultBusinessEntityAccess;
import cn.iocoder.yudao.module.pms.platform.support.persistence.BusinessEntityPersistenceRegistry;
import cn.iocoder.yudao.module.pms.platform.support.service.BusinessCallerContext;
import cn.iocoder.yudao.module.pms.platform.support.service.AbstractBusinessApplicationService;
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
    public cn.iocoder.yudao.module.pms.platform.support.service.BusinessEntityIdentityResolver businessEntityIdentityResolver(
            org.springframework.beans.factory.ListableBeanFactory beans) {
        return new cn.iocoder.yudao.module.pms.platform.support.service.BusinessEntityIdentityResolver(beans);
    }

    @Bean
    public cn.iocoder.yudao.module.pms.platform.support.access.DeclaredBusinessScopeSupport declaredBusinessScopes(
            ObjectProvider<cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessScopeAccess> policies) {
        return new cn.iocoder.yudao.module.pms.platform.support.access.DeclaredBusinessScopeSupport(policies.orderedStream().toList());
    }

    @Bean
    @org.springframework.context.annotation.Primary
    public BusinessEntityAccessPort businessEntityAccessPort(BusinessModelCatalog catalog,
                                                             BusinessEntityPersistenceRegistry persistence,
                                                             BusinessAccessGuard guard,
                                                             ObjectProvider<EntityExtensionApi> extensionApi,
                                                              ObjectProvider<BusinessEntityScopePolicy> scopePolicies,
                                                              ObjectProvider<cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessEntityContentReader> contentReaders,
            cn.iocoder.yudao.module.pms.platform.support.access.DeclaredBusinessScopeSupport declaredScopes) {
        DefaultBusinessEntityAccess access =
                new DefaultBusinessEntityAccess(catalog, persistence, guard, extensionApi.getIfAvailable(),
                        scopePolicies.orderedStream().toList(), contentReaders.orderedStream().toList(), declaredScopes);
        return access;
    }

    @Bean
    public BusinessCollectionPort businessCollectionPort(BusinessEntityAccessPort accessPort) {
        return (BusinessCollectionPort) accessPort;
    }

    @Bean
    @org.springframework.context.annotation.Primary
    public DefaultBusinessApplicationService defaultBusinessApplicationService(
            BusinessCallerContext callerContext, BusinessModelCatalog catalog,
            BusinessEntityPersistenceRegistry persistence, BusinessAccessGuard guard,
            OperationExecutionStore executionStore, BusinessEventPort eventPort,
            OperationAuditApi auditApi, ObjectProvider<TransactionOperations> transactionOperations,
            org.springframework.transaction.PlatformTransactionManager transactionManager,
            ObjectProvider<EntityExtensionApi> extensionApi,
            cn.iocoder.yudao.module.pms.platform.support.access.DeclaredBusinessScopeSupport declaredScopes,
            BusinessEntityAccessPort entityAccess,
            ObjectProvider<cn.iocoder.yudao.module.pms.platform.api.businessmodel.delivery.DefaultBusinessDeliveryApi> deliveryPort,
            jakarta.validation.Validator validator,
            ObjectProvider<cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessDeletionGuard> deletionGuards) {
        var defaults = defaultBusinessApplicationService(callerContext, catalog, persistence, guard,
                executionStore, eventPort, auditApi, transactionOperations, transactionManager, extensionApi, declaredScopes);
        defaults.configureDefaultCapabilities(entityAccess, deliveryPort::getObject);
        defaults.configureValidation(validator, deletionGuards.orderedStream().toList());
        return defaults;
    }

    /** Existing isolated runtimes may configure capability ports after constructing their local context. */
    public DefaultBusinessApplicationService defaultBusinessApplicationService(
            BusinessCallerContext callerContext, BusinessModelCatalog catalog,
            BusinessEntityPersistenceRegistry persistence, BusinessAccessGuard guard,
            OperationExecutionStore executionStore, BusinessEventPort eventPort, OperationAuditApi auditApi,
            ObjectProvider<TransactionOperations> transactionOperations,
            org.springframework.transaction.PlatformTransactionManager transactionManager,
            ObjectProvider<EntityExtensionApi> extensionApi,
            cn.iocoder.yudao.module.pms.platform.support.access.DeclaredBusinessScopeSupport declaredScopes) {
        return new DefaultBusinessApplicationService(callerContext, catalog, persistence, guard,
                executionStore, eventPort, auditApi,
                transactionOperations.getIfAvailable(() -> new org.springframework.transaction.support.TransactionTemplate(transactionManager)),
                extensionApi.getIfAvailable(), declaredScopes);
    }

    @Bean
    public BusinessOperationDispatcher businessOperationDispatcher(
            BusinessEntityPersistenceRegistry persistence,
            DefaultBusinessApplicationService defaultService,
            ObjectProvider<AbstractBusinessApplicationService<?>> applicationServices) {
        return new BusinessOperationDispatcher(persistence, defaultService,
                applicationServices.orderedStream().toList());
    }
}
