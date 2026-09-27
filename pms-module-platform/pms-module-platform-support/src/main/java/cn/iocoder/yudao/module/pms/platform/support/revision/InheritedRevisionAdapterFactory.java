package cn.iocoder.yudao.module.pms.platform.support.revision;

import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessAccessGuard;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityExtensionApi;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityFieldProvider;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityVersionProvider;
import cn.iocoder.yudao.module.pms.platform.support.persistence.BusinessEntityPersistenceRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 继承式修订适配器工厂：为声明了内容历史且携带修订 Mapper 的实体构造统一版本与字段 Provider。
 * SPI Provider 优先；本工厂只承接无专用 Provider 的普通声明实体，缓存按实体键复用。
 */
@Component
public class InheritedRevisionAdapterFactory {

    private final BusinessEntityPersistenceRegistry persistence;
    private final ObjectProvider<BusinessAccessGuard> guard;
    private final ObjectProvider<EntityExtensionApi> extensionApi;
    private final ObjectProvider<OperationAuditApi> auditApi;
    private final Map<String, InheritedRevisionAdapter> adapters = new ConcurrentHashMap<>();

    public InheritedRevisionAdapterFactory(BusinessEntityPersistenceRegistry persistence,
                                           ObjectProvider<BusinessAccessGuard> guard,
                                           ObjectProvider<EntityExtensionApi> extensionApi,
                                           ObjectProvider<OperationAuditApi> auditApi) {
        this.persistence = persistence;
        this.guard = guard;
        this.extensionApi = extensionApi;
        this.auditApi = auditApi;
    }

    /** 实体未声明或未启用继承式历史时返回 false，调用方继续按 Provider 缺失拒绝。 */
    public boolean supports(EntityRef entity) {
        return persistence.find(entity.ownerModule(), entity.entityType())
                .map(InheritedRevisionAdapter::supports)
                .orElse(false);
    }

    public EntityVersionProvider versionProvider(EntityRef entity) {
        return adapter(entity);
    }

    public EntityFieldProvider fieldProvider(EntityRef entity) {
        return adapter(entity);
    }

    private InheritedRevisionAdapter adapter(EntityRef entity) {
        var declaration = persistence.require(entity.ownerModule(), entity.entityType());
        if (!InheritedRevisionAdapter.supports(declaration)) {
            throw new BusinessContractException("ENTITY_PROVIDER_UNAVAILABLE",
                    "实体未声明可用的继承式历史能力: " + entity.ownerModule() + "/" + entity.entityType());
        }
        return adapters.computeIfAbsent(entity.ownerModule() + "/" + entity.entityType(),
                key -> new InheritedRevisionAdapter(declaration, guard.getIfAvailable(),
                        extensionApi.getIfAvailable(), auditApi.getIfAvailable()));
    }
}
