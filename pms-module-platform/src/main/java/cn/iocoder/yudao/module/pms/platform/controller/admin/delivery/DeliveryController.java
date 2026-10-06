package cn.iocoder.yudao.module.pms.platform.controller.admin.delivery;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.platform.api.delivery.DeliveryMaterialSource;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliveryCapabilityConfigDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliveryMaterialDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliveryRequirementDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliverySubmissionDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliveryTypeDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.DeliveryCapabilityConfigMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.DeliverySubmissionMapper;
import cn.iocoder.yudao.module.pms.platform.service.delivery.DeliveryCatalogService;
import cn.iocoder.yudao.module.pms.platform.service.delivery.DeliveryMaterialService;
import cn.iocoder.yudao.module.pms.platform.service.delivery.DeliveryRequirementService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 统一交付件入口：类型目录、实体适用配置、要求实例、材料记录、提交台账。
 * 材料上传复用统一文件两段式（/files:init-upload + :complete-upload，ownerContext=PLT），
 * 本入口只做材料登记与交付动作；权限不依赖任何项目执行对象。
 */
@RestController
@RequestMapping("/api/v1/pms/delivery")
@Tag(name = "管理后台 - PMS 统一交付件")
@Validated
@RequiredArgsConstructor
public class DeliveryController {
    @jakarta.annotation.Resource private cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService securityFrameworkService;
    @GetMapping("/allowed-actions")
    @PreAuthorize("@ss.hasPermission('pms:delivery:query')")
    public CommonResult<java.util.List<String>> allowedActions(@RequestParam("ownerModule") String module,
            @RequestParam("entityType") String type,@RequestParam("entityId") Long id) {
        ownerAccess.require(module,type,id,null,false,false);
        if (!ownerAccess.allowsGenericDeliveryActions(module,type)
                || !securityFrameworkService.hasPermission("pms:delivery:operate")) return success(java.util.List.of());
        boolean templateOwner = cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi.TEMPLATE_OWNER_MODULE.equals(module)
                && cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi.TEMPLATE_ENTITY_TYPE.equals(type);
        return success(templateOwner ? java.util.List.of("REGISTER_MATERIAL","WITHDRAW_MATERIAL")
                : java.util.List.of("REGISTER_MATERIAL","WITHDRAW_MATERIAL","SUBMIT","CONFIRM","WITHDRAW_SUBMISSION"));
    }


    private final DeliveryCatalogService catalogService;
    private final DeliveryMaterialService materialService;
    private final DeliveryRequirementService requirementService;
    private final cn.iocoder.yudao.module.pms.platform.service.delivery.DeliveryOwnerAccess ownerAccess;
    private final DeliverySubmissionMapper submissionMapper;
    private final cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi fileEvidence;

    @Data
    public static class DeliveryTypeCreateReqVO {
        @NotBlank
        private String typeCode;
        @NotBlank
        private String name;
        @NotBlank
        private String category;
        @NotEmpty
        private List<String> allowedMediaTypes;
        @NotNull
        private Long maxSizeBytes;
        private String remark;
    }

    @Data
    public static class DeliveryTypeUpdateReqVO {
        private String name;
        private String category;
        private List<String> allowedMediaTypes;
        private Long maxSizeBytes;
        private Boolean enabled;
        private String remark;
    }

    @Data
    public static class ConfigUpsertReqVO {
        @NotBlank
        private String ownerModule;
        @NotBlank
        private String entityType;
        @NotBlank
        private String typeCode;
        private Boolean required;
        private Integer minimumQuantity;
        @NotBlank
        private String countingUnit;
        private Boolean enabled;
    }

    @Data
    public static class OwnerReqVO {
        @NotBlank
        private String ownerModule;
        @NotBlank
        private String entityType;
        @NotNull
        private Long entityId;
    }

    @Data
    public static class MaterialRegisterReqVO extends OwnerReqVO {
        @NotBlank
        private String typeCode;
        @NotNull
        private Long fileReferenceId;
        private String title;
        private String sourceKind;
        /** 项目上下文（可空）：项目域材料用于项目级汇总定位。 */
        private Long projectId;
    }

    @Data
    public static class SubmissionReqVO {
        @NotNull
        private Long requirementId;
        @NotEmpty
        private List<Long> materialIds;
        @NotBlank
        private String requestKey;
    }

    @Data
    public static class RequirementVO {
        private Long id;
        private String ownerModule;
        private String entityType;
        private Long entityId;
        private String typeCode;
        private Boolean required;
        private Integer minimumQuantity;
        private String countingUnit;
        private String status;
        private Integer count;
        private Long configId;
    }

    @Data
    public static class MaterialVO {
        private Long id;
        private MaterialFileKey fileBusinessKey;
        private String materialKind;
        private String businessObjectType;
        private String businessObjectId;
        private Long businessRevisionNo;
        private String typeCode;
        private String fileName;
        private String title;
        private Long fileArtifactId;
        private Integer fileVersionNo;
        private String fileSha256;
        private Long fileReferenceId;
        private String sourceKind;
        private String status;
        private String createTime;
    }

    public record MaterialFileKey(String ownerContext, String objectType, String objectId,
                                  String purposeCode, String referenceKey) { }

    @Data
    public static class SubmissionOutcomeVO {
        private Long submissionId;
        private boolean replay;
        private Long requirementId;
        private String requirementStatus;
        private int count;
        private String requestKey;
    }

    @Data
    public static class SubmissionVO {
        private Long id;
        private Long requirementId;
        private String requestKey;
        private String status;
        private List<Long> materialIds;
        private String createTime;
    }

    public static SubmissionVO toView(DeliverySubmissionDO row) {
        SubmissionVO vo = new SubmissionVO();
        vo.setId(row.getId());
        vo.setRequirementId(row.getRequirementId());
        vo.setRequestKey(row.getRequestKey());
        vo.setStatus(row.getStatus());
        vo.setMaterialIds(cn.iocoder.yudao.module.pms.platform.service.delivery.JsonSupport
                .parseLongList(row.getMaterialIdsJson()));
        vo.setCreateTime(String.valueOf(row.getCreateTime()));
        return vo;
    }

    public static RequirementVO toView(DeliveryRequirementService.RequirementView view) {
        RequirementVO vo = new RequirementVO();
        DeliveryRequirementDO row = view.requirement();
        vo.setId(row.getId());
        vo.setOwnerModule(row.getOwnerModule());
        vo.setEntityType(row.getEntityType());
        vo.setEntityId(row.getEntityId());
        vo.setTypeCode(row.getTypeCode());
        vo.setRequired(row.getRequired());
        vo.setMinimumQuantity(view.minimumQuantity());
        vo.setCountingUnit(view.countingUnit());
        vo.setStatus(row.getStatus());
        vo.setCount(view.count());
        vo.setConfigId(row.getConfigId());
        return vo;
    }

    public static MaterialVO toView(DeliveryMaterialDO row) {
        MaterialVO vo = new MaterialVO();
        vo.setId(row.getId());
        vo.setMaterialKind(row.getMaterialKind());
        vo.setBusinessObjectType(row.getBusinessObjectType());
        vo.setBusinessObjectId(row.getBusinessObjectId());
        vo.setBusinessRevisionNo(row.getBusinessRevisionNo());
        vo.setTypeCode(row.getTypeCode());
        vo.setFileName(row.getFileName());
        vo.setTitle(row.getTitle());
        vo.setFileArtifactId(row.getFileArtifactId());
        vo.setFileVersionNo(row.getFileVersionNo());
        vo.setFileSha256(row.getFileSha256());
        vo.setFileReferenceId(row.getFileReferenceId());
        vo.setSourceKind(row.getSourceKind());
        vo.setStatus(row.getStatus());
        vo.setCreateTime(String.valueOf(row.getCreateTime()));
        return vo;
    }

    private MaterialVO materialView(DeliveryMaterialDO row) {
        MaterialVO view = toView(row);
        if (row.getFileReferenceId() != null) {
            var document = fileEvidence.inspectDocument(
                    cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId(),
                    row.getFileReferenceId());
            if (document != null) {
                // Original file identity is retained; the file APIs still enforce separate access permission.
                view.setFileBusinessKey(new MaterialFileKey(document.ownerContext(), document.objectType(),
                        document.objectId(), document.purposeCode(), document.referenceKey()));
            }
        }
        return view;
    }

    // ---------- 类型目录 ----------

    @GetMapping("/material-sources")
    @PreAuthorize("@ss.hasPermission('pms:delivery:query')")
    public CommonResult<List<DeliveryMaterialSource.Descriptor>> materialSources() {
        return success(java.util.Arrays.stream(DeliveryMaterialSource.values())
                .map(source -> new DeliveryMaterialSource.Descriptor(source.code(), source.label()))
                .toList());
    }

    @GetMapping("/types")
    @PreAuthorize("@ss.hasPermission('pms:delivery:query')")
    public CommonResult<List<DeliveryTypeDO>> listTypes(@RequestParam(required = false) Boolean enabled) {
        return success(catalogService.listTypes(enabled));
    }

    @PostMapping("/types")
    @PreAuthorize("@ss.hasPermission('pms:delivery:manage')")
    public CommonResult<DeliveryTypeDO> createType(@Valid @RequestBody DeliveryTypeCreateReqVO reqVO) {
        return success(catalogService.createType(reqVO.getTypeCode(), reqVO.getName(), reqVO.getCategory(),
                reqVO.getAllowedMediaTypes(), reqVO.getMaxSizeBytes(), reqVO.getRemark()));
    }

    @PutMapping("/types/{id}")
    @PreAuthorize("@ss.hasPermission('pms:delivery:manage')")
    public CommonResult<DeliveryTypeDO> updateType(@PathVariable Long id,
                                                   @Valid @RequestBody DeliveryTypeUpdateReqVO reqVO) {
        return success(catalogService.updateType(id, reqVO.getName(), reqVO.getCategory(),
                reqVO.getAllowedMediaTypes(), reqVO.getMaxSizeBytes(), reqVO.getEnabled(), reqVO.getRemark()));
    }

    // ---------- 适用配置 ----------

    @GetMapping("/configs")
    @PreAuthorize("@ss.hasPermission('pms:delivery:query')")
    public CommonResult<List<DeliveryCapabilityConfigDO>> listConfigs(
            @RequestParam String ownerModule, @RequestParam String entityType) {
        return success(catalogService.listConfigs(ownerModule, entityType));
    }

    @PostMapping("/configs")
    @PreAuthorize("@ss.hasPermission('pms:delivery:manage')")
    public CommonResult<DeliveryCapabilityConfigDO> upsertConfig(@Valid @RequestBody ConfigUpsertReqVO reqVO) {
        return success(catalogService.upsertConfig(reqVO.getOwnerModule(), reqVO.getEntityType(),
                reqVO.getTypeCode(), reqVO.getRequired(), reqVO.getMinimumQuantity(),
                reqVO.getCountingUnit(), reqVO.getEnabled()));
    }

    // ---------- 要求实例 ----------

    @PostMapping("/requirements/sync")
    @PreAuthorize("@ss.hasPermission('pms:delivery:query')")
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public CommonResult<List<RequirementVO>> syncRequirements(@Valid @RequestBody OwnerReqVO reqVO) {
        ownerAccess.require(reqVO.getOwnerModule(),reqVO.getEntityType(),reqVO.getEntityId(),null,true,true);
        return success(requirementService.syncFromConfig(reqVO.getOwnerModule(), reqVO.getEntityType(),
                reqVO.getEntityId()).stream().map(row -> toView(new DeliveryRequirementService.RequirementView(
                row, requirementService.countOf(row), row.getMinimumQuantity(), row.getCountingUnit()))).toList());
    }

    @GetMapping("/requirements")
    @PreAuthorize("@ss.hasPermission('pms:delivery:query')")
    public CommonResult<List<RequirementVO>> listRequirements(
            @RequestParam String ownerModule, @RequestParam String entityType, @RequestParam Long entityId) {
        ownerAccess.require(ownerModule,entityType,entityId,null,false,false);
        return success(requirementService.viewByEntity(ownerModule, entityType, entityId).stream()
                .map(DeliveryController::toView).toList());
    }

    @GetMapping("/requirements/{id}/completion")
    @PreAuthorize("@ss.hasPermission('pms:delivery:query')")
    public CommonResult<DeliveryRequirementService.CompletionFact> completion(@PathVariable Long id) {
        authorizeRequirement(id, false);
        return success(requirementService.evaluateCompletion(id));
    }

    @PostMapping("/requirements/{id}/confirm")
    @PreAuthorize("@ss.hasPermission('pms:delivery:operate')")
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public CommonResult<RequirementVO> confirm(@PathVariable Long id) {
        authorizeRequirement(id,true);
        return success(toView(requirementService.confirm(id)));
    }

    // ---------- 材料记录 ----------

    @GetMapping("/materials")
    @PreAuthorize("@ss.hasPermission('pms:delivery:query')")
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public CommonResult<List<MaterialVO>> listMaterials(
            @RequestParam String ownerModule, @RequestParam String entityType, @RequestParam Long entityId,
            @RequestParam(required = false) String typeCode) {
        ownerAccess.require(ownerModule,entityType,entityId,typeCode,false,false);
        return success(materialService.listByEntity(ownerModule, entityType, entityId, typeCode).stream()
                .map(this::materialView).toList());
    }

    @PostMapping("/materials")
    @PreAuthorize("@ss.hasPermission('pms:delivery:operate')")
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public CommonResult<MaterialVO> registerMaterial(@Valid @RequestBody MaterialRegisterReqVO reqVO) {
        ownerAccess.require(reqVO.getOwnerModule(),reqVO.getEntityType(),reqVO.getEntityId(),reqVO.getTypeCode(),true,true);
        Long actualProject = ownerAccess.projectId(reqVO.getOwnerModule(),reqVO.getEntityType(),reqVO.getEntityId());
        if (reqVO.getProjectId() != null && !java.util.Objects.equals(actualProject,reqVO.getProjectId()))
            throw new cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException("DELIVERY_PROJECT_MISMATCH","材料项目必须来自真实Owner对象");
        reqVO.setProjectId(actualProject);
        return success(materialView(materialService.registerFile(reqVO.getOwnerModule(), reqVO.getEntityType(),
                reqVO.getEntityId(), reqVO.getTypeCode(), reqVO.getFileReferenceId(), reqVO.getTitle(),
                reqVO.getSourceKind(), reqVO.getProjectId(), null)));
    }

    @PostMapping("/materials/{id}/withdraw")
    @PreAuthorize("@ss.hasPermission('pms:delivery:operate')")
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public CommonResult<MaterialVO> withdrawMaterial(@PathVariable Long id) {
        return success(materialView(materialService.withdraw(id)));
    }

    // ---------- 提交台账 ----------

    @PostMapping("/submissions")
    @PreAuthorize("@ss.hasPermission('pms:delivery:operate')")
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public CommonResult<SubmissionOutcomeVO> submit(@Valid @RequestBody SubmissionReqVO reqVO) {
        authorizeRequirement(reqVO.getRequirementId(),true);
        var outcome = requirementService.submit(reqVO.getRequirementId(), reqVO.getRequestKey(),
                reqVO.getMaterialIds());
        SubmissionOutcomeVO vo = new SubmissionOutcomeVO();
        vo.setSubmissionId(outcome.submission().getId());
        vo.setReplay(outcome.replay());
        vo.setRequirementId(outcome.requirement().getId());
        vo.setRequirementStatus(outcome.requirement().getStatus());
        vo.setCount(outcome.count());
        vo.setRequestKey(outcome.submission().getRequestKey());
        return success(vo);
    }

    @PostMapping("/submissions/{id}/withdraw")
    @PreAuthorize("@ss.hasPermission('pms:delivery:operate')")
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public CommonResult<SubmissionOutcomeVO> withdrawSubmission(@PathVariable Long id) {
        var submission = submissionMapper.selectById(id);
        if (submission == null) throw new cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException("DELIVERY_SUBMISSION_NOT_FOUND","提交记录不存在");
        authorizeRequirement(submission.getRequirementId(),true);
        var outcome = requirementService.withdrawSubmission(id);
        SubmissionOutcomeVO vo = new SubmissionOutcomeVO();
        vo.setSubmissionId(outcome.submission().getId());
        vo.setReplay(outcome.replay());
        vo.setRequirementId(outcome.requirement().getId());
        vo.setRequirementStatus(outcome.requirement().getStatus());
        vo.setCount(outcome.count());
        vo.setRequestKey(outcome.submission().getRequestKey());
        return success(vo);
    }

    @GetMapping("/submissions")
    @PreAuthorize("@ss.hasPermission('pms:delivery:query')")
    public CommonResult<List<SubmissionVO>> listSubmissions(@RequestParam Long requirementId) {
        authorizeRequirement(requirementId,false);
        return success(submissionMapper.selectByRequirement(requirementId).stream()
                .map(DeliveryController::toView).toList());
    }
    private void authorizeRequirement(Long id, boolean write) {
        var row = requirementService.requireRequirement(id);
        ownerAccess.require(row.getOwnerModule(),row.getEntityType(),row.getEntityId(),row.getTypeCode(),write,write);
        if (write && cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliveryRequirementDO.KIND_TEMPLATE_FROZEN.equals(row.getRequirementKind()))
            throw new cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException(
                    "DELIVERY_OWNER_COMMAND_REQUIRED", "模板冻结要求请使用来源Owner提交接口，以保留来源约束和判定证据");
    }

}
