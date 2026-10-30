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
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliverySourceFileQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliverySourceBusinessQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliverySourceIdentityQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
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

    @org.springframework.beans.factory.annotation.Autowired
    private DeliveryOwnerAccess ownerAccess;

    @org.springframework.beans.factory.annotation.Autowired
    private DeliveryFulfillmentService fulfillmentService;

    @org.springframework.beans.factory.annotation.Autowired
    private DeliveryDocumentOriginResolver originResolver;

    private cn.iocoder.yudao.module.pms.platform.api.file.FileDocumentSourceProvider.Scope origin(FileEvidenceApi.Document document, Long projectId) {
        var scope = originResolver == null ? null : originResolver.resolve(document);
        if (scope != null && !java.util.Objects.equals(projectId, scope.projectId()))
            throw new BusinessContractException("DELIVERY_SOURCE_PROJECT_MISMATCH", "File owner belongs to another project");
        return scope;
    }
    private DeliveryMaterialDO enrichOrigin(DeliveryMaterialDO row, cn.iocoder.yudao.module.pms.platform.api.file.FileDocumentSourceProvider.Scope scope) {
        if (scope == null || !scope.hasBusinessOwner()) return row;
        if (row.getSourceOwnerModule() != null && (!java.util.Objects.equals(row.getSourceOwnerModule(),scope.ownerModule())
                || !(java.util.Objects.equals(row.getSourceEntityType(),scope.entityType())
                    || "SOL".equals(scope.ownerModule()) && java.util.Set.of("REQUIREMENT_ANALYSIS","requirementAnalysis").contains(scope.entityType())
                    && row.getSourceEntityType()!=null && java.util.Set.of("REQUIREMENT_ANALYSIS","requirementAnalysis").contains(row.getSourceEntityType()))
                || !java.util.Objects.equals(row.getSourceEntityId(),scope.entityId())))
            throw new BusinessContractException("DELIVERY_SOURCE_OWNER_CONFLICT", "Source owner identity conflicts");
        if (row.getId() != null) materialMapper.assignOriginIfMissing(new cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliveryMaterialOriginUpdate(
                TenantContextHolder.getRequiredTenantId(),row.getId(),scope.sourceCode(),scope.ownerModule(),scope.entityType(),scope.entityId(),scope.revisionId()));
        if(row.getBusinessTypeCode()==null)row.setBusinessTypeCode(scope.sourceCode());
        if(row.getSourceOwnerModule()==null)row.setSourceOwnerModule(scope.ownerModule());
        if(row.getSourceEntityType()==null)row.setSourceEntityType(scope.entityType());
        row.setSourceEntityId(scope.entityId());
        if(row.getSourceRevisionId()==null)row.setSourceRevisionId(scope.revisionId());
        return row;
    }

    /** A specific authenticated generation operation has already validated its native file Owner. */
    @Transactional
    public DeliveryMaterialDO registerNativeGeneratedDocument(Long referenceId) {
        var document=fileEvidenceApi.inspectDocument(TenantContextHolder.getRequiredTenantId(),referenceId);
        if(document==null || !document.available())throw new BusinessContractException("DELIVERY_FILE_UNAVAILABLE","Generated file unavailable");
        return registerNativeDocument(document,true,DeliveryMaterialDO.SOURCE_GENERATED);
    }

    @Transactional
    public DeliveryMaterialDO registerNativeUploadedDocument(FileArtifactVersionFact fact) {
        return registerNativeDocument(requireNativeFile(fact),true,DeliveryMaterialDO.SOURCE_UPLOAD);
    }

    @Transactional
    public DeliveryMaterialDO registerNativeSourceDocument(FileArtifactVersionFact fact) {
        return registerNativeDocument(requireNativeFile(fact),false,DeliveryMaterialDO.SOURCE_ASSOCIATED);
    }

    private FileEvidenceApi.Document requireNativeFile(FileArtifactVersionFact fact) {
        if(fact==null)throw new BusinessContractException("DELIVERY_FILE_UNAVAILABLE","Native file fact required");
        var document=fileEvidenceApi.inspectDocumentByArtifact(TenantContextHolder.getRequiredTenantId(),fact.artifactId(),fact.versionNo());
        if(document==null || !document.available() || !java.util.Objects.equals(fact.sha256(),document.sha256())
                || !java.util.Objects.equals(fact.referenceKey(),document.referenceKey()))
            throw new BusinessContractException("DELIVERY_FILE_UNAVAILABLE","Native file fact does not match its registered anchor");
        return document;
    }

    private DeliveryMaterialDO registerNativeDocument(FileEvidenceApi.Document document,boolean requireCatalog,String sourceKind) {
        var scope=originResolver.resolve(document);
        if(scope==null || !scope.hasBusinessOwner())throw new BusinessContractException("DELIVERY_SOURCE_UNKNOWN","Generated file has no declared native source");
        if(requireCatalog)catalogService.requireEnabledType(scope.sourceCode());
        var existing=sourceFile(scope.projectId(),document.artifactId(),document.versionNo());
        if(existing!=null)return enrichOrigin(existing,scope);
        var row=new DeliveryMaterialDO();row.setTenantId(TenantContextHolder.getRequiredTenantId());
        row.setSourceIdentityKey(sourceKey(scope.projectId(),"FILE",document.artifactId(),document.versionNo()));
        row.setOwnerModule(scope.ownerModule());row.setEntityType(scope.entityType());row.setEntityId(scope.entityId());row.setTypeCode(scope.sourceCode());
        row.setProjectId(scope.projectId());row.setMaterialKind(DeliveryMaterialDO.KIND_FILE);row.setFileReferenceId(document.referenceId());
        row.setFileArtifactId(document.artifactId());row.setFileVersionNo(document.versionNo());row.setFileSha256(document.sha256());
        row.setFileName(document.name());row.setTitle(document.name());row.setSourceKind(sourceKind);row.setStatus(DeliveryMaterialDO.STATUS_ACTIVE);
        enrichOrigin(row,scope);
        try { materialMapper.insert(row); }
        catch(DuplicateKeyException conflict) { var concurrent=concurrentSource(row.getSourceIdentityKey());if(concurrent==null)throw conflict;return enrichOrigin(concurrent,scope); }
        eventPublisher.publishMaterial(scope.ownerModule(),scope.entityType(),scope.entityId(),scope.sourceCode(),"MATERIAL_REGISTERED");
        return row;
    }

    private DeliveryMaterialDO sourceFile(Long projectId, Long artifactId, Integer versionNo) {
        return materialMapper.selectSourceFile(new DeliverySourceFileQuery(
                TenantContextHolder.getRequiredTenantId(), projectId, artifactId, versionNo));
    }

    private DeliveryMaterialDO concurrentSource(String key) {
        return materialMapper.selectSourceIdentityForUpdate(new DeliverySourceIdentityQuery(
                TenantContextHolder.getRequiredTenantId(), key));
    }

    private DeliveryMaterialDO associate(DeliveryRequirementDO requirement, DeliveryMaterialDO row) {
        fulfillmentService.associate(requirement, row);
        return row;
    }

    private static String sourceKey(Long projectId, String kind, Object... identity) {
        // Length-prefixed components avoid delimiter collisions in externally defined object identifiers.
        StringBuilder value = new StringBuilder(projectId + ":" + kind);
        for (Object component : identity) {
            String part = component == null ? "N" : "S" + component;
            value.append(':').append(part.length()).append(':').append(part);
        }
        return org.apache.commons.codec.digest.DigestUtils.sha256Hex(value.toString());
    }

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
        if (requirementId != null) {
            DeliveryMaterialDO existing = sourceFile(projectId, document.artifactId(), document.versionNo());
            if (existing != null) return existing;
        }
        DeliveryMaterialDO row = new DeliveryMaterialDO();
        row.setTenantId(TenantContextHolder.getRequiredTenantId());
        if (requirementId != null) row.setSourceIdentityKey(sourceKey(projectId, "FILE", document.artifactId(), document.versionNo()));
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
            if (requirementId != null) {
                DeliveryMaterialDO existing = concurrentSource(row.getSourceIdentityKey());
                if (existing != null) return existing;
            }
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
        var identity=provider.identity(TenantContextHolder.getRequiredTenantId(),projectId,businessObjectId,businessRevisionNo);
        String canonicalType=identity==null?businessObjectType:identity.businessObjectType();
        String canonicalId=identity==null?businessObjectId:identity.businessObjectId();
        Long canonicalRevision=identity==null?businessRevisionNo:identity.businessRevisionNo();
        String identityKey=sourceKey(projectId,"BUSINESS_RESULT",canonicalType,canonicalId,canonicalRevision);
        DeliveryMaterialDO existing=matchingBusinessSource(concurrentSource(identityKey),identity,identityKey);
        if(existing==null)existing=matchingBusinessSource(materialMapper.selectSourceBusiness(new DeliverySourceBusinessQuery(TenantContextHolder.getRequiredTenantId(),projectId,canonicalType,canonicalId,canonicalRevision)),identity,identityKey);
        if(existing==null)for(var alias:provider.aliases(TenantContextHolder.getRequiredTenantId(),projectId,businessObjectId,businessRevisionNo)) {
            existing=matchingBusinessSource(materialMapper.selectSourceBusiness(new DeliverySourceBusinessQuery(TenantContextHolder.getRequiredTenantId(),projectId,alias.businessObjectType(),alias.businessObjectId(),alias.businessRevisionNo())),identity,identityKey);
            if(existing!=null)break;
        }
        if(existing!=null)return enrichBusinessIdentity(existing,identity,identityKey);
        DeliveryMaterialDO row = new DeliveryMaterialDO();
        row.setTenantId(TenantContextHolder.getRequiredTenantId());
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
        enrichBusinessIdentity(row,identity,identityKey);
        try { materialMapper.insert(row); }
        catch(DuplicateKeyException conflict) {var concurrent=concurrentSource(identityKey);if(concurrent==null)throw conflict;return enrichBusinessIdentity(concurrent,identity,identityKey);}
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
        return associate(requirement, doRegisterFile(requirement.getOwnerModule(), requirement.getEntityType(),
                requirement.getEntityId(), requirement.getTypeCode(), fileReferenceId, title, sourceKind,
                requirement.getProjectId(), requirement.getId()));
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
        if (!fact.artifactId().equals(document.artifactId()) || !fact.versionNo().equals(document.versionNo())
                || !fact.sha256().equals(document.sha256())) {
            throw new BusinessContractException("DELIVERY_FILE_UNAVAILABLE", "投影版本证据与文件事实不一致");
        }
        var scope=origin(document,requirement.getProjectId());
        DeliveryMaterialDO reusable = sourceFile(requirement.getProjectId(), fact.artifactId(), fact.versionNo());
        if (reusable != null) return associate(requirement, mergeArchiveObligation(enrichOrigin(reusable,scope), archiveStatus));
        DeliveryMaterialDO row = new DeliveryMaterialDO();
        row.setTenantId(TenantContextHolder.getRequiredTenantId());
        row.setSourceIdentityKey(sourceKey(requirement.getProjectId(), "FILE", fact.artifactId(), fact.versionNo()));
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
        enrichOrigin(row,scope);
        try {
            materialMapper.insert(row);
        } catch (DuplicateKeyException conflict) {
            DeliveryMaterialDO existing = concurrentSource(row.getSourceIdentityKey());
            if (existing == null) throw conflict;
            return associate(requirement, mergeArchiveObligation(enrichOrigin(existing,scope), archiveStatus));
        }
        eventPublisher.publishMaterial(requirement.getOwnerModule(), requirement.getEntityType(),
                requirement.getEntityId(), requirement.getTypeCode(), "MATERIAL_REGISTERED");
        return associate(requirement, row);
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
        var scope=origin(document,requirement.getProjectId());
        DeliveryMaterialDO existing = sourceFile(requirement.getProjectId(), document.artifactId(), document.versionNo());
        if (existing != null) return associate(requirement,enrichOrigin(existing,scope));
        DeliveryMaterialDO row = new DeliveryMaterialDO();
        row.setTenantId(TenantContextHolder.getRequiredTenantId());
        row.setSourceIdentityKey(sourceKey(requirement.getProjectId(), "FILE", document.artifactId(), document.versionNo()));
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
        enrichOrigin(row,scope);
        try { materialMapper.insert(row); }
        catch (DuplicateKeyException conflict) {
            DeliveryMaterialDO concurrent = concurrentSource(row.getSourceIdentityKey());
            if (concurrent == null) throw conflict;
            return associate(requirement,enrichOrigin(concurrent,scope));
        }
        eventPublisher.publishMaterial(requirement.getOwnerModule(), requirement.getEntityType(),
                requirement.getEntityId(), requirement.getTypeCode(), "MATERIAL_REGISTERED");
        return associate(requirement, row);
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
        var identity=provider.identity(TenantContextHolder.getRequiredTenantId(),requirement.getProjectId(),businessObjectId,businessRevisionNo);
        String canonicalType=identity==null?businessObjectType:identity.businessObjectType();
        String canonicalId=identity==null?businessObjectId:identity.businessObjectId();
        Long canonicalRevision=identity==null?businessRevisionNo:identity.businessRevisionNo();
        String identityKey=sourceKey(requirement.getProjectId(),"BUSINESS_RESULT",canonicalType,canonicalId,canonicalRevision);
        DeliveryMaterialDO existing=matchingBusinessSource(concurrentSource(identityKey),identity,identityKey);
        if(existing==null)existing=matchingBusinessSource(materialMapper.selectSourceBusiness(new DeliverySourceBusinessQuery(TenantContextHolder.getRequiredTenantId(),requirement.getProjectId(),canonicalType,canonicalId,canonicalRevision)),identity,identityKey);
        if(existing==null && identity!=null)existing=matchingBusinessSource(materialMapper.selectSourceBusiness(new DeliverySourceBusinessQuery(TenantContextHolder.getRequiredTenantId(),requirement.getProjectId(),businessObjectType,businessObjectId,businessRevisionNo)),identity,identityKey);
        if(existing!=null)return associate(requirement,enrichBusinessIdentity(existing,identity,identityKey));
        DeliveryMaterialDO row = new DeliveryMaterialDO();
        row.setTenantId(TenantContextHolder.getRequiredTenantId());
        row.setSourceIdentityKey(identityKey);
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
        enrichBusinessIdentity(row,identity,identityKey);
        try { materialMapper.insert(row); }
        catch (DuplicateKeyException conflict) {
            DeliveryMaterialDO concurrent = concurrentSource(row.getSourceIdentityKey());
            if (concurrent == null) throw conflict;
            return associate(requirement, concurrent);
        }
        eventPublisher.publishMaterial(requirement.getOwnerModule(), requirement.getEntityType(),
                requirement.getEntityId(), requirement.getTypeCode(), "MATERIAL_REGISTERED");
        return associate(requirement, row);
    }

    private DeliveryMaterialDO matchingBusinessSource(DeliveryMaterialDO row,DeliveryBusinessObjectEvidenceProvider.Identity identity,String key) {
        if(row==null || identity==null)return row;
        if(row.getSourceOwnerModule()!=null && !Objects.equals(row.getSourceOwnerModule(),identity.ownerModule())
                || row.getSourceEntityType()!=null && !Objects.equals(row.getSourceEntityType(),identity.entityType())
                || row.getSourceEntityId()!=null && !Objects.equals(row.getSourceEntityId(),identity.entityId())
                || row.getSourceRevisionId()!=null && !Objects.equals(row.getSourceRevisionId(),identity.businessRevisionNo()))return null;
        String storedKey=row.getSourceIdentityKey();
        // Both native identities and historical wrapper identities are hashes. A hash's shape
        // cannot identify its origin; validate a historical raw key against its frozen coordinates.
        if(storedKey!=null && storedKey.matches("[0-9a-f]{64}") && !storedKey.equals(key)
                && !storedKey.equals(sourceKey(row.getProjectId(),"BUSINESS_RESULT",
                        row.getBusinessObjectType(),row.getBusinessObjectId(),row.getBusinessRevisionNo())))return null;
        if(row.getBusinessRevisionNo()!=null && !Objects.equals(row.getBusinessRevisionNo(),identity.businessRevisionNo()))return null;
        if(identity.businessRevisionNo()!=null && !key.equals(storedKey)
                && !Objects.equals(row.getBusinessRevisionNo(),identity.businessRevisionNo())
                && !Objects.equals(row.getSourceRevisionId(),identity.businessRevisionNo()))return null;
        return row;
    }

    private DeliveryMaterialDO enrichBusinessIdentity(DeliveryMaterialDO row,DeliveryBusinessObjectEvidenceProvider.Identity identity,String key) {
        if(row.getSourceIdentityKey()==null && row.getId()!=null)
            materialMapper.assignSourceIdentityIfMissing(new cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliverySourceIdentityAssignment(TenantContextHolder.getRequiredTenantId(),row.getId(),key));
        if(row.getSourceIdentityKey()==null)row.setSourceIdentityKey(key);
        if(identity!=null)enrichOrigin(row,new cn.iocoder.yudao.module.pms.platform.api.file.FileDocumentSourceProvider.Scope(row.getProjectId(),identity.businessTypeCode(),identity.ownerModule(),identity.entityType(),identity.entityId(),identity.businessRevisionNo()));
        return row;
    }

    private DeliveryMaterialDO mergeArchiveObligation(DeliveryMaterialDO material, String requestedStatus) {
        if (DeliveryMaterialDO.ARCHIVE_PENDING_COMPENSATION.equals(requestedStatus)
                && DeliveryMaterialDO.STATUS_ACTIVE.equals(material.getStatus())) {
            int promoted = materialMapper.requireArchiveIfNotRequired(
                    new cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliveryMaterialArchiveObligationQuery(
                            TenantContextHolder.getRequiredTenantId(), material.getId()));
            if (promoted == 1) material.setArchiveStatus(DeliveryMaterialDO.ARCHIVE_PENDING_COMPENSATION);
        }
        return material;
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
        if (ownerAccess == null) throw DeliveryOwnerAccess.denied();
        ownerAccess.require(row.getOwnerModule(), row.getEntityType(), row.getEntityId(), row.getTypeCode(), true, true);
        if (row.getRequirementId() != null) throw new cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException(
                "DELIVERY_OWNER_COMMAND_REQUIRED", "模板材料请使用来源Owner操作接口");
        return withdrawTrusted(id);
    }

    /** Package-only entry for Owner-authorized projections and background evidence convergence. */
    DeliveryMaterialDO withdrawTrusted(Long id) {
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
        return materialMapper.selectListForOwner(new cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliveryMaterialOwnerQuery(TenantContextHolder.getRequiredTenantId(),ownerModule,entityType,entityId,typeCode));
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
            var identity=provider.identity(TenantContextHolder.getRequiredTenantId(),material.getProjectId(),material.getBusinessObjectId(),material.getBusinessRevisionNo());
            if(identity!=null && matchingBusinessSource(material,identity,sourceKey(material.getProjectId(),"BUSINESS_RESULT",
                    identity.businessObjectType(),identity.businessObjectId(),identity.businessRevisionNo()))==null)
                throw new BusinessContractException("DELIVERY_BUSINESS_OBJECT_INVALID","Frozen approved source identity no longer matches the current business result");
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
