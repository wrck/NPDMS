package cn.iocoder.yudao.module.pms.platform.service.delivery;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliveryMaterialDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.DeliveryMaterialMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 实际材料记录：登记即冻结文件证据（引用存在、可用、摘要一致、归属与类型匹配）；
 * 同一文件版本对同一来源实体幂等登记；撤回后不再计数，可重新提交前重新验证。
 */
@Service
@RequiredArgsConstructor
public class DeliveryMaterialService {

    public static final String FILE_OWNER_CONTEXT = "PLT";
    public static final String FILE_OBJECT_TYPE = "DELIVERY_MATERIAL";

    private final DeliveryMaterialMapper materialMapper;
    private final DeliveryCatalogService catalogService;
    private final FileEvidenceApi fileEvidenceApi;
    private final DeliveryEventPublisher eventPublisher;

    public static String fileObjectId(String ownerModule, String entityType, Long entityId) {
        return ownerModule + ":" + entityType + ":" + entityId;
    }

    @Transactional
    public DeliveryMaterialDO register(String ownerModule, String entityType, Long entityId, String typeCode,
                                       Long fileReferenceId, String title, String sourceKind) {
        catalogService.requireEnabledType(typeCode);
        FileEvidenceApi.Document document = fileEvidenceApi.inspectDocument(
                TenantContextHolder.getRequiredTenantId(), fileReferenceId);
        if (document == null) {
            throw new BusinessContractException("DELIVERY_FILE_UNAVAILABLE",
                    "文件引用不存在或不可用: " + fileReferenceId);
        }
        validateDocumentOwner(document, ownerModule, entityType, entityId, typeCode);
        if (!document.available()) {
            throw new BusinessContractException("DELIVERY_FILE_UNAVAILABLE",
                    "文件版本不可用，不能登记为交付材料: " + fileReferenceId);
        }
        DeliveryMaterialDO row = new DeliveryMaterialDO();
        row.setOwnerModule(ownerModule);
        row.setEntityType(entityType);
        row.setEntityId(entityId);
        row.setTypeCode(typeCode);
        row.setFileReferenceId(fileReferenceId);
        row.setFileArtifactId(document.artifactId());
        row.setFileVersionNo(document.versionNo());
        row.setFileSha256(document.sha256());
        row.setFileName(document.name());
        row.setTitle(title);
        row.setSourceKind(sourceKind == null || sourceKind.isBlank()
                ? DeliveryMaterialDO.SOURCE_UPLOAD : sourceKind);
        row.setStatus(DeliveryMaterialDO.STATUS_ACTIVE);
        try {
            materialMapper.insert(row);
        } catch (DuplicateKeyException conflict) {
            // 同一文件版本对同一来源实体的重复登记按幂等处理，返回既有材料记录。
            return materialMapper.selectByEntity(ownerModule, entityType, entityId, typeCode).stream()
                    .filter(material -> material.getFileArtifactId().equals(document.artifactId())
                            && material.getFileVersionNo().equals(document.versionNo()))
                    .findFirst()
                    .orElseThrow(() -> conflict);
        }
        eventPublisher.publishMaterial(ownerModule, entityType, entityId, typeCode, "MATERIAL_REGISTERED");
        return row;
    }

    private static void validateDocumentOwner(FileEvidenceApi.Document document, String ownerModule,
                                              String entityType, Long entityId, String typeCode) {
        boolean ownerMatches = FILE_OWNER_CONTEXT.equals(document.ownerContext())
                && FILE_OBJECT_TYPE.equals(document.objectType())
                && fileObjectId(ownerModule, entityType, entityId).equals(document.objectId())
                && typeCode.equals(document.purposeCode());
        if (!ownerMatches) {
            throw new BusinessContractException("DELIVERY_FILE_OWNER_MISMATCH",
                    "文件引用归属与材料声明不一致，拒绝登记");
        }
    }

    @Transactional
    public DeliveryMaterialDO withdraw(Long id) {
        DeliveryMaterialDO row = requireMaterial(id);
        if (!DeliveryMaterialDO.STATUS_ACTIVE.equals(row.getStatus())) {
            throw new BusinessContractException("DELIVERY_MATERIAL_NOT_ACTIVE", "材料已撤回或不可撤回: " + id);
        }
        row.setStatus(DeliveryMaterialDO.STATUS_WITHDRAWN);
        materialMapper.updateById(row);
        eventPublisher.publishMaterial(row.getOwnerModule(), row.getEntityType(), row.getEntityId(),
                row.getTypeCode(), "MATERIAL_WITHDRAWN");
        return row;
    }

    public DeliveryMaterialDO requireMaterial(Long id) {
        return Optional.ofNullable(materialMapper.selectById(id))
                .orElseThrow(() -> new BusinessContractException("DELIVERY_MATERIAL_NOT_FOUND",
                        "材料不存在: " + id));
    }

    public List<DeliveryMaterialDO> listByEntity(String ownerModule, String entityType, Long entityId,
                                                 String typeCode) {
        return materialMapper.selectByEntity(ownerModule, entityType, entityId, typeCode);
    }

    /** 提交/确认前的文件证据重验：引用仍存在、可用且摘要未变。 */
    public FileEvidenceApi.Document revalidateActive(DeliveryMaterialDO material) {
        FileEvidenceApi.Document document = fileEvidenceApi.inspectDocument(
                TenantContextHolder.getRequiredTenantId(), material.getFileReferenceId());
        if (document == null || !document.available() || !document.sha256().equals(material.getFileSha256())) {
            throw new BusinessContractException("DELIVERY_FILE_UNAVAILABLE",
                    "材料文件证据已失效，不能继续使用: 材料 " + material.getId());
        }
        return document;
    }
}
