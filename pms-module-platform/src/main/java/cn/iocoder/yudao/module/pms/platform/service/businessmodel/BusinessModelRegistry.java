package cn.iocoder.yudao.module.pms.platform.service.businessmodel;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelContributor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelDeclaration;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelDescriptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 设计时目录：收集各业务域声明，校验实体、稳定编码与操作编码唯一；
 * 权限只缩小可见集合，不形成另一套定义。
 */
@Component
public class BusinessModelRegistry
        implements cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelCatalog {

    private final Map<String, BusinessModelDescriptor> byEntity = new LinkedHashMap<>();
    private final Map<String, BusinessModelDescriptor> byStableCode = new LinkedHashMap<>();

    public BusinessModelRegistry(ObjectProvider<BusinessModelContributor> contributors) {
        contributors.orderedStream()
                .forEach(contributor -> contributor.declarations().forEach(this::register));
    }

    @Override
    public Optional<BusinessModelDescriptor> find(String ownerModule, String entityType) {
        return Optional.ofNullable(byEntity.get(entityKey(ownerModule, entityType)));
    }

    @Override
    public Optional<BusinessModelDescriptor> findByStableCode(String stableCode) {
        return Optional.ofNullable(byStableCode.get(stableCode));
    }

    @Override
    public List<BusinessModelDescriptor> all() {
        return List.copyOf(byEntity.values());
    }

    private void register(BusinessModelDeclaration declaration) {
        BusinessModelDescriptor descriptor = declaration.descriptor();
        String entityKey = entityKey(descriptor.ownerModule(), descriptor.entityType());
        if (byEntity.containsKey(entityKey)) {
            throw new cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException(
                    "ENTITY_DECLARED_TWICE", "实体重复声明: " + entityKey);
        }
        if (byStableCode.containsKey(descriptor.stableCode())) {
            throw new cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException(
                    "STABLE_CODE_CONFLICT", "稳定编码冲突: " + descriptor.stableCode());
        }
        Set<String> operationCodes = new LinkedHashSet<>();
        for (var operation : descriptor.operations()) {
            if (!operationCodes.add(operation.code())) {
                throw new cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException(
                        "OPERATION_CODE_CONFLICT",
                        descriptor.ownerModule() + "/" + descriptor.entityType() + " 操作编码重复: " + operation.code());
            }
        }
        byEntity.put(entityKey, descriptor);
        byStableCode.put(descriptor.stableCode(), descriptor);
    }

    private static String entityKey(String ownerModule, String entityType) {
        return ownerModule + "/" + entityType;
    }
}
