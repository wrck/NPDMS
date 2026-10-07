package cn.iocoder.yudao.module.pms.platform.support.service;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import java.util.Map;
import java.util.function.Supplier;

/** Fixed content and extension content share the caller's REQUIRED Owner transaction. */
public final class BusinessEntitySaveSupport {
    private final EntityExtensionApi extensions;

    public BusinessEntitySaveSupport(EntityExtensionApi extensions) {
        this.extensions = extensions;
    }

    public static void requireTransaction() {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            throw new BusinessContractException("TRANSACTION_REQUIRED", "固定与扩展保存必须处于同一业务事务");
    }

    public record ExtensionPatch(Long definitionRevisionId, int expectedVersion, Map<String, Object> values) {}

    /** The Owner validates its fields and advances its aggregate/revision version exactly once. */
    public <T> T save(EntityDataRef target, EntityActor actor, Long expectedEntityVersion,
                      ExtensionPatch patch, Supplier<T> fixedContentSave) {
        requireTransaction();
        if (patch != null && patch.values() != null) {
            if (extensions == null) throw new BusinessContractException("CAPABILITY_UNAVAILABLE", "扩展能力未装配");
            extensions.save(new EntityExtensionApi.Save(target, actor, expectedEntityVersion,
                    patch.expectedVersion(), patch.definitionRevisionId(), patch.values()));
        }
        return fixedContentSave.get();
    }
}
