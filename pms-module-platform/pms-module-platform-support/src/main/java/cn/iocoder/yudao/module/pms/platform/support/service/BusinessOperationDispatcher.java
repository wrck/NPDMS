package cn.iocoder.yudao.module.pms.platform.support.service;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.BusinessOperationReceipt;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.BusinessOperationRequest;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import cn.iocoder.yudao.module.pms.platform.support.persistence.BusinessEntityPersistenceRegistry;

import java.util.Map;
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
