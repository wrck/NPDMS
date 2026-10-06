package cn.iocoder.yudao.module.pms.platform.support.service;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.BusinessOperationReceipt;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.BusinessOperationRequest;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import cn.iocoder.yudao.module.pms.platform.support.persistence.BusinessEntityPersistenceRegistry;

import java.util.Map;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 统一操作分发：按声明定位实体所属服务；专业服务按 ownerModule/entityType 注册，
 * 未注册的实体自动落入默认服务。独立入口和各后端受控入口执行同一继承服务。
 */
public class BusinessOperationDispatcher {

    private final BusinessEntityPersistenceRegistry persistence;
    private final DefaultBusinessApplicationService defaultService;
    private final Map<String, AbstractBusinessApplicationService<?>> specialized = new ConcurrentHashMap<>();

    public BusinessOperationDispatcher(BusinessEntityPersistenceRegistry persistence,
                                       DefaultBusinessApplicationService defaultService) {
        this.persistence = persistence;
        this.defaultService = defaultService;
    }

    /** Discover inherited Owner services, including class-based transactional proxies. */
    public BusinessOperationDispatcher(BusinessEntityPersistenceRegistry persistence,
                                       DefaultBusinessApplicationService defaultService,
                                       Collection<? extends AbstractBusinessApplicationService<?>> services) {
        this(persistence, defaultService);
        for (AbstractBusinessApplicationService<?> service : services) {
            BusinessEntityService identity = service.getClass().getAnnotation(BusinessEntityService.class);
            if (identity != null) register(identity.ownerModule(), identity.entityType(), service);
        }
    }

    /** 专业服务注册：同一实体重复注册直接报告冲突，不允许静默替换。 */
    public void register(String ownerModule, String entityType,
                         AbstractBusinessApplicationService<?> service) {
        persistence.require(ownerModule, entityType);
        String key = ownerModule + "/" + entityType;
        AbstractBusinessApplicationService<?> previous = specialized.putIfAbsent(key, service);
        if (previous != null) {
            throw new BusinessContractException("SERVICE_DECLARED_TWICE",
                    "实体专业服务重复注册: " + key);
        }
    }

    /** Native Owners retain their own capability providers; generic defaults use this same thin service. */
    public DefaultBusinessApplicationService capabilityService(String ownerModule,String entityType) {
        persistence.require(ownerModule,entityType);
        var service=specialized.getOrDefault(ownerModule+"/"+entityType,defaultService);
        if(service==defaultService || service instanceof ExtensibleBusinessApplicationService)
            return (DefaultBusinessApplicationService)service;
        throw new BusinessContractException("ENTITY_PROVIDER_UNAVAILABLE","Native Owner requires its own capability contract");
    }

    public BusinessOperationReceipt recoverReceipt(String owner,String type,String operation,int version,String key) {
        persistence.require(owner,type);
        var service=specialized.getOrDefault(owner+"/"+type,defaultService);
        if(service instanceof DefaultBusinessApplicationService recovering)
            return recovering.recoverReceipt(owner,type,operation,version,key);
        throw new BusinessContractException("RECEIPT_RECOVERY_UNAVAILABLE","Owner has no public receipt recovery contract");
    }

    @SuppressWarnings("unchecked")
    public BusinessOperationReceipt dispatch(BusinessOperationRequest request) {
        String ownerModule = request.targetRef() != null
                ? request.targetRef().entity().ownerModule() : request.ownerModule();
        String entityType = request.targetRef() != null
                ? request.targetRef().entity().entityType() : request.entityType();
        persistence.require(ownerModule, entityType);
        AbstractBusinessApplicationService<BaseBusinessEntity> service =
                (AbstractBusinessApplicationService<BaseBusinessEntity>) specialized.getOrDefault(
                        ownerModule + "/" + entityType, defaultService);
        return service.execute(request);
    }
}
