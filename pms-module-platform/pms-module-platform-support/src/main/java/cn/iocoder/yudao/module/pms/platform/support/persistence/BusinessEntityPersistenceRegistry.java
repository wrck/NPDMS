package cn.iocoder.yudao.module.pms.platform.support.persistence;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelDeclaration;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelContributor;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 声明持久化注册表：收集各业务域的 BusinessModelContributor，
 * 校验声明与 Mapper 泛型一致、稳定编码唯一；重复声明直接报告冲突。
 */
@Component
public class BusinessEntityPersistenceRegistry {

    private final Map<String, BusinessModelDeclaration> byEntity = new LinkedHashMap<>();
    private final Map<String, BusinessModelDeclaration> byStableCode = new LinkedHashMap<>();

    public BusinessEntityPersistenceRegistry(ObjectProvider<BusinessModelContributor> contributors) {
        contributors.orderedStream().forEach(contributor -> contributor.declarations().forEach(this::register));
    }

    public java.util.List<BusinessModelDeclaration> declarations() { return java.util.List.copyOf(byEntity.values()); }

    public Optional<BusinessModelDeclaration> find(String ownerModule, String entityType) {
        return Optional.ofNullable(byEntity.get(key(ownerModule, entityType)));
    }

    public BusinessModelDeclaration require(String ownerModule, String entityType) {
        return find(ownerModule, entityType).orElseThrow(() -> new BusinessContractException(
                "MODEL_NOT_DECLARED", "实体未声明持久化映射: " + ownerModule + "/" + entityType));
    }

    @SuppressWarnings("unchecked")
    public <T> BaseMapper<T> mapperOf(BusinessModelDeclaration declaration) {
        return (BaseMapper<T>) declaration.mapper();
    }

    public Optional<BaseMapper<?>> revisionMapperOf(BusinessModelDeclaration declaration) {
        return Optional.ofNullable(declaration.revisionMapper())
                .map(mapper -> (BaseMapper<?>) mapper);
    }

    private void register(BusinessModelDeclaration declaration) {
        if (declaration.descriptor() == null || declaration.entityClass() == null
                || !(declaration.mapper() instanceof BaseMapper)) {
            throw new BusinessContractException("DECLARATION_INCOMPLETE",
                    "声明缺少描述、实体类或 BaseMapper");
        }
        if (!BaseBusinessEntity.class.isAssignableFrom(declaration.entityClass())) {
            throw new BusinessContractException("ENTITY_NOT_UNIFIED",
                    "实体未继承统一业务基类: " + declaration.entityClass().getName());
        }
        Class<?> mapperEntity = ResolvableType.forInstance(declaration.mapper())
                .as(BaseMapper.class).getGeneric(0).resolve();
        if (mapperEntity != null && !mapperEntity.equals(declaration.entityClass())) {
            throw new BusinessContractException("DECLARATION_MISMATCH",
                    "Mapper 泛型与声明实体不一致: " + mapperEntity.getName());
        }
        if (declaration.revisionMapper() != null && !(declaration.revisionMapper() instanceof BaseMapper)) {
            throw new BusinessContractException("DECLARATION_MISMATCH", "修订 Mapper 不是 BaseMapper");
        }
        String entityKey = key(declaration.descriptor().ownerModule(), declaration.descriptor().entityType());
        if (byEntity.containsKey(entityKey)) {
            throw new BusinessContractException("ENTITY_DECLARED_TWICE", "实体重复声明: " + entityKey);
        }
        String stableKey = declaration.descriptor().stableCode();
        if (byStableCode.containsKey(stableKey)) {
            throw new BusinessContractException("STABLE_CODE_CONFLICT",
                    "稳定编码冲突: " + stableKey);
        }
        byEntity.put(entityKey, declaration);
        byStableCode.put(stableKey, declaration);
    }

    private static String key(String ownerModule, String entityType) {
        return ownerModule + "/" + entityType;
    }
}
