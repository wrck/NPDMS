package cn.iocoder.yudao.module.pms.platform.service.delivery;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.delivery.DeliveryMaterialUploadPolicyValidator;
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
 * 统一交付材料的文件策略归属：ownerContext=PLT / objectType=DELIVERY_MATERIAL。
 * purposeCode 为类型目录编码时，文件约束（媒体/大小）取自类型目录，类型停用即拒绝新上传，
 * scopeVersion 取类型行乐观锁版本；purposeCode 非类型目录编码（模板冻结交付件的交付件编码等）
 * 时按 objectId 前缀解析归属 Owner 模块，委派 {@link DeliveryMaterialUploadPolicyValidator}
 * 裁决约束与授权——平台不代答 Owner 的业务授权（项目范围、允许来源、生命周期等）。
 */
@Component
@RequiredArgsConstructor
public class DeliveryFilePolicyProvider implements FileBusinessObjectPolicyProvider {

    private static final Set<String> SUPPORTED_ACTIONS = Set.of(
            FileActionCodes.UPLOAD, FileActionCodes.REFERENCE, FileActionCodes.READ,
            FileActionCodes.DOWNLOAD, FileActionCodes.PREVIEW);

    private final DeliveryCatalogService catalogService;
    private final List<DeliveryMaterialUploadPolicyValidator> uploadValidators;
    @org.springframework.beans.factory.annotation.Autowired
    private DeliveryOwnerAccess ownerAccess;

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
        return inspectOrDelegate(query, false, null);
    }

    @Override
    public FileBusinessObjectPolicyFact lockAndRevalidate(FileBusinessObjectPolicyRevalidationQuery query) {
        return inspectOrDelegate(query.toInspectionQuery(), true, query.expectedScopeVersion());
    }

    /** 引用集与单对象同口径：交付材料无独立引用集约束，按 key 走同一校验路径。 */
    @Override
    public FileBusinessObjectPolicyFact inspectReferenceSet(FileBusinessObjectReferenceSetQuery query) {
        // 引用集 key 不含 referenceKey；本 Provider 的校验只依赖类型目录/Owner 校验方，占位即可。
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
        FileBusinessObjectPolicyQuery inspection = new FileBusinessObjectPolicyQuery(query.tenantId(),
                query.actorUserId(), query.key().ownerContext(), query.key().objectType(),
                query.key().objectId(), query.key().purposeCode(), query.key().purposeCode(),
                query.requiredAction(), query.ownerExecutionContext());
        return inspectOrDelegate(inspection, true, query.expectedScopeVersion());
    }

    /** 类型目录命中走目录约束；未命中（Owner 管控用途）按 objectId 前缀委派 Owner 校验方。 */
    private FileBusinessObjectPolicyFact inspectOrDelegate(FileBusinessObjectPolicyQuery query,
                                                           boolean lock, Long expectedScopeVersion) {
        if (!SUPPORTED_ACTIONS.contains(query.requiredAction())) {
            throw new BusinessContractException("FILE_ACTION_NOT_ALLOWED",
                    "交付材料不支持文件动作: " + query.requiredAction());
        }
        String[] owner = parseOwnerObjectId(query.objectId());
        var validators = uploadValidators.stream().filter(v -> owner[0].equals(v.ownerModule()) && v.supportsEntityType(owner[1])).toList();
        if (validators.size() > 1) throw DeliveryOwnerAccess.denied();
        // Owner identity selects template semantics even if its code collides with the type catalog.
        if (validators.size() == 1) {
            return validators.getFirst().validateUpload(query.tenantId(), query.actorUserId(), owner[1], owner[2],
                    query.purposeCode(), query.requiredAction(), lock, expectedScopeVersion);
        }
        if (ownerAccess == null) throw DeliveryOwnerAccess.denied();
        boolean write = FileActionCodes.UPLOAD.equals(query.requiredAction()) || FileActionCodes.REFERENCE.equals(query.requiredAction());
        Long scope = ownerAccess.require(query.tenantId(),query.actorUserId(),owner[0],owner[1],Long.valueOf(owner[2]),
                query.purposeCode(),write,lock,expectedScopeVersion);
        DeliveryTypeDO type = write ? (lock ? catalogService.lockEnabledType(query.purposeCode()) : catalogService.requireEnabledType(query.purposeCode())) : catalogService.requireType(query.purposeCode());
        return new FileBusinessObjectPolicyFact(true, scope, "IMMUTABLE", "MULTIPLE",
                Set.of(type.getCategory()), catalogService.allowedMedia(type).stream().map(DeliveryFilePolicyProvider::mediaType)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()),
                type.getMaxSizeBytes(), "INTERNAL");
    }

    private static String mediaType(String value) {
        return switch(value.toLowerCase(java.util.Locale.ROOT)) {
            case "pdf" -> "application/pdf";
            case "html", "htm" -> "text/html";
            case "jpeg", "jpg" -> "image/jpeg";
            case "png" -> "image/png";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt" -> "application/vnd.ms-powerpoint";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "txt" -> "text/plain";
            default -> value;
        };
    }

    /** 材料文件锚 objectId = "{ownerModule}:{entityType}:{entityId}"（fileObjectId 约定）。 */
    private static String[] parseOwnerObjectId(String objectId) {
        String[] parts = objectId == null ? new String[0] : objectId.split(":", 3);
        if (parts.length != 3 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
            throw new BusinessContractException("DELIVERY_FILE_OWNER_INVALID",
                    "交付材料归属标识不合法: " + objectId);
        }
        try {
            Long.parseLong(parts[2]);
        } catch (NumberFormatException e) {
            throw new BusinessContractException("DELIVERY_FILE_OWNER_INVALID",
                    "交付材料归属标识不合法: " + objectId);
        }
        return parts;
    }

    private DeliveryMaterialUploadPolicyValidator requireValidator(String ownerModule) {
        return uploadValidators.stream()
                .filter(validator -> validator.ownerModule().equals(ownerModule))
                .findFirst()
                .orElseThrow(() -> new BusinessContractException("DELIVERY_UPLOAD_VALIDATOR_MISSING",
                        "交付材料归属模块无上传策略校验方: " + ownerModule));
    }
}
