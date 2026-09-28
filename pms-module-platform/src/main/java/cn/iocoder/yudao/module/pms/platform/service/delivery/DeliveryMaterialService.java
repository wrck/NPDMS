package cn.iocoder.yudao.module.pms.platform.service.delivery;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.delivery.DeliveryBusinessObjectEvidenceProvider;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi;
import cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliveryMaterialDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliveryRequirementDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.DeliveryMaterialMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 实际材料记录：登记即冻结证据。FILE 行冻结文件证据（引用存在、可用、摘要一致、归属与类型匹配）；
 * BUSINESS_RESULT 行冻结业务成果身份与修订锚（经 Owner 模块证据提供方校验）。
 * 同一文件版本/业务成果修订对同一来源实体幂等登记；撤回后不再计数，可重新提交前重新验证。
 */
@Service
@RequiredArgsConstructor
public class DeliveryMaterialService {

    public static final String FILE_OWNER_CONTEXT = PlatformDeliveryRequirementApi.MATERIAL_FILE_OWNER_CONTEXT;
    public static final String FILE_OBJECT_TYPE = PlatformDeliveryRequirementApi.MATERIAL_FILE_OBJECT_TYPE;

    private final DeliveryMaterialMapper materialMapper;
    private final DeliveryCatalogService catalogService;
    private final FileEvidenceApi fileEvidenceApi;
    private final DeliveryEventPublisher eventPublisher;
    private final List<DeliveryBusinessObjectEvidenceProvider> evidenceProviders;

    public static String fileObjectId(String ownerModule, String entityType, Long entityId) {
        return ownerModule + ":" + entityType + ":" + entityId;
    }

    @Transactional
    public DeliveryMaterialDO register(String ownerModule, String entityType, Long entityId, String typeCode,
                                       Long fileReferenceId, String title, String sourceKind) {
        return registerFile(ownerModule, entityType, entityId, typeCode, fileReferenceId, title, sourceKind, null, null);
    }

    @Transactional
    public DeliveryMaterialDO registerFile(String ownerModule, String entityType, Long entityId, String typeCode,
                                           Long fileReferenceId, String title, String sourceKind,
                                           Long projectId, Long requirementId) {
        catalogService.requireEnabledType(typeCode);
        return doRegisterFile(ownerModule, entityType, entityId, typeCode, fileReferenceId, title,
                sourceKind, projectId, requirementId);
    }

    private DeliveryMaterialDO doRegisterFile(String ownerModule, String entityType, Long entityId, String typeCode,
                                              Long fileReferenceId, String title, String sourceKind,
                                              Long projectId, Long requirementId) {
        FileEvidenceApi.Document document = fileEvidenceApi.inspectDocument(
                TenantContextHolder.getRequiredTenantId(), fileReferenceId);
        if (document == null) {
            throw new BusinessContractException("DELIVERY_FILE_UNAVAILABLE",
                    "文件引用不存在或不可用: " + fileReferenceId);
        }
        validateDocumentOwner(document, ownerModule, entityType, entityId, typeCode, requirementId);
        if (!document.available()) {
            throw new BusinessContractException("DELIVERY_FILE_UNAVAILABLE",
                    "文件版本不可用，不能登记为交付材料: " + fileReferenceId);
        }
        DeliveryMaterialDO row = new DeliveryMaterialDO();
        row.setOwnerModule(ownerModule);
        row.setEntityType(entityType);
        row.setEntityId(entityId);
        row.setTypeCode(typeCode);
        row.setMaterialKind(DeliveryMaterialDO.KIND_FILE);
        row.setRequirementId(requirementId);
        row.setProjectId(projectId);
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
                    .filter(material -> DeliveryMaterialDO.KIND_FILE.equals(material.getMaterialKind()))
                    .filter(material -> document.artifactId().equals(material.getFileArtifactId())
                            && document.versionNo().equals(material.getFileVersionNo()))
                    .findFirst()
                    .orElseThrow(() -> conflict);
        }
        eventPublisher.publishMaterial(ownerModule, entityType, entityId, typeCode, "MATERIAL_REGISTERED");
        return row;
    }

    /**
     * 登记业务成果材料：类型须启用，证据经 Owner 模块提供方校验（对象存在、状态成立、修订锚一致）；
     * 同一（来源实体 + 业务对象 + 修订锚）幂等返回既有记录，不重复落库。
     */
    @Transactional
    public DeliveryMaterialDO registerBusinessResult(String ownerModule, String entityType, Long entityId,
                                                     String typeCode, String businessObjectType,
                                                     String businessObjectId, Long businessRevisionNo,
                                                     String title, Long projectId) {
        catalogService.requireEnabledType(typeCode);
        if (businessObjectType == null || businessObjectType.isBlank()
                || businessObjectId == null || businessObjectId.isBlank()) {
            throw new BusinessContractException("DELIVERY_BUSINESS_OBJECT_INVALID",
                    "业务成果材料必须携带业务对象类型与标识");
        }
        DeliveryBusinessObjectEvidenceProvider provider = requireProvider(businessObjectType);
        provider.validateCurrent(TenantContextHolder.getRequiredTenantId(), projectId,
                businessObjectId, businessRevisionNo);
        DeliveryMaterialDO existing = materialMapper.selectByBusinessObject(
                ownerModule, entityType, entityId, businessObjectType, businessObjectId, businessRevisionNo);
        if (existing != null) {
            return existing;
        }
        DeliveryMaterialDO row = new DeliveryMaterialDO();
        row.setOwnerModule(ownerModule);
        row.setEntityType(entityType);
        row.setEntityId(entityId);
        row.setTypeCode(typeCode);
        row.setMaterialKind(DeliveryMaterialDO.KIND_BUSINESS_RESULT);
        row.setProjectId(projectId);
        row.setBusinessObjectType(businessObjectType);
        row.setBusinessObjectId(businessObjectId);
        row.setBusinessRevisionNo(businessRevisionNo);
        row.setTitle(title);
        row.setSourceKind(DeliveryMaterialDO.SOURCE_ASSOCIATED);
        row.setStatus(DeliveryMaterialDO.STATUS_ACTIVE);
        materialMapper.insert(row);
        eventPublisher.publishMaterial(ownerModule, entityType, entityId, typeCode, "MATERIAL_REGISTERED");
        return row;
    }

    private DeliveryBusinessObjectEvidenceProvider requireProvider(String businessObjectType) {
        return evidenceProviders.stream()
                .filter(provider -> provider.supports(businessObjectType))
                .findFirst()
                .orElseThrow(() -> new BusinessContractException("DELIVERY_EVIDENCE_PROVIDER_MISSING",
                        "业务成果对象无证据提供方，拒绝登记: " + businessObjectType));
    }

    /**
     * 模板冻结链文件材料登记（P06R I2）：typeCode 是交付件编码而非目录类型，不做目录启停校验；
     * 上传锚为 ACC/project_deliverable/{projectId}（purposeCode=交付件编码），归属校验后冻结证据。
     */
    @Transactional
    public DeliveryMaterialDO registerTemplateFrozenFile(DeliveryRequirementDO requirement,
                                                         Long fileReferenceId, String title, String sourceKind) {
        return doRegisterFile(requirement.getOwnerModule(), requirement.getEntityType(),
                requirement.getEntityId(), requirement.getTypeCode(), fileReferenceId, title, sourceKind,
                requirement.getProjectId(), requirement.getId());
    }

    /**
     * 模板冻结链自动投影文件材料登记：投影事实（工件版本）是权威来源，不做 owner 锚校验——
     * 投影文件的历史锚（如 ACC/ACCEPTANCE_REPORT_VERSION）保持原样，不重锚不可变历史。
     * 引用身份按工件版本反查（引用≈工件版本 1:1，取最早引用槽）。
     */
    @Transactional
    public DeliveryMaterialDO registerProjectionFile(DeliveryRequirementDO requirement,
                                                     FileArtifactVersionFact fact, String title,
                                                     String archiveStatus) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        FileEvidenceApi.Document document = fileEvidenceApi.inspectDocumentByArtifact(
                tenantId, fact.artifactId(), fact.versionNo());
        if (document == null || !document.available()) {
            throw new BusinessContractException("DELIVERY_FILE_UNAVAILABLE",
                    "投影文件无法定位可用统一引用: artifact " + fact.artifactId() + "#" + fact.versionNo());
        }
        DeliveryMaterialDO row = new DeliveryMaterialDO();
        row.setOwnerModule(requirement.getOwnerModule());
        row.setEntityType(requirement.getEntityType());
        row.setEntityId(requirement.getEntityId());
        row.setTypeCode(requirement.getTypeCode());
        row.setMaterialKind(DeliveryMaterialDO.KIND_FILE);
        row.setRequirementId(requirement.getId());
        row.setProjectId(requirement.getProjectId());
        row.setFileReferenceId(document.referenceId());
        row.setFileArtifactId(fact.artifactId());
        row.setFileVersionNo(fact.versionNo());
        row.setFileSha256(fact.sha256());
        row.setFileName(fact.name());
        row.setTitle(title == null || title.isBlank() ? fact.name() : title);
        row.setSourceKind(DeliveryMaterialDO.SOURCE_GENERATED);
        row.setArchiveStatus(archiveStatus == null || archiveStatus.isBlank()
                ? DeliveryMaterialDO.ARCHIVE_NOT_REQUIRED : archiveStatus);
        row.setStatus(DeliveryMaterialDO.STATUS_ACTIVE);
        try {
            materialMapper.insert(row);
        } catch (DuplicateKeyException conflict) {
            DeliveryMaterialDO existing = materialMapper.selectByEntity(requirement.getOwnerModule(),
                            requirement.getEntityType(), requirement.getEntityId(), requirement.getTypeCode()).stream()
                    .filter(material -> DeliveryMaterialDO.KIND_FILE.equals(material.getMaterialKind()))
                    .filter(material -> fact.artifactId().equals(material.getFileArtifactId())
                            && fact.versionNo().equals(material.getFileVersionNo()))
                    .findFirst()
                    .orElseThrow(() -> conflict);
            // 投影事实是权威来源：此前被整体置换/撤销撤回的同一工件版本材料，重新登记时恢复有效。
            if (DeliveryMaterialDO.STATUS_WITHDRAWN.equals(existing.getStatus())) {
                existing.setStatus(DeliveryMaterialDO.STATUS_ACTIVE);
                existing.setArchiveStatus(archiveStatus == null || archiveStatus.isBlank()
                        ? DeliveryMaterialDO.ARCHIVE_NOT_REQUIRED : archiveStatus);
                materialMapper.updateById(existing);
                eventPublisher.publishMaterial(requirement.getOwnerModule(), requirement.getEntityType(),
                        requirement.getEntityId(), requirement.getTypeCode(), "MATERIAL_REACTIVATED");
            }
            return existing;
        }
        eventPublisher.publishMaterial(requirement.getOwnerModule(), requirement.getEntityType(),
                requirement.getEntityId(), requirement.getTypeCode(), "MATERIAL_REGISTERED");
        return row;
    }

    /**
     * 模板冻结链归集文档材料登记：文件锚点属其他业务对象（不重挂历史），按文件自身身份冻结证据；
     * businessObjectType 承载归集来源编码（documentSources）、businessObjectId 承载引用ID，
     * 幂等范围 =（要求 + 来源编码 + 引用）。不做 Owner 锚校验——归集由来源事件驱动。
     */
    @Transactional
    public DeliveryMaterialDO registerTemplateFrozenDocument(DeliveryRequirementDO requirement,
                                                             String sourceCode, Long fileReferenceId, String title) {
        if (sourceCode == null || sourceCode.isBlank() || fileReferenceId == null || fileReferenceId <= 0) {
            throw new BusinessContractException("DELIVERY_BUSINESS_OBJECT_INVALID",
                    "归集文档材料必须携带来源编码与引用");
        }
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        FileEvidenceApi.Document document = fileEvidenceApi.inspectDocument(tenantId, fileReferenceId);
        if (document == null || !document.available()) {
            throw new BusinessContractException("DELIVERY_FILE_UNAVAILABLE",
                    "归集文件引用不存在或不可用: " + fileReferenceId);
        }
        DeliveryMaterialDO existing = materialMapper.selectByRequirementAndBusinessObject(
                requirement.getId(), sourceCode, String.valueOf(fileReferenceId), null);
        if (existing != null) {
            return existing;
        }
        DeliveryMaterialDO row = new DeliveryMaterialDO();
        row.setOwnerModule(requirement.getOwnerModule());
        row.setEntityType(requirement.getEntityType());
        row.setEntityId(requirement.getEntityId());
        row.setTypeCode(requirement.getTypeCode());
        row.setMaterialKind(DeliveryMaterialDO.KIND_FILE);
        row.setRequirementId(requirement.getId());
        row.setProjectId(requirement.getProjectId());
        row.setFileReferenceId(fileReferenceId);
        row.setFileArtifactId(document.artifactId());
        row.setFileVersionNo(document.versionNo());
        row.setFileSha256(document.sha256());
        row.setFileName(document.name());
        row.setTitle(title == null || title.isBlank() ? document.name() : title);
        row.setBusinessObjectType(sourceCode);
        row.setBusinessObjectId(String.valueOf(fileReferenceId));
        row.setSourceKind(DeliveryMaterialDO.SOURCE_ASSOCIATED);
        row.setStatus(DeliveryMaterialDO.STATUS_ACTIVE);
        materialMapper.insert(row);
        eventPublisher.publishMaterial(requirement.getOwnerModule(), requirement.getEntityType(),
                requirement.getEntityId(), requirement.getTypeCode(), "MATERIAL_REGISTERED");
        return row;
    }

    /**
     * 模板冻结链业务成果材料登记：证据经 Owner 模块提供方按（项目 + 对象 + 修订）校验；
     * 幂等范围是（要求实例 + 业务对象 + 修订锚）——同一业务成果可同时是多个交付件的证据。
     * 旧修订材料的退场由本次提交的整体置换（submitTemplateFrozen）统一处理。
     */
    @Transactional
    public DeliveryMaterialDO registerTemplateFrozenBusinessResult(DeliveryRequirementDO requirement,
                                                                   String businessObjectType,
                                                                   String businessObjectId,
                                                                   Long businessRevisionNo, String title) {
        if (businessObjectType == null || businessObjectType.isBlank()
                || businessObjectId == null || businessObjectId.isBlank()) {
            throw new BusinessContractException("DELIVERY_BUSINESS_OBJECT_INVALID",
                    "业务成果材料必须携带业务对象类型与标识");
        }
        DeliveryBusinessObjectEvidenceProvider provider = requireProvider(businessObjectType);
        provider.validateCurrent(TenantContextHolder.getRequiredTenantId(), requirement.getProjectId(),
                businessObjectId, businessRevisionNo);
        DeliveryMaterialDO existing = materialMapper.selectByRequirementAndBusinessObject(
                requirement.getId(), businessObjectType, businessObjectId, businessRevisionNo);
        if (existing != null) {
            return existing;
        }
        DeliveryMaterialDO row = new DeliveryMaterialDO();
        row.setOwnerModule(requirement.getOwnerModule());
        row.setEntityType(requirement.getEntityType());
        row.setEntityId(requirement.getEntityId());
        row.setTypeCode(requirement.getTypeCode());
        row.setMaterialKind(DeliveryMaterialDO.KIND_BUSINESS_RESULT);
        row.setRequirementId(requirement.getId());
        row.setProjectId(requirement.getProjectId());
        row.setBusinessObjectType(businessObjectType);
        row.setBusinessObjectId(businessObjectId);
        row.setBusinessRevisionNo(businessRevisionNo);
        row.setTitle(title);
        row.setSourceKind(DeliveryMaterialDO.SOURCE_ASSOCIATED);
        row.setStatus(DeliveryMaterialDO.STATUS_ACTIVE);
        materialMapper.insert(row);
        eventPublisher.publishMaterial(requirement.getOwnerModule(), requirement.getEntityType(),
                requirement.getEntityId(), requirement.getTypeCode(), "MATERIAL_REGISTERED");
        return row;
    }

    private static void validateDocumentOwner(FileEvidenceApi.Document document, String ownerModule,
                                              String entityType, Long entityId, String typeCode, Long requirementId) {
        boolean ownerMatches = FILE_OWNER_CONTEXT.equals(document.ownerContext())
                && FILE_OBJECT_TYPE.equals(document.objectType())
                && fileObjectId(ownerModule, entityType, entityId).equals(document.objectId())
                && typeCode.equals(document.purposeCode());
        // 迁移保留的旧交付件附件锚：rootId == 要求ID，重选入新提交时接受（历史文件不重挂）。
        boolean legacyMatches = requirementId != null
                && PlatformDeliveryRequirementApi.LEGACY_OWNER_CONTEXT.equals(document.ownerContext())
                && PlatformDeliveryRequirementApi.LEGACY_OBJECT_TYPE.equals(document.objectType())
                && String.valueOf(requirementId).equals(document.objectId())
                && PlatformDeliveryRequirementApi.LEGACY_PURPOSE_CODE.equals(document.purposeCode());
        if (!ownerMatches && !legacyMatches) {
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

    /** 提交台账冻结证据：FILE 行冻结文件版本锚，BUSINESS_RESULT 行冻结业务成果锚（P06R）。 */
    public record FrozenEvidence(String materialKind, Long fileReferenceId, String fileName, String fileSha256,
                                 Long fileArtifactId, Integer fileVersionNo,
                                 String businessObjectType, String businessObjectId, Long businessRevisionNo) {
    }

    /** 提交/确认前的材料证据重验：按材料种类校验文件证据或业务成果证据仍有效，返回冻结证据快照。 */
    public FrozenEvidence revalidateActive(DeliveryMaterialDO material) {
        if (DeliveryMaterialDO.KIND_BUSINESS_RESULT.equals(material.getMaterialKind())) {
            DeliveryBusinessObjectEvidenceProvider provider = requireProvider(material.getBusinessObjectType());
            provider.validateCurrent(TenantContextHolder.getRequiredTenantId(), material.getProjectId(),
                    material.getBusinessObjectId(), material.getBusinessRevisionNo());
            return new FrozenEvidence(material.getMaterialKind(), null, null, null, null, null,
                    material.getBusinessObjectType(), material.getBusinessObjectId(),
                    material.getBusinessRevisionNo());
        }
        FileEvidenceApi.Document document = fileEvidenceApi.inspectDocument(
                TenantContextHolder.getRequiredTenantId(), material.getFileReferenceId());
        if (document == null || !document.available() || !document.sha256().equals(material.getFileSha256())) {
            throw new BusinessContractException("DELIVERY_FILE_UNAVAILABLE",
                    "材料文件证据已失效，不能继续使用: 材料 " + material.getId());
        }
        return frozenEvidence(material, document);
    }

    /**
     * 门禁/收敛路径的锁级重验（P06R I2）：先以引用身份定位文档，再用文档自身锚点字段构造锁查询——
     * 兼容历史 ACC 锚文件（ownerContext=ACC），不重锚不可变历史。失效即抛
     * DELIVERY_FILE_UNAVAILABLE（文件失效为终态，不存在瞬态失效）。
     */
    public FrozenEvidence lockAndRevalidateActive(DeliveryMaterialDO material) {
        if (DeliveryMaterialDO.KIND_BUSINESS_RESULT.equals(material.getMaterialKind())) {
            return revalidateActive(material);
        }
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        FileEvidenceApi.Document document = fileEvidenceApi.inspectDocument(tenantId, material.getFileReferenceId());
        if (document == null || !document.available() || !document.sha256().equals(material.getFileSha256())) {
            throw new BusinessContractException("DELIVERY_FILE_UNAVAILABLE",
                    "材料文件证据已失效，不能继续使用: 材料 " + material.getId());
        }
        FileEvidenceApi.Fact fact = fileEvidenceApi.lockAndRevalidate(new FileEvidenceApi.Query(
                tenantId, document.artifactId(), document.versionNo(), document.ownerContext(),
                document.objectType(), document.objectId(), document.purposeCode(), document.referenceKey(),
                document.sha256()));
        if (fact == null || !fact.valid()) {
            throw new BusinessContractException("DELIVERY_FILE_UNAVAILABLE",
                    "材料文件证据锁级重验未通过: 材料 " + material.getId()
                            + (fact == null ? "" : " (" + fact.reason() + ")"));
        }
        return frozenEvidence(material, document);
    }

    private static FrozenEvidence frozenEvidence(DeliveryMaterialDO material, FileEvidenceApi.Document document) {
        return new FrozenEvidence(material.getMaterialKind(), material.getFileReferenceId(),
                material.getFileName(), material.getFileSha256(), document.artifactId(),
                document.versionNo(), null, null, null);
    }
}
