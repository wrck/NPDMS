package cn.iocoder.yudao.module.pms.platform.service.delivery;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.FileBusinessObjectPolicyProvider;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectReferenceSetQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectReferenceSetRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliveryTypeDO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 统一交付材料的文件策略归属：ownerContext=PLT / objectType=DELIVERY_MATERIAL，
 * purposeCode=交付类型编码；文件约束（媒体/大小）取自类型目录，类型停用即拒绝新上传。
 * scopeVersion 取类型行乐观锁版本——类型约束变更使在途上传会话显式失效，不静默沿用旧约束。
 */
@Component
@RequiredArgsConstructor
public class DeliveryFilePolicyProvider implements FileBusinessObjectPolicyProvider {

    private static final Set<String> SUPPORTED_ACTIONS = Set.of(
            FileActionCodes.UPLOAD, FileActionCodes.REFERENCE, FileActionCodes.READ,
            FileActionCodes.DOWNLOAD, FileActionCodes.PREVIEW);

    private final DeliveryCatalogService catalogService;

    @Override
    public String ownerContext() {
        return DeliveryMaterialService.FILE_OWNER_CONTEXT;
    }

    @Override
    public String objectType() {
        return DeliveryMaterialService.FILE_OBJECT_TYPE;
    }

    @Override
    public FileBusinessObjectPolicyFact inspect(FileBusinessObjectPolicyQuery query) {
        if (!SUPPORTED_ACTIONS.contains(query.requiredAction())) {
            throw new BusinessContractException("FILE_ACTION_NOT_ALLOWED",
                    "交付材料不支持文件动作: " + query.requiredAction());
        }
        DeliveryTypeDO type = catalogService.requireEnabledType(query.purposeCode());
        return new FileBusinessObjectPolicyFact(true, type.getVersion().longValue(), "IMMUTABLE", "MULTIPLE",
                Set.of(type.getCategory()), Set.copyOf(catalogService.allowedMedia(type)),
                type.getMaxSizeBytes(), "INTERNAL");
    }

    @Override
    public FileBusinessObjectPolicyFact lockAndRevalidate(FileBusinessObjectPolicyRevalidationQuery query) {
        FileBusinessObjectPolicyFact fact = inspect(query.toInspectionQuery());
        if (!fact.scopeVersion().equals(query.expectedScopeVersion())) {
            throw new BusinessContractException("FILE_SCOPE_VERSION_CONFLICT",
                    "交付类型约束已变化，请重新发起上传");
        }
        return fact;
    }

    /** 引用集与单对象同口径：交付材料无独立引用集约束，按 key 走同一类型目录校验。 */
    @Override
    public FileBusinessObjectPolicyFact inspectReferenceSet(FileBusinessObjectReferenceSetQuery query) {
        // 引用集 key 不含 referenceKey；本 Provider 的校验只依赖类型目录，占位即可。
        return inspect(new FileBusinessObjectPolicyQuery(query.tenantId(), query.actorUserId(),
                query.key().ownerContext(), query.key().objectType(), query.key().objectId(),
                query.key().purposeCode(), query.key().purposeCode(), query.requiredAction(),
                query.ownerExecutionContext()));
    }

    @Override
    public Map<FileBusinessObjectReferenceSetQuery, FileBusinessObjectPolicyFact> inspectReferenceSets(
            List<FileBusinessObjectReferenceSetQuery> queries) {
        Map<FileBusinessObjectReferenceSetQuery, FileBusinessObjectPolicyFact> facts = new LinkedHashMap<>();
        for (FileBusinessObjectReferenceSetQuery query : queries) {
            facts.put(query, inspectReferenceSet(query));
        }
        return facts;
    }

    @Override
    public FileBusinessObjectPolicyFact lockAndRevalidateReferenceSet(
            FileBusinessObjectReferenceSetRevalidationQuery query) {
        FileBusinessObjectPolicyFact fact = inspect(new FileBusinessObjectPolicyQuery(query.tenantId(),
                query.actorUserId(), query.key().ownerContext(), query.key().objectType(),
                query.key().objectId(), query.key().purposeCode(), query.key().purposeCode(),
                query.requiredAction(), query.ownerExecutionContext()));
        if (!fact.scopeVersion().equals(query.expectedScopeVersion())) {
            throw new BusinessContractException("FILE_SCOPE_VERSION_CONFLICT",
                    "交付类型约束已变化，请重新发起上传");
        }
        return fact;
    }
}
